package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import co.sirdab.driver.shared.feature.trip.api.ExceptionKind
import co.sirdab.driver.shared.feature.trip.api.ExceptionSeverity
import co.sirdab.driver.shared.feature.trip.api.TripEventRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow as KStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class DriverTripsUiState(
    val trips: List<DriverTrip> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    /**
     * A refusal the driver cannot act on: the trip routes are carrier-scoped, and this driver works
     * independently. Rendered as an empty list rather than an error, because nothing is broken and
     * there is nothing to retry.
     */
    val errorReason: AppErrorReason? = null,
    val nextCursor: String? = null,
    /** Which call [errorMessage] came from, so retrying repeats that call and not the other. */
    val failedLoadMore: Boolean = false,
) {
    val canLoadMore: Boolean get() = nextCursor != null && !isLoadingMore
    /** An error with nothing on screen is a dead end; with rows behind it, it is a footer. */
    val isEmpty: Boolean get() = trips.isEmpty() && !isLoading
}

class DriverTripsViewModel(
    private val repository: DriverTripRepository,
    private val recorder: TripEventRecorder,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverTripsUiState())
    val state: StateFlow<DriverTripsUiState> = _state.asStateFlow()

    /** The page being appended, cancelled by a refresh that replaces the list under it. */
    private var loadingMore: Job? = null

    /** The latest refresh; an older one answering late must not overwrite it. */
    private var refreshing: Job? = null

    init {
        refresh()
    }

    /** [pulled] keeps the list on screen while it reloads, rather than a spinner. */
    fun refresh(pulled: Boolean = false) {
        // A page of the old list appended to the new one would repeat ids, and the list keys on
        // id, so that is a crash rather than a glitch.
        loadingMore?.cancel()
        refreshing?.cancel()
        _state.value = _state.value.copy(
            isLoading = !pulled && _state.value.trips.isEmpty(),
            isRefreshing = pulled,
            isLoadingMore = false,
            errorMessage = null,
            failedLoadMore = false,
        )
        refreshing = viewModelScope.launch {
            // A pull to refresh is the driver asking for the app to catch up,
            // and what is behind is as often their own unsent work as the list.
            recorder.sync()
            when (val result = repository.trips()) {
                is AppResult.Success -> _state.value = DriverTripsUiState(
                    trips = result.data.items,
                    nextCursor = result.data.nextCursor,
                )
                is AppResult.Failure -> _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.error.message,
                    errorReason = result.error.reason,
                )
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return

        _state.value = _state.value.copy(isLoadingMore = true, errorMessage = null, failedLoadMore = false)
        loadingMore = viewModelScope.launch {
            when (val result = repository.trips(cursor = cursor)) {
                is AppResult.Success -> _state.value = _state.value.copy(
                    // Appended, never merged by id: the cursor guarantees the next page does not
                    // overlap this one, so a de-duplication pass would only hide a server bug.
                    trips = _state.value.trips + result.data.items,
                    nextCursor = result.data.nextCursor,
                    isLoadingMore = false,
                )
                is AppResult.Failure -> _state.value = _state.value.copy(
                    isLoadingMore = false,
                    errorMessage = result.error.message,
                    failedLoadMore = true,
                )
            }
        }
    }

    /** The footer's retry: the page that failed, or the refresh that did. */
    fun retry() {
        if (_state.value.failedLoadMore) loadMore() else refresh(pulled = true)
    }
}

data class DriverTripDetailUiState(
    val trip: DriverTrip? = null,
    val isLoading: Boolean = true,
    /** A reload the driver asked for, which keeps the trip on screen while it runs. */
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /** Actions recorded on this device that the server has not acknowledged yet. */
    val pendingWrites: Int = 0,
    /** Actions the server refused for good, which the driver has not dismissed yet. */
    val droppedWrites: Int = 0,
    val isReportingException: Boolean = false,
    val exceptionReported: Boolean = false,
)

