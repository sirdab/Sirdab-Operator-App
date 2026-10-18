package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.BidRange
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverBidStatus
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.Money
import co.sirdab.driver.shared.feature.bidding.api.BidOutOfRange
import co.sirdab.driver.shared.feature.bidding.api.DriverBiddingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
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
    /**
     * The driver's own pending bid the open sheet reprices, or null when it places a new one.
     *
     * Taken when the sheet opens rather than read from [myBids] as it changes: a refusal re-reads
     * the bids, and a sheet that turned into "place a bid" under the driver's thumb would offer to
     * bid again on a posting they already hold a bid on.
     */
    val editing: DriverBid? = null,
    /** Why the sheet's last offer was refused, in words the screen can translate; else [errorMessage]. */
    val bidErrorReason: AppErrorReason? = null,
    /** What the last offer that went out did, said above the list until the next sheet opens. */
    val notice: BidNotice? = null,
    /**
     * The next page failed. Kept apart from [errorMessage], which the offer sheet also uses: a bid
     * refused in the sheet must not turn into a "load more" retry under the list.
     */
    val failedLoadMore: Boolean = false,
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
    val isBoardOutOfScope: Boolean get() = errorReason == AppErrorReason.NOT_PROVISIONED || isCompanyDriver

    /**
     * A company carrier's driver, whom the server refuses the board because the company bids.
     *
     * The shell hides the tab once `/me` says so; this covers the moment before it has.
     */
    val isCompanyDriver: Boolean get() = errorReason == AppErrorReason.NOT_A_BIDDER

    /**
     * A carrier may bid once per posting, so an existing bid replaces the action.
     *
     * A withdrawn bid is no longer an offer, and bidding again revives it, so the board offers
     * the action again rather than showing it.
     */
    fun bidFor(postingId: String): DriverBid? =
        myBids.firstOrNull { it.loadPostingId == postingId && it.status != DriverBidStatus.WITHDRAWN }

    /** The server's copy of [bid], in place of the one held: a revived or repriced bid keeps its id. */
    internal fun withBid(bid: DriverBid): DriverPostingsUiState =
        copy(myBids = myBids.filterNot { it.id == bid.id } + bid)

    internal fun withBidRange(postingId: String, range: BidRange?): DriverPostingsUiState = copy(
        bidding = bidding?.let { if (it.id == postingId) it.copy(bidRange = range) else it },
        postings = postings.map { if (it.id == postingId) it.copy(bidRange = range) else it },
    )
}

/**
 * What the board says above the list after the sheet closes: an offer that went out, or one that
 * could not, because the bid was already decided ([DECIDED]) or the posting left the board ([CLOSED]).
 */
enum class BidNotice { SENT, UPDATED, DECIDED, CLOSED }

/**
 * "Go lower or match?": another carrier's pending bid is below this one.
 *
 * [matchCents] is that bid to the halala, and [goLowerSar] the most whole riyals strictly under it.
 * Either is null when the posting's range would refuse it, so the sheet only offers what can land.
 */
internal data class Undercut(val lowest: Money, val matchCents: Int?, val goLowerSar: Int?)

/** Null unless this bid is pending and someone else is asking less. */
internal fun DriverBid.undercut(range: BidRange?): Undercut? {
    val lowest = lowestCompetingBid
        ?.takeIf { status == DriverBidStatus.PENDING && it.amountCents < amount.amountCents }
        ?: return null
    val match = lowest.amountCents.takeIf { range == null || it.toLong() in range }
    val below = ((lowest.amountCents - 1) / 100).takeIf { it > 0 && (range == null || it * 100L in range) }
    return Undercut(lowest, match, below)
}

