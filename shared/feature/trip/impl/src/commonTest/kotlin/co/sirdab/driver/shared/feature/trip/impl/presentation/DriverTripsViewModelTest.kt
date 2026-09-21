package co.sirdab.driver.shared.feature.trip.impl.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.model.TripLifecycle
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

private fun fakeTrip(id: String) = DriverTrip(
    id = id,
    reference = "TRP-$id",
    status = TripLifecycle.ASSIGNED,
    stopCount = 2,
)

private class FakeTrips(
    private val pages: List<AppResult<Page<DriverTrip>>>,
) : DriverTripRepository {
    val cursors = mutableListOf<String?>()
    private var call = 0

    override suspend fun trips(cursor: String?, limit: Int): AppResult<Page<DriverTrip>> {
        cursors += cursor
        return pages[minOf(call++, pages.size - 1)]
    }

    override suspend fun trip(id: String): AppResult<DriverTrip> = AppResult.Success(fakeTrip(id))
}

/** Records only that a sync happened; the list VM asks for nothing else. */
private class CountingRecorder : TripEventRecorder {
    var syncs = 0

    override suspend fun record(
        tripId: String,
        eventType: TripEventType,
        stopId: String?,
        occurredAtMillis: Long,
        lat: Double?,
        lng: Double?,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun reportException(
        tripId: String,
        kind: ExceptionKind,
        severity: ExceptionSeverity,
        occurredAtMillis: Long,
        stopId: String?,
        note: String?,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun attachPhotoProof(
        stopId: String,
        bytes: ByteArray,
        contentType: String,
        capturedAtMillis: Long,
        lat: Double?,
        lng: Double?,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override fun pendingProofs(stopId: String): Flow<Int> = flowOf(0)

    override fun pendingCount(): Flow<Int> = flowOf(0)

    override suspend fun sync() {
        syncs++
    }
}

class DriverTripsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the first page without a cursor`() = runTest(dispatcher) {
        val repo = FakeTrips(listOf(AppResult.Success(Page(listOf(fakeTrip("a")), "cursor-1"))))
        val vm = DriverTripsViewModel(repo, CountingRecorder())
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.trips.map { it.id }).isEqualTo(listOf("a"))
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(repo.cursors).isEqualTo(listOf<String?>(null))
    }

    @Test
    fun `appends the next page and passes the cursor back untouched`() = runTest(dispatcher) {
        val repo = FakeTrips(
            listOf(
                AppResult.Success(Page(listOf(fakeTrip("a")), "cursor-1")),
                AppResult.Success(Page(listOf(fakeTrip("b")), null)),
            ),
        )
        val vm = DriverTripsViewModel(repo, CountingRecorder())
        testScheduler.advanceUntilIdle()

        vm.loadMore()
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.trips.map { it.id }).isEqualTo(listOf("a", "b"))
        assertThat(repo.cursors).isEqualTo(listOf(null, "cursor-1"))
        // A null cursor is the end of the list, so the button goes away.
        assertThat(vm.state.value.canLoadMore).isFalse()
    }

    @Test
    fun `does nothing when there is no next page`() = runTest(dispatcher) {
        val repo = FakeTrips(listOf(AppResult.Success(Page(listOf(fakeTrip("a")), null))))
        val vm = DriverTripsViewModel(repo, CountingRecorder())
        testScheduler.advanceUntilIdle()

        vm.loadMore()
        testScheduler.advanceUntilIdle()

        assertThat(repo.cursors).isEqualTo(listOf<String?>(null))
    }

    @Test
    fun `a failed first load leaves an empty list carrying the message`() = runTest(dispatcher) {
        val repo = FakeTrips(listOf(AppResult.Failure(AppError("no connection"))))
        val vm = DriverTripsViewModel(repo, CountingRecorder())
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.isEmpty).isTrue()
        assertThat(vm.state.value.errorMessage).isEqualTo("no connection")
    }

    @Test
    fun `a failed next page keeps the rows already on screen`() = runTest(dispatcher) {
        val repo = FakeTrips(
            listOf(
                AppResult.Success(Page(listOf(fakeTrip("a")), "cursor-1")),
                AppResult.Failure(AppError("timed out")),
            ),
        )
        val vm = DriverTripsViewModel(repo, CountingRecorder())
        testScheduler.advanceUntilIdle()

        vm.loadMore()
        testScheduler.advanceUntilIdle()

        // Losing a page must not lose the page before it.
        assertThat(vm.state.value.trips.map { it.id }).isEqualTo(listOf("a"))
        assertThat(vm.state.value.errorMessage).isEqualTo("timed out")
        // The cursor survives, so retrying asks for the same page again.
        assertThat(vm.state.value.nextCursor).isEqualTo("cursor-1")
    }

    @Test
    fun `refreshing after a failure clears the message`() = runTest(dispatcher) {
        val repo = FakeTrips(
            listOf(
                AppResult.Failure(AppError("no connection")),
                AppResult.Success(Page(listOf(fakeTrip("a")), null)),
            ),
        )
        val vm = DriverTripsViewModel(repo, CountingRecorder())
        testScheduler.advanceUntilIdle()

        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isNull()
        assertThat(vm.state.value.trips.map { it.id }).isEqualTo(listOf("a"))
    }
}
