package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.feature.bidding.api.DriverBiddingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriverPostingsUiState(
    val postings: List<DriverPosting> = emptyList(),
    val trucks: List<DriverTruck> = emptyList(),
    val myBids: List<DriverBid> = emptyList(),
    val isLoading: Boolean = false,
    /** A pull-to-refresh, which keeps the list on screen while it reloads. */
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val nextCursor: String? = null,
    /** The posting whose offer sheet is open. */
    val bidding: DriverPosting? = null,
    val bidSent: Boolean = false,
) {
    val canLoadMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = postings.isEmpty() && !isLoading

    /** A carrier may bid once per posting, so an existing bid replaces the action. */
    fun bidFor(postingId: String): DriverBid? =
        myBids.firstOrNull { it.loadPostingId == postingId }
}

class DriverPostingsViewModel(
    private val repository: DriverBiddingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverPostingsUiState())
    val state: StateFlow<DriverPostingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Reload the board.
     *
     * [pulled] distinguishes a driver's own pull from the first load: a spinner
     * replacing the list is right when there is nothing to show yet, and wrong
     * when they are checking whether anything new arrived.
     */
    fun refresh(pulled: Boolean = false) {
        _state.value = _state.value.copy(
            isLoading = !pulled && _state.value.postings.isEmpty(),
            isRefreshing = pulled,
            errorMessage = null,
        )
        viewModelScope.launch {
            when (val result = repository.postings()) {
                is AppResult.Success -> _state.value = _state.value.copy(
                    postings = result.data.items,
                    nextCursor = result.data.nextCursor,
                    isLoading = false,
                    isRefreshing = false,
                )
                is AppResult.Failure -> _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.error.message,
                )
            }
            // Both are needed before a bid can be placed, and neither is worth
            // failing the board over.
            (repository.myBids() as? AppResult.Success)?.let {
                _state.value = _state.value.copy(myBids = it.data.items)
            }
            (repository.trucks() as? AppResult.Success)?.let {
                _state.value = _state.value.copy(trucks = it.data)
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        viewModelScope.launch {
            when (val result = repository.postings(cursor = cursor)) {
                is AppResult.Success -> _state.value = _state.value.copy(
                    postings = _state.value.postings + result.data.items,
                    nextCursor = result.data.nextCursor,
                )
                is AppResult.Failure -> _state.value =
                    _state.value.copy(errorMessage = result.error.message)
            }
        }
    }

    fun openBid(posting: DriverPosting) {
        _state.value = _state.value.copy(bidding = posting, bidSent = false, errorMessage = null)
    }

    fun dismissBid() {
        _state.value = _state.value.copy(bidding = null)
    }

    /**
     * Place the offer.
     *
     * Sent straight out rather than queued: a bid is only worth anything before
     * the posting closes, so one that syncs tomorrow is an offer on something
     * already awarded. The driver is told it failed instead.
     */
    fun submitBid(postingId: String, amountSar: Int, truckId: String, note: String) {
        if (_state.value.isSubmitting) return
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {
            val result = repository.placeBid(
                postingId = postingId,
                // The contract carries minor units; the driver typed riyals.
                amountCents = amountSar * 100,
                truckId = truckId,
                note = note,
            )
            _state.value = when (result) {
                is AppResult.Success -> _state.value.copy(
                    isSubmitting = false,
                    bidding = null,
                    bidSent = true,
                    myBids = _state.value.myBids + result.data,
                )
                is AppResult.Failure -> _state.value.copy(
                    isSubmitting = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }
}
