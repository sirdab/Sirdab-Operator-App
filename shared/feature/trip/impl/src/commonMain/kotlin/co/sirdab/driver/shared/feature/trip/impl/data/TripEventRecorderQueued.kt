package co.sirdab.driver.shared.feature.trip.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.network.FilePurpose
import co.sirdab.driver.shared.core.platform.files.LocalFileStore
import co.sirdab.driver.shared.core.queue.DrainResult
import co.sirdab.driver.shared.core.queue.WriteQueue
import co.sirdab.driver.shared.feature.trip.api.ExceptionKind
import co.sirdab.driver.shared.feature.trip.api.ExceptionSeverity
import co.sirdab.driver.shared.feature.trip.api.QueuedTripEvent
import co.sirdab.driver.shared.feature.trip.api.TripEventRecorder
import co.sirdab.driver.shared.feature.trip.api.TripEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
internal data class TripEventInputDto(
    val eventType: String,
    val stopId: String? = null,
    val occurredAt: String,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("accuracyM") val accuracyM: Double? = null,
)

@Serializable
internal data class ProofInputDto(
    val proofType: String,
    val capturedAt: String,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
internal data class ExceptionInputDto(
    val tripId: String,
    val stopId: String? = null,
    val kind: String,
    val severity: String,
    val occurredAt: String,
    val note: String? = null,
)

/**
 * Queues trip events rather than posting them.
 *
 * Every tap is persisted before the driver is told it worked, and the queue does
 * the sending. That is the whole offline story: the screen moves on, the write
 * survives a force-close, and it lands exactly once whenever signal returns.
 */
class TripEventRecorderQueued(
    private val queue: WriteQueue,
    private val files: LocalFileStore,
    private val json: Json = Json { explicitNulls = false; encodeDefaults = false },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: Clock = Clock.System,
) : TripEventRecorder {

    private val dropped = MutableStateFlow(0)

    /** The one pending wake-up for rows waiting out a backoff. */
    private var retry: Job? = null
    private val retryLock = Mutex()

    override suspend fun record(
        tripId: String,
        eventType: TripEventType,
        stopId: String?,
        occurredAtMillis: Long,
        lat: Double?,
        lng: Double?,
    ): AppResult<Unit> {
        val body = json.encodeToString(
            TripEventInputDto.serializer(),
            TripEventInputDto(
                eventType = eventType.wire,
                stopId = stopId,
                // The contract requires an explicit offset. Instant renders UTC
                // with a trailing Z, which it accepts, and which cannot be
                // misread the way a bare local time would be.
                occurredAt = Instant.fromEpochMilliseconds(occurredAtMillis).toString(),
                lat = lat,
                lng = lng,
            ),
        )

        return enqueue(eventsPath(tripId), body, occurredAtMillis)
    }

    override suspend fun reportException(
        tripId: String,
        kind: ExceptionKind,
        severity: ExceptionSeverity,
        occurredAtMillis: Long,
        stopId: String?,
        note: String?,
    ): AppResult<Unit> {
        val body = json.encodeToString(
            ExceptionInputDto.serializer(),
            ExceptionInputDto(
                tripId = tripId,
                stopId = stopId,
                kind = kind.wire,
                severity = severity.wire,
                occurredAt = Instant.fromEpochMilliseconds(occurredAtMillis).toString(),
                note = note?.takeIf { it.isNotBlank() },
            ),
        )
        return enqueue("api/driver/exceptions", body, occurredAtMillis)
    }

    override suspend fun attachPhotoProof(
        stopId: String,
        bytes: ByteArray,
        contentType: String,
        capturedAtMillis: Long,
        lat: Double?,
        lng: Double?,
    ): AppResult<Unit> {
        // `fileIds` is deliberately absent: the file does not exist until the
        // queue mints it, and the queue writes it into this body then.
        val body = json.encodeToString(
            ProofInputDto.serializer(),
            ProofInputDto(
                proofType = PHOTO_PROOF,
                capturedAt = Instant.fromEpochMilliseconds(capturedAtMillis).toString(),
                lat = lat,
                lng = lng,
            ),
        )

        val path = proofPath(stopId)
        return runCatching {
            // Bytes first. A queue row pointing at a file that was never
            // written is a proof that can only ever be dropped.
            val localPath = files.write("$stopId-$capturedAtMillis.jpg", bytes)
            queue.enqueueUpload(
                path = path,
                body = body,
                occurredAtMillis = capturedAtMillis,
                localPath = localPath,
                contentType = contentType,
                purpose = FilePurpose.PROOF_PHOTO.wire,
            )
        }.fold(
            onSuccess = {
                drainInBackground()
                AppResult.Success(Unit)
            },
            onFailure = { AppResult.Failure(AppError(it.message ?: "Could not save that photo.")) },
        )
    }

    override fun pendingProofs(stopId: String): Flow<Int> =
        queue.observeUploadsFor(proofPath(stopId))

    override fun pendingCount(): Flow<Int> = queue.observeCount()

    override suspend fun queuedEvents(tripId: String): List<QueuedTripEvent> {
        val path = eventsPath(tripId)
        return queue.pending()
            .filter { it.path == path }
            .mapNotNull { write ->
                val input = runCatching {
                    json.decodeFromString(TripEventInputDto.serializer(), write.body)
                }.getOrNull() ?: return@mapNotNull null
                val type = TripEventType.entries.firstOrNull { it.wire == input.eventType }
                    ?: return@mapNotNull null
                QueuedTripEvent(type, input.stopId, write.occurredAtMillis)
            }
    }

    override fun droppedCount(): Flow<Int> = dropped.asStateFlow()

    override fun acknowledgeDropped() {
        dropped.value = 0
    }

    /** Forced: the driver asking is the best evidence there is that the network is back. */
    override suspend fun sync() {
        drainNow(force = true)
    }

    private fun drainInBackground() {
        scope.launch { drainNow(force = false) }
    }

    private suspend fun drainNow(force: Boolean) {
        val result = try {
            queue.drain(force)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // A disk or database error mid-drain. The rows are still on disk and
            // the next drain will find them; crashing the app here would only
            // take the driver's screen down with it.
            return
        }
        handle(result)
    }

    private suspend fun handle(result: DrainResult) {
        when (result) {
            is DrainResult.Dropped -> {
                dropped.update { it + result.failures.size }
                handle(result.rest)
            }
            // Nothing else would wake the queue: without this, a row that failed
            // once sits unsent until the driver happens to tap something.
            is DrainResult.Waiting -> scheduleRetry(result.nextAttemptAtMillis)
            // The row that failed has just been given its backoff; asking again
            // costs one read and comes back Waiting with the time to wake up.
            is DrainResult.Deferred -> scheduleRetry(clock.now().toEpochMilliseconds())
            // Paused needs something to change (a sign-in, a server fix), which a
            // timer cannot supply; the next tap or pull tries again.
            is DrainResult.Paused, DrainResult.Drained -> Unit
        }
    }

    private suspend fun scheduleRetry(atMillis: Long) {
        retryLock.withLock {
            retry?.cancel()
            retry = scope.launch {
                delay((atMillis - clock.now().toEpochMilliseconds()).coerceAtLeast(0))
                drainNow(force = false)
            }
        }
    }

    private fun proofPath(stopId: String) = "api/driver/stops/$stopId/proofs"

    private fun eventsPath(tripId: String) = "api/driver/trips/$tripId/events"

    private suspend fun enqueue(path: String, body: String, occurredAtMillis: Long): AppResult<Unit> =
        runCatching { queue.enqueue(path, body, occurredAtMillis) }.fold(
            onSuccess = {
                // Fire and forget: the write is already safe on disk, so a failure
                // here costs nothing but a later retry.
                drainInBackground()
                AppResult.Success(Unit)
            },
            // Only a storage failure reaches here, and it is the one case the
            // driver must see: nothing was saved, so nothing will be sent.
            onFailure = { AppResult.Failure(AppError(it.message ?: "Could not save that.")) },
        )

    private companion object {
        /** The only proof type the server's photo rule counts. */
        const val PHOTO_PROOF = "photo"
    }
}
