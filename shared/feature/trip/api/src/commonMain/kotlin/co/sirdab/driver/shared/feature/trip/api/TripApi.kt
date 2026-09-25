package co.sirdab.driver.shared.feature.trip.api

import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip

import co.sirdab.driver.shared.core.model.Page

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface TripRoute : NavKey {

    @Serializable data object Trips : TripRoute
    @Serializable data class Detail(val tripId: String) : TripRoute
}

/**
 * The trips the TMS says this driver is running.
 *
 * Separate from [TripRepository], which is the demo world's single-active-trip script. The two
 * model a trip differently enough that one interface would lie about both: a TMS trip is a list of
 * stops with no rate and no load id attached, and its status is derived from its legs rather than
 * advanced by the app.
 */
interface DriverTripRepository {
    /** [cursor] is whatever the previous page returned, or null for the first. */
    suspend fun trips(cursor: String? = null, limit: Int = 50): AppResult<Page<DriverTrip>>

    suspend fun trip(id: String): AppResult<DriverTrip>
}

/** What a driver can record against a stop. Mirrors the contract's TripEventType. */
enum class TripEventType(val wire: String) {
    TRIP_STARTED("trip_started"),
    ARRIVED_AT_STOP("arrived_at_stop"),
    LOADING_STARTED("loading_started"),
    LOADING_FINISHED("loading_finished"),
    DEPARTED_STOP("departed_stop"),
    DELIVERED("delivered"),
    EXCEPTION("exception"),
    TRIP_COMPLETED("trip_completed"),
}

/** Why a trip went wrong. Mirrors the contract's ExceptionKind. */
enum class ExceptionKind(val wire: String) {
    DELAY("delay"),
    DAMAGE("damage"),
    REFUSED("refused"),
    ACCESS_DENIED("access_denied"),
    VEHICLE_BREAKDOWN("vehicle_breakdown"),
    WRONG_ADDRESS("wrong_address"),
}

enum class ExceptionSeverity(val wire: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

/**
 * Records what the driver did.
 *
 * Returns as soon as the write is durably queued, not when the server has it: a
 * driver at the back of a warehouse has no signal, and the app must not make
 * them wait for one to confirm they arrived.
 */
interface TripEventRecorder {
    suspend fun record(
        tripId: String,
        eventType: TripEventType,
        stopId: String? = null,
        occurredAtMillis: Long,
        lat: Double? = null,
        lng: Double? = null,
    ): AppResult<Unit>

    /**
     * Report something that went wrong.
     *
     * Never moves the trip on: an exception is information for the dispatcher,
     * and what it means for the load is their call.
     */
    suspend fun reportException(
        tripId: String,
        kind: ExceptionKind,
        severity: ExceptionSeverity,
        occurredAtMillis: Long,
        stopId: String? = null,
        note: String? = null,
    ): AppResult<Unit>

    /**
     * Attach a photo to a stop.
     *
     * [bytes] are already downscaled and are persisted before this returns, so
     * the driver can photograph a pallet in a dead zone and walk away. The
     * upload is queued behind whatever the driver did before it and ahead of
     * whatever they do next, which is what keeps a proof in front of the
     * delivery that the server will not accept without it.
     */
    suspend fun attachPhotoProof(
        stopId: String,
        bytes: ByteArray,
        contentType: String,
        capturedAtMillis: Long,
        lat: Double? = null,
        lng: Double? = null,
    ): AppResult<Unit>

    /** How many photos for this stop are still on their way. */
    fun pendingProofs(stopId: String): Flow<Int>

    /** How many of this driver's actions have not reached the server yet. */
    fun pendingCount(): Flow<Int>

    /**
     * This trip's events still in the queue, oldest first.
     *
     * A reload has to lay these back over what the server returns: until they
     * land, the server's copy is older than what the driver did, and showing it
     * as-is puts the Arrive button back under a driver who already tapped it.
     */
    suspend fun queuedEvents(tripId: String): List<QueuedTripEvent>

    /**
     * How many writes the server refused for good since the driver last looked.
     *
     * Those are gone from the queue, so the pending count falls as if they had
     * been sent. The driver has to hear otherwise, or a refused delivery reads
     * exactly like a successful one.
     */
    fun droppedCount(): Flow<Int>

    /** The driver has seen the refusals counted by [droppedCount]. */
    fun acknowledgeDropped()

    /**
     * Try the queue again.
     *
     * Recording drains as a side effect, which is enough while a driver keeps
     * tapping, but not when they stop: a photo that failed at a loading bay is
     * otherwise stuck until the next thing they happen to do. Opening a trip or
     * pulling to refresh is the moment the app has evidence of a network, so it
     * is the moment to retry.
     */
    suspend fun sync()
}

/** A trip event the driver recorded that has not reached the server yet. */
data class QueuedTripEvent(
    val eventType: TripEventType,
    val stopId: String?,
    val occurredAtMillis: Long,
)
