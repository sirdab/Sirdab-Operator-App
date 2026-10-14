package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import co.sirdab.driver.shared.feature.trip.api.TripEventRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val analytics: Analytics,
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
                is AppResult.Success -> {
                    _state.value = DriverTripsUiState(
                        trips = result.data.items,
                        nextCursor = result.data.nextCursor,
                    )
                    analytics.track(
                        AnalyticsEvent.tripsLoaded(result.data.items, result.data.nextCursor != null, pulled),
                    )
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.tripsLoadFailed(pulled, result.error))
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.error.message,
                        errorReason = result.error.reason,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return

        _state.value = _state.value.copy(isLoadingMore = true, errorMessage = null, failedLoadMore = false)
        loadingMore = viewModelScope.launch {
            when (val result = repository.trips(cursor = cursor)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        // Appended, never merged by id: the cursor guarantees the next page does
                        // not overlap this one, so a de-duplication pass would only hide a server bug.
                        trips = _state.value.trips + result.data.items,
                        nextCursor = result.data.nextCursor,
                        isLoadingMore = false,
                    )
                    analytics.track(
                        AnalyticsEvent.moreTripsLoaded(
                            pageSize = result.data.items.size,
                            totalCount = _state.value.trips.size,
                            hasMore = result.data.nextCursor != null,
                        ),
                    )
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.moreTripsLoadFailed(result.error))
                    _state.value = _state.value.copy(
                        isLoadingMore = false,
                        errorMessage = result.error.message,
                        failedLoadMore = true,
                    )
                }
            }
        }
    }

    /** The driver tapped [trip]; the screen does the navigating. */
    fun tripOpened(trip: DriverTrip) {
        val position = _state.value.trips.indexOfFirst { it.id == trip.id }
        analytics.track(AnalyticsEvent.tripOpened(trip, position))
    }

    /** The footer's retry: the page that failed, or the refresh that did. */
    fun retry() {
        if (_state.value.failedLoadMore) loadMore() else refresh(pulled = true)
    }
}
