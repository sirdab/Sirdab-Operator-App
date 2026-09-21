package co.sirdab.driver.shared.feature.trip.impl.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.model.StopAddress
import co.sirdab.driver.shared.core.model.StopStatus
import co.sirdab.driver.shared.core.model.StopType
import co.sirdab.driver.shared.core.model.TripLifecycle
import co.sirdab.driver.shared.core.model.TripStop
import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import co.sirdab.driver.shared.feature.trip.api.ExceptionKind
import co.sirdab.driver.shared.feature.trip.api.ExceptionSeverity
import co.sirdab.driver.shared.feature.trip.api.TripEventRecorder
import co.sirdab.driver.shared.feature.trip.api.TripEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

private val TRIP = DriverTrip(
    id = "t1",
    reference = "TRP-1",
    status = TripLifecycle.ASSIGNED,
    stopCount = 1,
    stops = listOf(
        TripStop(
            id = "s1",
            legId = "l1",
            stopType = StopType.PICKUP,
            sequenceNumber = 1,
            status = StopStatus.PENDING,
            address = StopAddress(id = "a1", label = "Depot", street = "Street"),
        ),
    ),
)

private class FakeRepo(private val result: AppResult<DriverTrip> = AppResult.Success(TRIP)) :
    DriverTripRepository {
    override suspend fun trips(cursor: String?, limit: Int): AppResult<Page<DriverTrip>> =
        AppResult.Success(Page(emptyList(), null))

    override suspend fun trip(id: String): AppResult<DriverTrip> = result
}

private class FakeRecorder(
    private val result: AppResult<Unit> = AppResult.Success(Unit),
) : TripEventRecorder {
    data class Call(
        val tripId: String,
        val eventType: TripEventType,
        val stopId: String?,
        val occurredAtMillis: Long,
    )

    data class ExceptionCall(
        val tripId: String,
        val kind: ExceptionKind,
        val severity: ExceptionSeverity,
        val occurredAtMillis: Long,
        val stopId: String?,
        val note: String?,
    )

    data class ProofCall(
        val stopId: String,
        val bytes: ByteArray,
        val contentType: String,
        val capturedAtMillis: Long,
    )

    val calls = mutableListOf<Call>()
    val exceptions = mutableListOf<ExceptionCall>()
    val proofs = mutableListOf<ProofCall>()
    var syncs = 0

    override suspend fun record(
        tripId: String,
        eventType: TripEventType,
        stopId: String?,
        occurredAtMillis: Long,
        lat: Double?,
        lng: Double?,
    ): AppResult<Unit> {
        calls += Call(tripId, eventType, stopId, occurredAtMillis)
        return result
    }

    override suspend fun reportException(
        tripId: String,
        kind: ExceptionKind,
        severity: ExceptionSeverity,
        occurredAtMillis: Long,
        stopId: String?,
        note: String?,
    ): AppResult<Unit> {
        exceptions += ExceptionCall(tripId, kind, severity, occurredAtMillis, stopId, note)
        return result
    }

    override suspend fun attachPhotoProof(
        stopId: String,
        bytes: ByteArray,
        contentType: String,
        capturedAtMillis: Long,
        lat: Double?,
        lng: Double?,
    ): AppResult<Unit> {
        proofs += ProofCall(stopId, bytes, contentType, capturedAtMillis)
        return result
    }

    override fun pendingProofs(stopId: String): Flow<Int> =
        flowOf(proofs.count { it.stopId == stopId })

    override fun pendingCount(): Flow<Int> = flowOf(calls.size)

    override suspend fun sync() {
        syncs++
    }
}

