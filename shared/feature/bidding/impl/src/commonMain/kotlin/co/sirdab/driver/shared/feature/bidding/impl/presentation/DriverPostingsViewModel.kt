package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.feature.bidding.api.DriverBiddingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class DriverPostingsUiState(
    val postings: List<DriverPosting> = emptyList(),
    val trucks: List<DriverTruck> = emptyList(),
    val myBids: List<DriverBid> = emptyList(),
    val isLoading: Boolean = false,
    /** A pull-to-refresh, which keeps the list on screen while it reloads. */
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    /** Named failures the screen renders as a state of its own rather than as red text. */
    val errorReason: AppErrorReason? = null,
    val nextCursor: String? = null,
    /** The posting whose offer sheet is open. */
    val bidding: DriverPosting? = null,
    val bidSent: Boolean = false,
) {
    val canLoadMore: Boolean get() = nextCursor != null && !isLoadingMore
    val isEmpty: Boolean get() = postings.isEmpty() && !isLoading

    /**
     * The board is not built yet, server-side.
     *
     * Bidding is phase 4 and answers 501 until it ships. That is a promise, not a fault, and a
     * driver should not be offered a retry button for it.
     */
    val isComingSoon: Boolean get() = errorReason == AppErrorReason.UNAVAILABLE

    /**
     * The board being out of this driver's reach, which is not a fault to report.
     *
     * Every posting route is scoped to a carrier, so a driver working independently is refused all
     * of them. They are signed in and their paperwork is approved; there is simply nobody posting
     * loads to them. An empty board says that; a red error with a retry button says the app is
     * broken.
     */
    val isBoardOutOfScope: Boolean get() = errorReason == AppErrorReason.NOT_PROVISIONED

    /** A carrier may bid once per posting, so an existing bid replaces the action. */
    fun bidFor(postingId: String): DriverBid? =
        myBids.firstOrNull { it.loadPostingId == postingId }
}

class DriverPostingsViewModel(
    private val repository: DriverBiddingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverPostingsUiState())
    val state: StateFlow<DriverPostingsUiState> = _state.asStateFlow()

    /** The page being appended, cancelled by a refresh that replaces the list under it. */
    private var loadingMore: Job? = null

    /**
     * The offer being made, and the key it goes out under.
     *
     * Kept across retries of the same offer so a bid that landed without its answer replays rather
     * than coming back as a duplicate. Changing the amount, truck or note is a different offer and
     * gets a new key: the server refuses a key reused for a different body.
     */
    private var offer: Pair<Offer, String>? = null

    private data class Offer(val postingId: String, val amountSar: Int, val truckId: String, val note: String)

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
        // A page from the old list appended to the new one would repeat rows, and the list keys
        // on id, so that is a crash rather than a glitch.
        loadingMore?.cancel()
        _state.value = _state.value.copy(
            isLoadingMore = false,
            isLoading = !pulled && _state.value.postings.isEmpty(),
            isRefreshing = pulled,
            errorMessage = null,
            errorReason = null,
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
                    errorReason = result.error.reason,
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
        // A double tap would fetch the same page twice and append it twice.
        if (_state.value.isLoadingMore) return

        _state.value = _state.value.copy(isLoadingMore = true)
        loadingMore = viewModelScope.launch {
            when (val result = repository.postings(cursor = cursor)) {
                is AppResult.Success -> _state.value = _state.value.copy(
                    postings = _state.value.postings + result.data.items,
                    nextCursor = result.data.nextCursor,
                    isLoadingMore = false,
                )
                is AppResult.Failure -> _state.value =
                    _state.value.copy(errorMessage = result.error.message, isLoadingMore = false)
            }
        }
    }

    fun openBid(posting: DriverPosting) {
        _state.value = _state.value.copy(bidding = posting, bidSent = false, errorMessage = null)
    }

    fun dismissBid() {
        offer = null
        _state.value = _state.value.copy(bidding = null)
    }

    /**
     * Place the offer.
     *
     * Sent straight out rather than queued: a bid is only worth anything before
     * the posting closes, so one that syncs tomorrow is an offer on something
     * already awarded. The driver is told it failed instead.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun submitBid(postingId: String, amountSar: Int, truckId: String, note: String) {
        if (_state.value.isSubmitting) return
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)

        val draft = Offer(postingId, amountSar, truckId, note)
        val key = offer?.takeIf { it.first == draft }?.second ?: Uuid.random().toString()
        offer = draft to key

        viewModelScope.launch {
            val result = repository.placeBid(
                postingId = postingId,
                // The contract carries minor units; the driver typed riyals.
                amountCents = amountSar * 100,
                truckId = truckId,
                note = note,
                idempotencyKey = key,
            )
            when (result) {
                is AppResult.Success -> {
                    offer = null
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        bidding = null,
                        bidSent = true,
                        myBids = _state.value.myBids + result.data,
                    )
                }
                is AppResult.Failure -> {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        errorMessage = result.error.message,
                    )
                    // The failure may be a bid that did land (a lost answer, or "already bid"),
                    // and the board keys its action on myBids. Re-read it so the screen stops
                    // offering an offer that exists.
                    (repository.myBids() as? AppResult.Success)?.let {
                        _state.value = _state.value.copy(myBids = it.data.items)
                    }
                }
            }
        }
    }
}
