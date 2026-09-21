package co.sirdab.driver.shared.feature.trip.impl.presentation

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.StopAddress
import co.sirdab.driver.shared.core.model.StopStatus
import co.sirdab.driver.shared.core.model.StopType
import co.sirdab.driver.shared.core.model.TripLifecycle
import co.sirdab.driver.shared.core.model.TripStop
import kotlin.test.Test

private fun stop(
    id: String,
    sequence: Int,
    type: StopType,
    status: StopStatus,
    photos: Int = 0,
) = TripStop(
    id = id,
    legId = "l1",
    stopType = type,
    sequenceNumber = sequence,
    status = status,
    address = StopAddress(id = "a$id", label = "Place", street = "Street"),
    photoProofCount = photos,
)

private fun trip(
    vararg stops: TripStop,
    status: TripLifecycle = TripLifecycle.IN_TRANSIT,
) = DriverTrip(
    id = "t1",
    reference = "TRP-1",
    status = status,
    stopCount = stops.size,
    stops = stops.toList(),
)

/**
 * These rules have to agree with what recordTripEvent accepts. A button the
 * server would refuse is worse here than elsewhere: the write is queued, so the
 * 409 may not arrive until hours later, by which point it is silently dropped.
 */
class StopActionTest {

    @Test
    fun `a pending first stop offers only arrive`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.PENDING)

        assertThat(actionsFor(trip(pickup), pickup)).isEqualTo(listOf(StopAction.ARRIVE))
    }

    @Test
    fun `a later stop offers nothing until the one before it is finished`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.ARRIVED)
        val dropoff = stop("s2", 2, StopType.DROPOFF, StopStatus.PENDING)

        // The server answers stop_out_of_sequence here, so the button is hidden.
        assertThat(actionsFor(trip(pickup, dropoff), dropoff)).isEmpty()
    }

    @Test
    fun `a departed pickup counts as finished for the stop after it`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.DEPARTED)
        val dropoff = stop("s2", 2, StopType.DROPOFF, StopStatus.PENDING)

        assertThat(actionsFor(trip(pickup, dropoff), dropoff)).isEqualTo(listOf(StopAction.ARRIVE))
    }

    @Test
    fun `an arrived pickup with proof offers loading detail and the close`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.ARRIVED, photos = 1)

        // Departing is not here: the server takes departed_stop only from
        // completed, which finishing the loading is what produces.
        assertThat(actionsFor(trip(pickup), pickup)).isEqualTo(
            listOf(StopAction.START_LOADING, StopAction.FINISH_LOADING),
        )
    }

    @Test
    fun `an arrived dropoff with proof offers delivery`() {
        val dropoff = stop("s1", 1, StopType.DROPOFF, StopStatus.ARRIVED, photos = 2)

        assertThat(actionsFor(trip(dropoff), dropoff)).isEqualTo(listOf(StopAction.DELIVER))
    }

    @Test
    fun `closing a stop is withheld until a photo proof exists`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.ARRIVED)
        val dropoff = stop("s2", 2, StopType.DROPOFF, StopStatus.ARRIVED)

        // The server answers photo_required, so neither close is offered. Loading
        // itself carries no proof, so it survives.
        assertThat(actionsFor(trip(pickup), pickup)).isEqualTo(listOf(StopAction.START_LOADING))
        assertThat(actionsFor(trip(dropoff), dropoff)).isEmpty()
    }

    @Test
    fun `a stop held up only by its photo says so`() {
        val dropoff = stop("s1", 1, StopType.DROPOFF, StopStatus.ARRIVED)

        assertThat(awaitingPhotoProof(trip(dropoff), dropoff)).isEqualTo(true)
        assertThat(awaitingPhotoProof(trip(dropoff), dropoff.copy(photoProofCount = 1)))
            .isEqualTo(false)
    }

    @Test
    fun `a closed stop offers departure`() {
        val done = stop("s1", 1, StopType.DROPOFF, StopStatus.COMPLETED)

        assertThat(actionsFor(trip(done), done)).isEqualTo(listOf(StopAction.DEPART))
    }

    @Test
    fun `the last stop can still be left after delivery completes the trip`() {
        val done = stop("s1", 1, StopType.DROPOFF, StopStatus.COMPLETED)

        // Delivering the final leg moves the trip to complete while the driver is
        // still standing there, and the server keeps accepting departed_stop.
        assertThat(actionsFor(trip(done, status = TripLifecycle.COMPLETE), done))
            .isEqualTo(listOf(StopAction.DEPART))
    }

    @Test
    fun `a left or skipped stop offers nothing`() {
        val gone = stop("s1", 1, StopType.DROPOFF, StopStatus.DEPARTED)
        val skipped = stop("s2", 2, StopType.DROPOFF, StopStatus.SKIPPED)
        val t = trip(gone, skipped)

        assertThat(actionsFor(t, gone)).isEmpty()
        assertThat(actionsFor(t, skipped)).isEmpty()
    }

    @Test
    fun `a trip that has not started offers no stop work`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.PENDING)

        // Every stop event is refused until trip_started lands.
        for (status in listOf(TripLifecycle.CREATED, TripLifecycle.ASSIGNED)) {
            assertThat(actionsFor(trip(pickup, status = status), pickup)).isEmpty()
        }
    }

    @Test
    fun `a trip that has not started offers to start`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.PENDING)

        assertThat(tripActionFor(trip(pickup, status = TripLifecycle.ASSIGNED)))
            .isEqualTo(TripAction.START)
        assertThat(tripActionFor(trip(pickup, status = TripLifecycle.CREATED)))
            .isEqualTo(TripAction.START)
    }

    @Test
    fun `a running or finished trip is never offered a start`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.PENDING)

        for (status in listOf(
            TripLifecycle.IN_TRANSIT,
            TripLifecycle.COMPLETE,
            TripLifecycle.CANCELLED,
            TripLifecycle.FAILED,
        )) {
            assertThat(tripActionFor(trip(pickup, status = status))).isNull()
        }
    }

    @Test
    fun `a cancelled or failed trip offers nothing at all`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.PENDING)

        for (status in listOf(TripLifecycle.CANCELLED, TripLifecycle.FAILED)) {
            assertThat(actionsFor(trip(pickup, status = status), pickup)).isEmpty()
        }
    }

    @Test
    fun `starting moves the trip into transit`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.PENDING)

        val after = trip(pickup, status = TripLifecycle.ASSIGNED).applyLocally(TripAction.START)

        assertThat(after.status).isEqualTo(TripLifecycle.IN_TRANSIT)
    }

    @Test
    fun `arriving marks the stop arrived and stamps the tap time`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.PENDING)

        val after = trip(pickup).applyLocally("s1", StopAction.ARRIVE, 1_700_000L)

        assertThat(after.stops[0].status).isEqualTo(StopStatus.ARRIVED)
        assertThat(after.stops[0].arrivedAtMillis).isEqualTo(1_700_000L)
    }

    @Test
    fun `finishing loading and delivering both close the stop`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.ARRIVED, photos = 1)
        val dropoff = stop("s1", 1, StopType.DROPOFF, StopStatus.ARRIVED, photos = 1)

        assertThat(trip(pickup).applyLocally("s1", StopAction.FINISH_LOADING, 1L).stops[0].status)
            .isEqualTo(StopStatus.COMPLETED)
        assertThat(trip(dropoff).applyLocally("s1", StopAction.DELIVER, 1L).stops[0].status)
            .isEqualTo(StopStatus.COMPLETED)
    }

    @Test
    fun `closing a stop is not leaving it`() {
        val dropoff = stop("s1", 1, StopType.DROPOFF, StopStatus.ARRIVED, photos = 1)

        // Departure is its own event now, so nothing may invent a time for it.
        val after = trip(dropoff).applyLocally("s1", StopAction.DELIVER, 1_700_000L)

        assertThat(after.stops[0].departedAtMillis).isNull()
    }

    @Test
    fun `departing marks the stop left and stamps the tap time`() {
        val done = stop("s1", 1, StopType.DROPOFF, StopStatus.COMPLETED)

        val after = trip(done).applyLocally("s1", StopAction.DEPART, 1_700_000L)

        assertThat(after.stops[0].status).isEqualTo(StopStatus.DEPARTED)
        assertThat(after.stops[0].departedAtMillis).isEqualTo(1_700_000L)
    }

    @Test
    fun `recording the start of loading changes no status`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.ARRIVED)

        assertThat(trip(pickup).applyLocally("s1", StopAction.START_LOADING, 1L).stops[0].status)
            .isEqualTo(StopStatus.ARRIVED)
    }

    @Test
    fun `applying to one stop leaves the others alone`() {
        val pickup = stop("s1", 1, StopType.PICKUP, StopStatus.COMPLETED)
        val dropoff = stop("s2", 2, StopType.DROPOFF, StopStatus.PENDING)

        val after = trip(pickup, dropoff).applyLocally("s1", StopAction.DEPART, 1L)

        assertThat(after.stops[1].status).isEqualTo(StopStatus.PENDING)
    }
}
