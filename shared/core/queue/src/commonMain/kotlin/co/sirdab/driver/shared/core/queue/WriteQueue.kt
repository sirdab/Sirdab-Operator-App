package co.sirdab.driver.shared.core.queue

import co.sirdab.driver.shared.core.network.ApiFailure
import co.sirdab.driver.shared.core.network.FailureDisposition
import co.sirdab.driver.shared.core.network.FileUploader
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.TmsJson
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.disposition
import co.sirdab.driver.shared.core.platform.files.LocalFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Why a drain stopped, so the caller can tell the driver something true. */
sealed interface DrainResult {
    /** The queue is empty. */
    data object Drained : DrainResult

    /** Nothing was due yet: everything left is waiting out its backoff. */
    data class Waiting(val remaining: Int, val nextAttemptAtMillis: Long) : DrainResult

    /** A transient failure. The rows are still queued and will be retried. */
    data class Deferred(val remaining: Int, val reason: String) : DrainResult

    /**
     * The server refused in a way retrying cannot fix: not signed in, not set up,
     * or an endpoint that does not exist yet. Draining stops until something
     * changes; looping here would hammer the API and drain the battery.
     */
    data class Paused(val remaining: Int, val reason: String) : DrainResult

    /** Writes that will never succeed, removed from the queue and surfaced. */
    data class Dropped(val failures: List<String>, val rest: DrainResult) : DrainResult
}

/**
 * The durable outbox for everything the driver records.
 *
 * A driver taps "arrived" in a dead zone at the back of a warehouse and the
 * phone syncs forty minutes later. Everything here exists so that tap survives
 * the gap, lands exactly once, and lands in the order it was made.
 */
