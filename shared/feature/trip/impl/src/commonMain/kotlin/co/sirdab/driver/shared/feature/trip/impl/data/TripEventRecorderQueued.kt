package co.sirdab.driver.shared.feature.trip.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.network.FilePurpose
import co.sirdab.driver.shared.core.platform.files.LocalFileStore
import co.sirdab.driver.shared.core.queue.WriteQueue
import co.sirdab.driver.shared.feature.trip.api.ExceptionKind
import co.sirdab.driver.shared.feature.trip.api.ExceptionSeverity
import co.sirdab.driver.shared.feature.trip.api.TripEventRecorder
import co.sirdab.driver.shared.feature.trip.api.TripEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
) : TripEventRecorder {

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

        return enqueue("api/driver/trips/$tripId/events", body, occurredAtMillis)
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
                scope.launch { queue.drain() }
                AppResult.Success(Unit)
            },
            onFailure = { AppResult.Failure(AppError(it.message ?: "Could not save that photo.")) },
        )
    }

    override fun pendingProofs(stopId: String): Flow<Int> =
        queue.observeUploadsFor(proofPath(stopId))

    override fun pendingCount(): Flow<Int> = queue.observeCount()

    override suspend fun sync() {
        queue.drain()
    }

    private fun proofPath(stopId: String) = "api/driver/stops/$stopId/proofs"

    private suspend fun enqueue(path: String, body: String, occurredAtMillis: Long): AppResult<Unit> =
        runCatching { queue.enqueue(path, body, occurredAtMillis) }.fold(
            onSuccess = {
                // Fire and forget: the write is already safe on disk, so a failure
                // here costs nothing but a later retry.
                scope.launch { queue.drain() }
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
