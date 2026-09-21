package co.sirdab.driver.shared.feature.trip.impl.presentation

import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.StopStatus
import co.sirdab.driver.shared.core.model.StopType
import co.sirdab.driver.shared.core.model.TripLifecycle
import co.sirdab.driver.shared.core.model.TripStop
import co.sirdab.driver.shared.feature.trip.api.TripEventType

/**
 * Something the driver can record against the trip as a whole.
 *
 * Only starting is offered. Completing is the server's to decide, from whether
 * every leg has been delivered, so a button for it would be a guess.
 */
enum class TripAction(val event: TripEventType) {
    START(TripEventType.TRIP_STARTED),
}

/** Whether this trip is still waiting to be started. */
fun tripActionFor(trip: DriverTrip): TripAction? = when (trip.status) {
    TripLifecycle.CREATED, TripLifecycle.ASSIGNED -> TripAction.START
    else -> null
}

/**
 * Something the driver can record at a stop.
 *
 * [primary] is the one action that moves the trip forward; the rest are optional
 * detail the driver may or may not bother with.
 */
enum class StopAction(
    val event: TripEventType,
    val primary: Boolean,
    /** The server refuses this one until a photo proof is attached to the stop. */
    val requiresPhoto: Boolean = false,
) {
    ARRIVE(TripEventType.ARRIVED_AT_STOP, primary = true),
    START_LOADING(TripEventType.LOADING_STARTED, primary = false),
    FINISH_LOADING(TripEventType.LOADING_FINISHED, primary = true, requiresPhoto = true),
    DELIVER(TripEventType.DELIVERED, primary = true, requiresPhoto = true),
    DEPART(TripEventType.DEPARTED_STOP, primary = true),
}

/**
 * What this stop offers right now.
 *
 * The rules mirror what the server will actually accept, so a button is never
 * shown for a write that comes back 409. That matters more than usual here: the
 * write is queued offline and the refusal may not arrive for hours, by which
 * point the driver is long gone and the write is simply dropped.
 *
 * Both kinds of stop run arrive -> close -> depart. What closes them differs:
 * a pickup is closed by finishing loading, a dropoff by delivering, and only
 * the latter closes the leg.
 */
fun actionsFor(trip: DriverTrip, stop: TripStop): List<StopAction> {
    val candidates = when (stop.status) {
        // The server refuses arriving out of order, so the button is not offered
        // until every earlier stop is behind the driver.
        StopStatus.PENDING ->
            if (earlierStopsFinished(trip, stop)) listOf(StopAction.ARRIVE) else emptyList()

        StopStatus.ARRIVED -> when (stop.stopType) {
            StopType.PICKUP -> listOf(StopAction.START_LOADING, StopAction.FINISH_LOADING)
            StopType.DROPOFF -> listOf(StopAction.DELIVER)
        }

        StopStatus.COMPLETED -> listOf(StopAction.DEPART)

        StopStatus.DEPARTED, StopStatus.SKIPPED -> emptyList()
    }

    return candidates.filter { action ->
        trip.status in action.allowedTripStatuses() &&
            (!action.requiresPhoto || stop.photoProofCount > 0)
    }
}

/**
 * Stop work only happens on a running trip: before `trip_started` the server
 * refuses every one of these.
 *
 * Departing is the exception, because delivering the last leg already moves the
 * trip to complete while the driver is still standing at the stop. Withholding
 * the button there would strand that stop closed but never left.
 */
private fun StopAction.allowedTripStatuses(): Set<TripLifecycle> = when (this) {
    StopAction.DEPART -> setOf(TripLifecycle.IN_TRANSIT, TripLifecycle.COMPLETE)
    else -> setOf(TripLifecycle.IN_TRANSIT)
}

/**
 * Whether this stop is only waiting on a photo.
 *
 * Distinguishing "nothing to do here" from "do the paperwork first" is the whole
 * difference between a screen that looks finished and one that looks broken.
 */
fun awaitingPhotoProof(trip: DriverTrip, stop: TripStop): Boolean =
    stop.photoProofCount == 0 &&
        stop.status == StopStatus.ARRIVED &&
        trip.status == TripLifecycle.IN_TRANSIT

private fun earlierStopsFinished(trip: DriverTrip, stop: TripStop): Boolean =
    trip.stops.none {
        it.sequenceNumber < stop.sequenceNumber &&
            it.status != StopStatus.DEPARTED &&
            it.status != StopStatus.COMPLETED &&
            it.status != StopStatus.SKIPPED
    }

/**
 * The trip as it will look once [action] is recorded.
 *
 * Applied locally the moment the driver taps, because the write is queued rather
 * than sent: with no signal there is no confirmation coming, and a screen that
 * waited for one would simply never advance.
 */
fun DriverTrip.applyLocally(stopId: String, action: StopAction, atMillis: Long): DriverTrip =
    copy(
        stops = stops.map { stop ->
            if (stop.id != stopId) {
                stop
            } else {
                when (action) {
                    StopAction.ARRIVE ->
                        stop.copy(status = StopStatus.ARRIVED, arrivedAtMillis = atMillis)
                    // Both of these close the stop, as they do on the server.
                    StopAction.FINISH_LOADING, StopAction.DELIVER ->
                        stop.copy(status = StopStatus.COMPLETED)
                    StopAction.DEPART ->
                        stop.copy(status = StopStatus.DEPARTED, departedAtMillis = atMillis)
                    // Loading is recorded but changes no status, on the server too.
                    StopAction.START_LOADING -> stop
                }
            }
        },
    )

/** The trip as it will look once [action] is recorded. */
fun DriverTrip.applyLocally(action: TripAction): DriverTrip = when (action) {
    TripAction.START -> copy(status = TripLifecycle.IN_TRANSIT)
}