class WriteQueue(
    private val dao: PendingWriteDao,
    private val api: TmsApiClient,
    private val uploader: FileUploader,
    private val files: LocalFileStore,
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
    /** Who is signed in now; see [PendingWrite.ownerId]. */
    private val owner: () -> String? = { null },
) {
    /**
     * Drains are serialized. Two at once (a screen retrying while a background
     * sync runs) would send the same rows twice and interleave the order.
     */
    private val drainLock = Mutex()

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observePending(): Flow<List<PendingWrite>> = dao.observeAll()

    /** Everything still queued, in the order it will be sent. */
    suspend fun pending(): List<PendingWrite> = dao.all()

    /**
     * Persist a write before telling the driver it worked.
     *
     * [body] must be the exact string to send, and [occurredAtMillis] the device
     * clock at the tap. The idempotency key is minted here, once, and travels
     * with the row for every retry.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun enqueue(path: String, body: String, occurredAtMillis: Long): Long =
        dao.insert(
            PendingWrite(
                path = path,
                body = body,
                idempotencyKey = Uuid.random().toString(),
                occurredAtMillis = occurredAtMillis,
                createdAtMillis = clock.now().toEpochMilliseconds(),
                ownerId = owner(),
            ),
        )

    /**
     * Persist bytes and the write that will cite them, as one row.
     *
     * [localPath] must already hold the bytes: this is called after the driver
     * took the photo, and the row is the promise that it will be delivered.
     * [body] is the write minus its `fileIds`, which cannot be known until the
     * server mints the file; the queue fills them in and then keeps that exact
     * string for every retry.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun enqueueUpload(
        path: String,
        body: String,
        occurredAtMillis: Long,
        localPath: String,
        contentType: String,
        purpose: String,
    ): Long = dao.insert(
        PendingWrite(
            path = path,
            body = body,
            idempotencyKey = Uuid.random().toString(),
            occurredAtMillis = occurredAtMillis,
            createdAtMillis = clock.now().toEpochMilliseconds(),
            kind = PendingWriteKind.UPLOAD,
            localPath = localPath,
            contentType = contentType,
            purpose = purpose,
            ownerId = owner(),
        ),
    )

    /** How many proofs are still on their way to [path], for the screen to say so. */
    fun observeUploadsFor(path: String): Flow<Int> = dao.observeUploadsFor(path)

    /**
     * Send what is due, oldest first, stopping at the first row that cannot go.
     *
     * Serial and stop-on-failure rather than best-effort parallel: skipping a
     * stuck row to send a later one is exactly how dispatch ends up with a
     * departure recorded before the arrival it followed.
     *
     * [force] tries the head now even if it is still waiting out its backoff.
     * It is for a driver who asked (pulled to refresh, opened the trip): they
     * are the best evidence there is that the network is back, and making them
     * wait minutes for a backoff timer is how "N not sent" becomes permanent.
     * Only the head is forced; a failure backs off again as usual.
     */
    suspend fun drain(force: Boolean = false): DrainResult = drainLock.withLock {
        forgetOtherAccounts()
        val dropped = mutableListOf<String>()
        val rest = sendWhatIsDue(dropped, force)

        if (dropped.isEmpty()) rest else DrainResult.Dropped(dropped, rest)
    }

    /**
     * The loop itself, returning why it stopped.
     *
     * A declared return type rather than a `var` accumulated across the loop:
     * Kotlin/Native narrows such a variable to the type it was initialised with
     * and inserts a downcast on every read, which throws the first time a write
     * fails and leaves the queue in exactly the state it exists to survive.
     */
    private suspend fun sendWhatIsDue(dropped: MutableList<String>, force: Boolean): DrainResult {
        var forceHead = force
        while (true) {
            val pending = dao.all()
            if (pending.isEmpty()) return DrainResult.Drained

            val now = clock.now().toEpochMilliseconds()
            val next = pending.first()

            // The head is not due yet. Later rows may be, but sending them first
            // would reorder the driver's actions, so the queue waits.
            if (next.nextAttemptAtMillis > now && !forceHead) {
                return DrainResult.Waiting(pending.size, next.nextAttemptAtMillis)
            }
            forceHead = false

            val outcome = send(next, now)
            if (outcome is SendOutcome.Dropped) dropped += outcome.reason
            if (outcome is SendOutcome.Stop) return outcome.result(pending.size)
        }
    }

    /**
     * Remove rows another account recorded.
     *
     * Signing out through the app clears the queue, but a session can also end on its own (a
     * refresh token the server refuses), and then the rows stay for that driver to sign back in.
     * If someone else signs in instead, those rows are not theirs to send: posted under this token
     * they would land as this driver's shift. Their photos go with them, for the same reason
     * [clear] deletes them. With no session there is nobody to compare against, so nothing is
     * touched.
     */
    private suspend fun forgetOtherAccounts() {
        val current = owner() ?: return
        dao.all()
            .filter { it.ownerId != null && it.ownerId != current }
            .forEach { write ->
                write.localPath?.let { files.delete(it) }
                dao.delete(write.id)
            }
    }

    /** Forget everything queued. Used when signing out, so writes cannot cross accounts. */
    suspend fun clear() {
        // The photos go with the rows: they are one driver's evidence, and the
        // next person to sign in on this phone must not be able to attach them.
        dao.all().forEach { write -> write.localPath?.let { files.delete(it) } }
        dao.clear()
    }

    private sealed interface SendOutcome {
        data object Sent : SendOutcome
        data class Dropped(val reason: String) : SendOutcome
        data class Stop(val result: (Int) -> DrainResult) : SendOutcome
    }

    private suspend fun send(write: PendingWrite, now: Long): SendOutcome {
        if (write.kind == PendingWriteKind.UPLOAD) {
            val ready = prepareUpload(write, now)
            if (ready !is UploadStep.Ready) return (ready as UploadStep.Failed).outcome
            return post(ready.write, now)
        }
        return post(write, now)
    }

    /**
     * What an upload row owes before its body can be posted: a file row on the
     * server, and its bytes in the bucket.
     *
     * Both happen in one attempt because the signed target expires in minutes,
     * so there is no point storing it to use later. What is stored is the
     * outcome: once the bytes are up, a retry skips straight to the post rather
     * than uploading a warehouse photo over 3G a second time.
     */
    private suspend fun prepareUpload(write: PendingWrite, now: Long): UploadStep {
        val localPath = write.localPath
        val contentType = write.contentType
        val purpose = write.purpose
        if (localPath == null || contentType == null || purpose == null) {
            dao.delete(write.id)
            return UploadStep.Failed(SendOutcome.Dropped("${write.path}: upload row is incomplete"))
        }

        if (write.uploaded) return UploadStep.Ready(write)

        // Read first: with the bytes gone there is nothing to upload, and
        // posting the proof anyway would document a stop with no evidence.
        val bytes = files.read(localPath)
            ?: run {
                dao.delete(write.id)
                return UploadStep.Failed(
                    SendOutcome.Dropped("${write.path}: the photo is no longer on this device"),
                )
            }

        val target = uploader.mint(purpose, contentType, bytes.size)
            .getOrElse { return UploadStep.Failed(deferUpload(write, it, now)) }

        // Recorded before the bytes go, so the body that cites this file is the
        // one every later attempt posts, byte for byte, under the same key.
        val body = withFileId(write.body, target.fileId)
        dao.markMinted(write.id, target.fileId, body)
        val minting = write.copy(fileId = target.fileId, body = body)

        uploader.send(target, bytes)
            .getOrElse { return UploadStep.Failed(deferUpload(minting, it, now)) }

        dao.markUploaded(minting.id)
        return UploadStep.Ready(minting.copy(uploaded = true))
    }

    private sealed interface UploadStep {
        data class Ready(val write: PendingWrite) : UploadStep
        data class Failed(val outcome: SendOutcome) : UploadStep
    }

    /** Puts the minted file id into the stored body, which had none until now. */
    private fun withFileId(body: String, fileId: String): String {
        val fields = TmsJson.parseToJsonElement(body).jsonObject.toMutableMap()
        fields["fileIds"] = JsonArray(listOf(JsonPrimitive(fileId)))
        return TmsJson.encodeToString(JsonObject.serializer(), JsonObject(fields))
    }

    /**
     * The disposition table for a step that is not the final post, with one
     * difference: nothing here drops.
     *
     * A JSON write the server refuses is dropped because replaying it can only
     * be refused again. These bytes are not like that. The driver photographed
     * a pallet and drove away; the pallet is gone, and a refusal that discards
     * the photo destroys the only evidence the delivery happened. So a refusal
     * that would drop parks the queue instead: the row stays, the phone still
     * has the file, the driver sees it as unsent, and someone can find out why
     * rather than the proof quietly disappearing.
     */
    private suspend fun deferUpload(write: PendingWrite, cause: Throwable, now: Long): SendOutcome {
        val failure = cause.apiFailure ?: ApiFailure.Transport(cause)
        defer(write, now, failure)
        return when (failure.disposition) {
            FailureDisposition.Retry ->
                SendOutcome.Stop { remaining -> DrainResult.Deferred(remaining, failure.message) }

            FailureDisposition.Drop,
            FailureDisposition.Pause,
            FailureDisposition.Reauthenticate,
            ->
                SendOutcome.Stop { remaining -> DrainResult.Paused(remaining, failure.message) }
        }
    }

    private suspend fun defer(write: PendingWrite, now: Long, failure: ApiFailure) {
        dao.markAttempted(
            write.id,
            write.attempts + 1,
            now + backoffMillis(write.attempts + 1),
            failure.message,
        )
    }

    private suspend fun post(write: PendingWrite, now: Long): SendOutcome {
        val result = api.post(
            path = write.path,
            body = write.body,
            deserializer = JsonElement.serializer(),
            idempotencyKey = write.idempotencyKey,
        )

        result.onSuccess {
            // A replay carries Idempotency-Replayed and the original response.
            // It means an earlier attempt did land, so this row is done.
            dao.delete(write.id)
            // The bytes are the server's now, and a phone with a shift of
            // delivery photos on it fills up fast.
            write.localPath?.let { path -> files.delete(path) }
            return SendOutcome.Sent
        }

        val failure = result.exceptionOrNull()?.apiFailure
            ?: ApiFailure.Transport(result.exceptionOrNull() ?: Exception("unknown"))

        if (failure.isFileNotReady() && write.kind == PendingWriteKind.UPLOAD &&
            write.attempts < MAX_UPLOAD_REDOS
        ) {
            // The server never saw the bytes, but the phone still has them. The
            // contract's answer is to redo the upload and replay, which is what
            // forgetting the minted file does; dropping here would destroy the
            // proof and then drop the delivery that needed it as photo_required.
            dao.resetUpload(write.id)
            defer(write, now, failure)
            return SendOutcome.Stop { remaining -> DrainResult.Deferred(remaining, failure.message) }
        }

        return when (failure.disposition) {
            FailureDisposition.Drop -> {
                dao.delete(write.id)
                write.localPath?.let { path -> files.delete(path) }
                SendOutcome.Dropped(describe(write, failure))
            }

            FailureDisposition.Pause, FailureDisposition.Reauthenticate -> {
                // The client already refreshed once and retried before this
                // reached us, so a 401 here means the session is genuinely gone.
                dao.markAttempted(
                    write.id,
                    write.attempts + 1,
                    now + backoffMillis(write.attempts + 1),
                    failure.message,
                )
                SendOutcome.Stop { remaining -> DrainResult.Paused(remaining, failure.message) }
            }

            FailureDisposition.Retry -> {
                dao.markAttempted(
                    write.id,
                    write.attempts + 1,
                    now + backoffMillis(write.attempts + 1),
                    failure.message,
                )
                SendOutcome.Stop { remaining -> DrainResult.Deferred(remaining, failure.message) }
            }
        }
    }

    private fun ApiFailure.isFileNotReady(): Boolean =
        (this as? ApiFailure.Http)?.rawCode == FILE_NOT_READY

    private fun describe(write: PendingWrite, failure: ApiFailure): String {
        val code = (failure as? ApiFailure.Http)?.rawCode.orEmpty()
        return if (code.isBlank()) {
            "${write.path}: ${failure.message}"
        } else {
            "${write.path}: $code, ${failure.message}"
        }
    }

    /**
     * Exponential, jittered, and capped. The jitter matters when a whole depot
     * of drivers regains signal at the same moment and would otherwise retry in
     * lockstep.
     */
    private fun backoffMillis(attempt: Int): Long {
        val exponential = BASE_BACKOFF_MILLIS shl min(attempt - 1, MAX_SHIFT)
        val capped = min(exponential, MAX_BACKOFF_MILLIS)
        return capped + random.nextLong(0, capped / 2 + 1)
    }

    private companion object {
        const val BASE_BACKOFF_MILLIS = 2_000L
        const val MAX_BACKOFF_MILLIS = 5 * 60 * 1000L
        const val MAX_SHIFT = 8

        /**
         * How many times a proof is re-uploaded after `file_not_ready` before it
         * is given up on. Bounded because the queue stops behind it: a proof that
         * can never land must not hold every later event hostage.
         */
        const val MAX_UPLOAD_REDOS = 3
        const val FILE_NOT_READY = "file_not_ready"
    }
}