class DriverTripDetailViewModel(
    private val tripId: String,
    private val repository: DriverTripRepository,
    private val recorder: TripEventRecorder,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverTripDetailUiState())
    val state: StateFlow<DriverTripDetailUiState> = _state.asStateFlow()

    val pendingWrites: KStateFlow<Int> = recorder.pendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        load()
        viewModelScope.launch {
            // Null until the first count, which may be refusals from before this
            // screen opened; the load above already reads the trip fresh for those.
            var seen: Int? = null
            recorder.droppedCount().collect { count ->
                _state.value = _state.value.copy(droppedWrites = count)
                // A refusal means the screen has been showing a step the server
                // never took. Read the truth back rather than keep pretending.
                if (seen != null && count > seen!!) load(pulled = true)
                seen = count
            }
        }
    }

    fun acknowledgeDropped() {
        recorder.acknowledgeDropped()
    }

    /**
     * Photos for this stop that have not reached the server yet.
     *
     * Kept apart from the stop's own `photoProofCount`, which is the server's
     * count. The screen adds the two to unlock Delivered (see [actionsFor]):
     * the queue sends the photo before the delivery tapped after it.
     */
    fun pendingProofs(stopId: String): KStateFlow<Int> =
        proofFlows.getOrPut(stopId) {
            recorder.pendingProofs(stopId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
        }

    private val proofFlows = mutableMapOf<String, KStateFlow<Int>>()

    /**
     * Keep the photo, then reload.
     *
     * The reload is what turns a queued photo into an unlocked button: it is
     * the server's count coming back, which is the only evidence that the
     * proof is really there.
     */
    fun attachPhoto(stopId: String, bytes: ByteArray, contentType: String) {
        val capturedAtMillis = clock.now().toEpochMilliseconds()
        viewModelScope.launch {
            val result = recorder.attachPhotoProof(
                stopId = stopId,
                bytes = bytes,
                contentType = contentType,
                capturedAtMillis = capturedAtMillis,
            )
            when (result) {
                is AppResult.Success -> load()
                is AppResult.Failure ->
                    _state.value = _state.value.copy(errorMessage = result.error.message)
            }
        }
    }

    /**
     * Read the trip again.
     *
     * [pulled] is the driver asking rather than the screen opening: the stops stay where they are
     * and the gesture carries its own indicator, so blanking the trip to a spinner would take away
     * the very thing they pulled to compare against.
     */
    fun load(pulled: Boolean = false) {
        _state.value = _state.value.copy(
            isLoading = !pulled,
            isRefreshing = pulled,
            errorMessage = null,
        )
        viewModelScope.launch {
            // Push before pulling. A proof still sitting in the queue is the
            // reason the stop reads as unproven, so sending it first is what
            // makes the reload that follows show the action unlocked.
            recorder.sync()
            _state.value = when (val result = repository.trip(tripId)) {
                // Whatever the sync could not send is laid back on top: the server's
                // copy predates it, and showing that copy would offer the driver a
                // step they already took, whose second tap the server then refuses.
                is AppResult.Success -> DriverTripDetailUiState(
                    trip = result.data.withQueued(recorder.queuedEvents(tripId)),
                    isLoading = false,
                    droppedWrites = _state.value.droppedWrites,
                )
                is AppResult.Failure -> _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }

    /**
     * Record what the driver just did.
     *
     * The timestamp is taken here, at the tap, and travels with the queued write
     * as `occurredAt`. It is never refreshed on a retry: a driver who arrives in
     * a dead zone and syncs an hour later arrived when they arrived.
     *
     * The screen advances immediately on the strength of the local write. That is
     * not optimism about the network, it is the point of the queue: the write is
     * already durable, and offline there is no confirmation coming to wait for.
     */
    fun openExceptionReport() {
        _state.value = _state.value.copy(isReportingException = true, exceptionReported = false)
    }

    fun dismissExceptionReport() {
        _state.value = _state.value.copy(isReportingException = false)
    }

    fun acknowledgeExceptionReported() {
        _state.value = _state.value.copy(exceptionReported = false)
    }

    /**
     * Report a problem against this trip, and against the stop being worked if
     * there is one: a refused delivery belongs to a stop, a breakdown does not.
     */
    fun reportException(kind: ExceptionKind, severity: ExceptionSeverity, note: String) {
        val trip = _state.value.trip ?: return
        val occurredAtMillis = clock.now().toEpochMilliseconds()
        _state.value = _state.value.copy(isReportingException = false)

        viewModelScope.launch {
            val result = recorder.reportException(
                tripId = trip.id,
                kind = kind,
                severity = severity,
                occurredAtMillis = occurredAtMillis,
                stopId = trip.currentStop?.id,
                note = note,
            )
            _state.value = when (result) {
                is AppResult.Success -> _state.value.copy(exceptionReported = true)
                is AppResult.Failure -> _state.value.copy(errorMessage = result.error.message)
            }
        }
    }

    /**
     * Start the trip.
     *
     * The server refuses every piece of stop work until this lands, so it is the
     * one thing on offer while the trip is still merely assigned.
     */
    fun startTrip() {
        val trip = _state.value.trip ?: return
        val action = tripActionFor(trip) ?: return
        val occurredAtMillis = clock.now().toEpochMilliseconds()

        _state.value = _state.value.copy(trip = trip.applyLocally(action), errorMessage = null)

        viewModelScope.launch {
            val result = recorder.record(
                tripId = trip.id,
                eventType = action.event,
                occurredAtMillis = occurredAtMillis,
            )
            if (result is AppResult.Failure) {
                _state.value = _state.value.copy(trip = trip, errorMessage = result.error.message)
            }
        }
    }

    fun record(stopId: String, action: StopAction) {
        val trip = _state.value.trip ?: return
        val stop = trip.stops.firstOrNull { it.id == stopId } ?: return
        // Checked against the state as it is now, not as the button was drawn: a double tap lands
        // twice before the screen recomposes, and the second, out-of-order event would be queued,
        // refused hours later, and dropped. The photo rule is the screen's to enforce.
        if (action !in actionsFor(trip, stop, requirePhoto = false)) return
        val occurredAtMillis = clock.now().toEpochMilliseconds()

        _state.value = _state.value.copy(
            trip = trip.applyLocally(stopId, action, occurredAtMillis),
            errorMessage = null,
        )

        viewModelScope.launch {
            val result = recorder.record(
                tripId = tripId,
                eventType = action.event,
                stopId = stopId,
                occurredAtMillis = occurredAtMillis,
            )
            // Only a failure to persist reaches here, and it is the one the driver
            // must see: nothing was saved, so the screen must not keep pretending.
            if (result is AppResult.Failure) {
                _state.value = _state.value.copy(
                    trip = trip,
                    errorMessage = result.error.message,
                )
            }
        }
    }
}