class DriverPostingsViewModel(
    private val repository: DriverBiddingRepository,
    private val analytics: Analytics,
    private val clock: Clock = Clock.System,
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

    private sealed interface Offer {
        data class New(val postingId: String, val amountSar: Int, val truckId: String, val note: String) : Offer
        data class Reprice(val bidId: String, val amountCents: Int) : Offer
    }

    /** The key [draft] goes out under: the held one when it is a retry of the same offer. */
    @OptIn(ExperimentalUuidApi::class)
    private fun keyFor(draft: Offer): String {
        val key = offer?.takeIf { it.first == draft }?.second ?: Uuid.random().toString()
        offer = draft to key
        return key
    }

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
            failedLoadMore = false,
            isLoading = !pulled && _state.value.postings.isEmpty(),
            isRefreshing = pulled,
            errorMessage = null,
            errorReason = null,
        )
        viewModelScope.launch {
            val result = repository.postings()
            when (result) {
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
            // After both, so the event can say whether this driver could bid on what they saw.
            analytics.track(
                when (result) {
                    is AppResult.Success -> AnalyticsEvent.boardLoaded(
                        postings = result.data.items,
                        hasMore = result.data.nextCursor != null,
                        myBidCount = _state.value.myBids.size,
                        truckCount = _state.value.trucks.size,
                        pulled = pulled,
                    )
                    is AppResult.Failure -> AnalyticsEvent.boardLoadFailed(pulled, result.error)
                },
            )
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        // A double tap would fetch the same page twice and append it twice.
        if (_state.value.isLoadingMore) return

        _state.value = _state.value.copy(isLoadingMore = true, failedLoadMore = false)
        loadingMore = viewModelScope.launch {
            when (val result = repository.postings(cursor = cursor)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(
                        postings = _state.value.postings + result.data.items,
                        nextCursor = result.data.nextCursor,
                        isLoadingMore = false,
                    )
                    analytics.track(
                        AnalyticsEvent.morePostingsLoaded(
                            pageSize = result.data.items.size,
                            totalCount = _state.value.postings.size,
                            hasMore = result.data.nextCursor != null,
                        ),
                    )
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.morePostingsLoadFailed(result.error))
                    _state.value = _state.value.copy(
                        errorMessage = result.error.message,
                        isLoadingMore = false,
                        failedLoadMore = true,
                    )
                }
            }
        }
    }

    /**
     * Opens the sheet: on the driver's own pending bid to reprice it ([DriverPostingsUiState.editing]),
     * or for a new offer. A fixed rate has no price to change, so its bid is never edited.
     */
    fun openBid(posting: DriverPosting) {
        val editing = _state.value.bidFor(posting.id)
            ?.takeIf { it.status == DriverBidStatus.PENDING && !posting.isFixedRate }
        _state.value = _state.value.copy(
            bidding = posting,
            editing = editing,
            notice = null,
            errorMessage = null,
            bidErrorReason = null,
        )
        analytics.track(
            AnalyticsEvent.bidSheetOpened(
                posting = posting,
                truckCount = _state.value.trucks.size,
                nowMillis = clock.now().toEpochMilliseconds(),
                editing = editing != null,
            ),
        )
    }

    /** The sheet closing without an offer; one that went out closes it by itself. */
    fun dismissBid() {
        _state.value.bidding?.let { posting ->
            // An offer is still held only after a refusal: one that landed clears it and closes
            // the sheet by itself.
            val refused = offer != null && !_state.value.isSubmitting
            analytics.track(AnalyticsEvent.bidSheetDismissed(posting.id, afterFailure = refused))
        }
        offer = null
        _state.value = _state.value.copy(bidding = null, editing = null)
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
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null, bidErrorReason = null)

        val key = keyFor(Offer.New(postingId, amountSar, truckId, note))
        // Read now, before the answer: by then a refresh may have replaced the board.
        val posting = _state.value.bidding?.takeIf { it.id == postingId }
            ?: _state.value.postings.firstOrNull { it.id == postingId }
        val nowMillis = clock.now().toEpochMilliseconds()

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
                    analytics.track(
                        AnalyticsEvent.bidPlaced(
                            postingId = postingId,
                            bidId = result.data.id,
                            amountSar = amountSar,
                            hasNote = note.isNotBlank(),
                            posting = posting,
                            nowMillis = nowMillis,
                        ),
                    )
                    offer = null
                    // A bid on a posting withdrawn from before revives the same row, so it
                    // replaces that one rather than joining it.
                    _state.value = _state.value.withBid(result.data).copy(
                        isSubmitting = false,
                        bidding = null,
                        editing = null,
                        notice = BidNotice.SENT,
                    )
                }
                is AppResult.Failure -> {
                    analytics.track(
                        AnalyticsEvent.bidFailed(postingId, amountSar, result.error, posting, nowMillis),
                    )
                    // The failure may be a bid that did land (a lost answer, or "already bid"),
                    // and the board keys its action on myBids. Re-read it so the screen stops
                    // offering an offer that exists.
                    refused(postingId, result.error)
                }
            }
        }
    }

    /**
     * Reprice the driver's pending bid.
     *
     * In halalas rather than riyals, so matching a competing bid sends exactly its amount. Sent
     * straight out, like a new bid. The answer carries the lowest competing bid as it is after the
     * change, so the board knows at once whether the driver is still undercut.
     */
    fun updateBid(bidId: String, amountCents: Int) {
        if (_state.value.isSubmitting) return
        val current = _state.value.myBids.firstOrNull { it.id == bidId } ?: return
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null, bidErrorReason = null)

        val amountSar = amountCents / 100
        val key = keyFor(Offer.Reprice(bidId, amountCents))
        val postingId = current.loadPostingId
        val posting = _state.value.bidding?.takeIf { it.id == postingId }
            ?: _state.value.postings.firstOrNull { it.id == postingId }
        val nowMillis = clock.now().toEpochMilliseconds()

        viewModelScope.launch {
            when (val result = repository.updateBid(bidId, amountCents, key)) {
                is AppResult.Success -> {
                    analytics.track(
                        AnalyticsEvent.bidUpdated(
                            postingId = postingId,
                            bidId = bidId,
                            amountSar = amountSar,
                            previousAmountSar = current.amount.amountSar,
                            matched = current.lowestCompetingBid?.amountCents == amountCents,
                            lowestCompetingSar = result.data.lowestCompetingBid?.amountSar,
                            posting = posting,
                            nowMillis = nowMillis,
                        ),
                    )
                    offer = null
                    _state.value = _state.value.withBid(result.data).copy(
                        isSubmitting = false,
                        bidding = null,
                        editing = null,
                        notice = BidNotice.UPDATED,
                    )
                }
                is AppResult.Failure -> {
                    analytics.track(
                        AnalyticsEvent.bidUpdateFailed(postingId, bidId, amountSar, result.error, posting, nowMillis),
                    )
                    // Most refusals here mean the bid is no longer pending (won, lost, or the
                    // posting closed). Re-read it so the card stops offering an edit.
                    refused(postingId, result.error)
                }
            }
        }
    }

    private suspend fun refused(postingId: String, error: AppError) {
        val gone = when (error.reason) {
            AppErrorReason.BID_DECIDED -> BidNotice.DECIDED
            AppErrorReason.BIDDING_CLOSED -> BidNotice.CLOSED
            else -> null
        }
        if (gone != null) {
            // Nothing the sheet could send would change this. It closes, the board says why, and
            // reloads so the card shows how the bid ended, or leaves with its posting.
            offer = null
            _state.value = _state.value.copy(isSubmitting = false, bidding = null, editing = null, notice = gone)
            refresh()
            return
        }

        val outOfRange = error.cause as? BidOutOfRange
        _state.value = if (outOfRange != null) {
            // The dispatcher moved the range after the board loaded. With the range as it is
            // now, the sheet's own check says what is wrong, in the driver's language and with
            // the real numbers, so the server's sentence is not shown.
            _state.value.withBidRange(postingId, outOfRange.range).copy(isSubmitting = false)
        } else {
            _state.value.copy(isSubmitting = false, errorMessage = error.message, bidErrorReason = error.reason)
        }
        (repository.myBids() as? AppResult.Success)?.let { page ->
            _state.value = _state.value.copy(
                myBids = page.data.items,
                // The bid being edited as it is now, its lowest competing bid included.
                editing = _state.value.editing?.let { held -> page.data.items.firstOrNull { it.id == held.id } ?: held },
            )
        }
    }
}