private class FixedClock(private val millis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

class DriverTripDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `advances the stop before the write is sent`() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1_700_000L))
        testScheduler.advanceUntilIdle()

        vm.record("s1", StopAction.ARRIVE)

        // Before the coroutine that records it has even run: offline there is no
        // confirmation coming, so the screen cannot wait for one.
        assertThat(vm.state.value.trip?.stops?.first()?.status).isEqualTo(StopStatus.ARRIVED)
    }

    @Test
    fun `queues the event with the time of the tap`() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1_700_000L))
        testScheduler.advanceUntilIdle()

        vm.record("s1", StopAction.ARRIVE)
        testScheduler.advanceUntilIdle()

        assertThat(recorder.calls).isEqualTo(
            listOf(
                FakeRecorder.Call(
                    tripId = "t1",
                    eventType = TripEventType.ARRIVED_AT_STOP,
                    stopId = "s1",
                    // The device clock at the tap, which is what occurredAt carries.
                    occurredAtMillis = 1_700_000L,
                ),
            ),
        )
    }

    @Test
    fun `rolls the stop back when the write cannot be saved`() = runTest(dispatcher) {
        val recorder = FakeRecorder(AppResult.Failure(AppError("disk full")))
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1_700_000L))
        testScheduler.advanceUntilIdle()

        vm.record("s1", StopAction.ARRIVE)
        testScheduler.advanceUntilIdle()

        // Nothing was persisted, so the screen must stop claiming otherwise.
        assertThat(vm.state.value.trip?.stops?.first()?.status).isEqualTo(StopStatus.PENDING)
        assertThat(vm.state.value.errorMessage).isEqualTo("disk full")
    }

    @Test
    fun `does nothing when the trip has not loaded`() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = DriverTripDetailViewModel(
            "t1",
            FakeRepo(AppResult.Failure(AppError("offline"))),
            recorder,
            FixedClock(1L),
        )
        testScheduler.advanceUntilIdle()

        vm.record("s1", StopAction.ARRIVE)
        testScheduler.advanceUntilIdle()

        assertThat(recorder.calls).isEqualTo(emptyList())
    }

    @Test
    fun `a successful record leaves no error behind`() = runTest(dispatcher) {
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), FakeRecorder(), FixedClock(1L))
        testScheduler.advanceUntilIdle()

        vm.record("s1", StopAction.ARRIVE)
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isNull()
    }

    @Test
    fun `reports a problem against the stop being worked`() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1_700_000L))
        testScheduler.advanceUntilIdle()

        vm.reportException(ExceptionKind.VEHICLE_BREAKDOWN, ExceptionSeverity.HIGH, "Tyre blew")
        testScheduler.advanceUntilIdle()

        assertThat(recorder.exceptions).isEqualTo(
            listOf(
                FakeRecorder.ExceptionCall(
                    tripId = "t1",
                    kind = ExceptionKind.VEHICLE_BREAKDOWN,
                    severity = ExceptionSeverity.HIGH,
                    // The device clock at the tap, as occurredAt.
                    occurredAtMillis = 1_700_000L,
                    stopId = "s1",
                    note = "Tyre blew",
                ),
            ),
        )
        assertThat(vm.state.value.exceptionReported).isEqualTo(true)
    }

    @Test
    fun `reporting never advances the stop`() = runTest(dispatcher) {
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), FakeRecorder(), FixedClock(1L))
        testScheduler.advanceUntilIdle()

        vm.reportException(ExceptionKind.DELAY, ExceptionSeverity.LOW, "")
        testScheduler.advanceUntilIdle()

        // An exception is information for dispatch, not a step in the trip.
        assertThat(vm.state.value.trip?.stops?.first()?.status).isEqualTo(StopStatus.PENDING)
    }

    @Test
    fun `surfaces a report that could not be saved`() = runTest(dispatcher) {
        val recorder = FakeRecorder(AppResult.Failure(AppError("disk full")))
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1L))
        testScheduler.advanceUntilIdle()

        vm.reportException(ExceptionKind.DAMAGE, ExceptionSeverity.HIGH, "")
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("disk full")
        assertThat(vm.state.value.exceptionReported).isEqualTo(false)
    }

    @Test
    fun `opening and dismissing the report sheet toggles cleanly`() = runTest(dispatcher) {
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), FakeRecorder(), FixedClock(1L))
        testScheduler.advanceUntilIdle()

        vm.openExceptionReport()
        assertThat(vm.state.value.isReportingException).isEqualTo(true)

        vm.dismissExceptionReport()
        assertThat(vm.state.value.isReportingException).isEqualTo(false)
    }

    @Test
    fun `loading sends what is queued before it reads`() = runTest(dispatcher) {
        val recorder = FakeRecorder()

        DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1L))
        testScheduler.advanceUntilIdle()

        // Opening a trip is the app's evidence of a network. A proof stuck in
        // the queue goes now, so the reload right after shows it landed.
        assertThat(recorder.syncs).isEqualTo(1)
    }

    @Test
    fun `a photo is kept with the time it was taken`() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1_700_000L))
        testScheduler.advanceUntilIdle()

        vm.attachPhoto("s1", byteArrayOf(1, 2, 3), "image/jpeg")
        testScheduler.advanceUntilIdle()

        assertThat(recorder.proofs.size).isEqualTo(1)
        assertThat(recorder.proofs.first().stopId).isEqualTo("s1")
        assertThat(recorder.proofs.first().contentType).isEqualTo("image/jpeg")
        // Stamped at the shutter, like every other thing the driver records.
        assertThat(recorder.proofs.first().capturedAtMillis).isEqualTo(1_700_000L)
    }

    @Test
    fun `a photo that could not be kept is surfaced rather than assumed`() = runTest(dispatcher) {
        val recorder = FakeRecorder(AppResult.Failure(AppError("disk full")))
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1L))
        testScheduler.advanceUntilIdle()

        vm.attachPhoto("s1", byteArrayOf(1), "image/jpeg")
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("disk full")
    }

    @Test
    fun `a queued photo does not count as proof the server has`() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = DriverTripDetailViewModel("t1", FakeRepo(), recorder, FixedClock(1L))
        testScheduler.advanceUntilIdle()

        vm.attachPhoto("s1", byteArrayOf(1), "image/jpeg")
        testScheduler.advanceUntilIdle()

        // The photo is safely queued...
        assertThat(recorder.proofs.size).isEqualTo(1)
        // ...but the stop still reads zero proofs, because that count is the
        // server's. Letting a queued photo unlock Delivered would post the
        // delivery against a stop the server sees as unproven, and the
        // photo_required that came back would drop it.
        assertThat(vm.state.value.trip?.stops?.first()?.photoProofCount).isEqualTo(0)
    }
}
