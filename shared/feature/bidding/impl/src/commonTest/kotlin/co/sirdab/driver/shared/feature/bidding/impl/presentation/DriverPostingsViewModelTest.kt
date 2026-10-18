package co.sirdab.driver.shared.feature.bidding.impl.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.testing.RecordingAnalytics
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.BidRange
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverBidStatus
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.Money
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.model.PostingPlace
import co.sirdab.driver.shared.core.model.PostingStatus
import co.sirdab.driver.shared.feature.bidding.api.BidOutOfRange
import co.sirdab.driver.shared.feature.bidding.api.DriverBiddingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

private fun posting(id: String) = DriverPosting(
    id = id,
    loadId = "load-$id",
    status = PostingStatus.entries.first(),
    origin = PostingPlace("Riyadh"),
    destination = PostingPlace("Jeddah"),
    bodyType = EquipmentBody.BOX,
    sizeClass = EquipmentSize.PICKUP,
    temperature = EquipmentTemperature.AMBIENT,
)

private class FakeBidding : DriverBiddingRepository {
    val postingCursors = mutableListOf<String?>()
    val bidKeys = mutableListOf<String>()
    val edits = mutableListOf<Triple<String, Int, String>>()
    var myBidsReads = 0
    var bids: List<DriverBid> = emptyList()
    var updateResult: AppResult<DriverBid> = AppResult.Failure(AppError("timeout"))

    /** Holds the second page open, so a test can tap again while it is in flight. */
    val secondPage = CompletableDeferred<AppResult<Page<DriverPosting>>>()
    var bidResult: AppResult<DriverBid> = AppResult.Failure(AppError("timeout"))
    var firstPage: AppResult<Page<DriverPosting>> = AppResult.Success(Page(listOf(posting("a")), "c1"))

    override suspend fun postings(cursor: String?, limit: Int): AppResult<Page<DriverPosting>> {
        postingCursors += cursor
        return if (cursor == null) firstPage else secondPage.await()
    }

    var trucks: List<DriverTruck> = emptyList()

    override suspend fun trucks(): AppResult<List<DriverTruck>> = AppResult.Success(trucks)

    override suspend fun placeBid(
        postingId: String,
        amountCents: Int,
        truckId: String,
        note: String?,
        idempotencyKey: String,
    ): AppResult<DriverBid> {
        bidKeys += idempotencyKey
        return bidResult
    }

    override suspend fun updateBid(bidId: String, amountCents: Int, idempotencyKey: String): AppResult<DriverBid> {
        edits += Triple(bidId, amountCents, idempotencyKey)
        return updateResult
    }

    override suspend fun myBids(cursor: String?, limit: Int): AppResult<Page<DriverBid>> {
        myBidsReads++
        return AppResult.Success(Page(bids, null))
    }
}

private class FixedClock(private val millis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

private val TRUCK = DriverTruck("t1", "ABC 123", EquipmentBody.BOX, EquipmentSize.PICKUP, EquipmentTemperature.AMBIENT)

class DriverPostingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalytics()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a double tap on load more fetches the page once`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.loadMore()
        vm.loadMore()
        testScheduler.advanceUntilIdle()
        repo.secondPage.complete(AppResult.Success(Page(listOf(posting("b")), null)))
        testScheduler.advanceUntilIdle()

        // Twice would append the same rows twice, and the list keys on id.
        assertThat(repo.postingCursors).isEqualTo(listOf(null, "c1"))
        assertThat(vm.state.value.postings.map { it.id }).isEqualTo(listOf("a", "b"))
    }

    @Test
    fun `a company driver's refusal is a state of its own rather than a fault`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            firstPage = AppResult.Failure(
                AppError("only independent operators bid from the app", reason = AppErrorReason.NOT_A_BIDDER),
            )
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        // No red text and no retry button: retrying would be refused the same way for ever.
        assertThat(vm.state.value.isCompanyDriver).isEqualTo(true)
        assertThat(vm.state.value.isBoardOutOfScope).isEqualTo(true)
    }

    @Test
    fun `retrying the same offer reuses its key`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.submitBid("p1", 1700, "t1", "")
        testScheduler.advanceUntilIdle()
        vm.submitBid("p1", 1700, "t1", "")
        testScheduler.advanceUntilIdle()

        // The first may have landed with its answer lost; the retry must replay it.
        assertThat(repo.bidKeys[0]).isEqualTo(repo.bidKeys[1])
    }

    @Test
    fun `changing the offer gets a new key`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.submitBid("p1", 1700, "t1", "")
        testScheduler.advanceUntilIdle()
        vm.submitBid("p1", 1500, "t1", "")
        testScheduler.advanceUntilIdle()

        // The server refuses a key reused for a different body.
        assertThat(repo.bidKeys[0]).isNotEqualTo(repo.bidKeys[1])
    }

    @Test
    fun `a bid refused for its price shows the range as the server has it now`() = runTest(dispatcher) {
        val moved = BidRange(minCents = 150_000, maxCents = 250_000)
        val repo = FakeBidding().apply {
            firstPage = AppResult.Success(Page(listOf(posting("a").copy(bidRange = BidRange(100_000, 200_000))), null))
            bidResult = AppResult.Failure(
                AppError(
                    "this bid is outside the posting's range",
                    cause = BidOutOfRange(moved),
                    reason = AppErrorReason.BID_OUT_OF_RANGE,
                ),
            )
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()
        vm.openBid(vm.state.value.postings.single())

        vm.submitBid("a", 1200, "t1", "")
        testScheduler.advanceUntilIdle()

        // The sheet checks the amount against this range and says what is wrong in the driver's
        // language, so the server's English sentence is not shown on top of it.
        assertThat(vm.state.value.bidding?.bidRange).isEqualTo(moved)
        assertThat(vm.state.value.postings.single().bidRange).isEqualTo(moved)
        assertThat(vm.state.value.errorMessage).isNull()
        assertThat(vm.state.value.isSubmitting).isEqualTo(false)
    }

    @Test
    fun `a failed bid re-reads the driver's bids`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()
        val before = repo.myBidsReads

        vm.submitBid("p1", 1700, "t1", "")
        testScheduler.advanceUntilIdle()

        assertThat(repo.myBidsReads).isEqualTo(before + 1)
    }

    @Test
    fun `a placed bid is tracked with the offer the driver made`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            bidResult = AppResult.Success(
                DriverBid("b1", "p1", "t1", Money(170_000), DriverBidStatus.PENDING),
            )
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.submitBid("p1", 1700, "t1", "Can load tonight")
        testScheduler.advanceUntilIdle()

        // "p1" is not on the board, so the offer goes out with its ids and nothing else.
        assertThat(analytics.events.last()).isEqualTo(
            AnalyticsEvent.bidPlaced(
                postingId = "p1",
                bidId = "b1",
                amountSar = 1700,
                hasNote = true,
                posting = null,
                nowMillis = 0,
            ),
        )
    }

    @Test
    fun `a refused bid is tracked as a failure`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.submitBid("p1", 1700, "t1", "")
        testScheduler.advanceUntilIdle()

        assertThat(analytics.events.last()).isEqualTo(
            AnalyticsEvent.bidFailed("p1", 1700, AppError("timeout"), posting = null, nowMillis = 0),
        )
    }

    @Test
    fun `the board load is tracked once the driver's bids and trucks are in`() = runTest(dispatcher) {
        val repo = FakeBidding().apply { trucks = listOf(TRUCK) }
        DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        assertThat(analytics.events).isEqualTo(
            listOf(
                AnalyticsEvent.boardLoaded(
                    postings = listOf(posting("a")),
                    hasMore = true,
                    myBidCount = 0,
                    truckCount = 1,
                    pulled = false,
                ),
            ),
        )
    }

    @Test
    fun `a board the driver is refused is tracked with why`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            firstPage = AppResult.Failure(AppError("not built", reason = AppErrorReason.UNAVAILABLE))
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.refresh(pulled = true)
        testScheduler.advanceUntilIdle()

        assertThat(analytics.eventNames).isEqualTo(listOf("Board Load Failed", "Board Load Failed"))
        assertThat(analytics.events.last().properties["error_reason"]).isEqualTo("unavailable")
        assertThat(analytics.events.last().properties["pulled"]).isEqualTo(true)
    }

    @Test
    fun `opening the sheet says what the posting is and how long it has left`() = runTest(dispatcher) {
        val repo = FakeBidding().apply { trucks = listOf(TRUCK) }
        val vm = DriverPostingsViewModel(repo, analytics, FixedClock(1_000_000L))
        testScheduler.advanceUntilIdle()

        vm.openBid(
            posting("p1").copy(
                origin = PostingPlace("Depot 4", city = "Riyadh"),
                targetRate = Money(150_000),
                biddingClosesAtMillis = 1_000_000L + 45 * 60_000L,
            ),
        )

        assertThat(analytics.events.last().properties).isEqualTo(
            mapOf(
                "posting_id" to "p1",
                "pricing" to "fixed",
                "target_rate_sar" to 1500,
                "minutes_to_close" to 45,
                "origin_city" to "Riyadh",
                "body_type" to EquipmentBody.BOX.wire,
                "size_class" to EquipmentSize.PICKUP.wire,
                "temperature" to EquipmentTemperature.AMBIENT.wire,
                "truck_count" to 1,
                "editing" to false,
            ),
        )
    }

    @Test
    fun `closing the sheet after a refusal is told apart from changing their mind`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.openBid(posting("a"))
        vm.dismissBid()
        vm.openBid(posting("a"))
        vm.submitBid("a", 1700, "t1", "")
        testScheduler.advanceUntilIdle()
        vm.dismissBid()

        val dismissals = analytics.events.filter { it.name == "Bid Sheet Dismissed" }
        assertThat(dismissals.map { it.properties["after_failure"] }).isEqualTo(listOf(false, true))
    }

    @Test
    fun `a bid on a posting the board holds carries the posting's lane`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            bidResult = AppResult.Success(DriverBid("b1", "a", "t1", Money(170_000), DriverBidStatus.PENDING))
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.submitBid("a", 1700, "t1", "")
        testScheduler.advanceUntilIdle()

        assertThat(analytics.events.last().properties["pricing"]).isEqualTo("open")
    }

    @Test
    fun `the sheet on a pending bid reprices it in place`() = runTest(dispatcher) {
        val pending = DriverBid("b1", "a", "t1", Money(170_000), DriverBidStatus.PENDING)
        val repo = FakeBidding().apply {
            bids = listOf(pending)
            updateResult = AppResult.Success(
                pending.copy(amount = Money(150_000), lowestCompetingBid = Money(140_000)),
            )
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.openBid(vm.state.value.postings.single())
        assertThat(vm.state.value.editing).isEqualTo(pending)
        vm.updateBid("b1", 150_000)
        testScheduler.advanceUntilIdle()

        assertThat(repo.edits.single().second).isEqualTo(150_000)
        // The server's copy replaces the held one rather than joining it.
        assertThat(vm.state.value.myBids.map { it.amount }).isEqualTo(listOf(Money(150_000)))
        assertThat(vm.state.value.bidding).isNull()
        assertThat(vm.state.value.notice).isEqualTo(BidNotice.UPDATED)
        assertThat(analytics.events.last().properties["still_undercut"]).isEqualTo(true)
    }

    @Test
    fun `retrying the same edit reuses its key`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            bids = listOf(DriverBid("b1", "a", "t1", Money(170_000), DriverBidStatus.PENDING))
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.updateBid("b1", 150_000)
        testScheduler.advanceUntilIdle()
        vm.updateBid("b1", 150_000)
        testScheduler.advanceUntilIdle()
        vm.updateBid("b1", 140_000)
        testScheduler.advanceUntilIdle()

        val keys = repo.edits.map { it.third }
        assertThat(keys[0]).isEqualTo(keys[1])
        assertThat(keys[1]).isNotEqualTo(keys[2])
    }

    @Test
    fun `a bid on a fixed rate is never edited`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            firstPage = AppResult.Success(Page(listOf(posting("a").copy(targetRate = Money(150_000))), null))
            bids = listOf(DriverBid("b1", "a", "t1", Money(150_000), DriverBidStatus.PENDING))
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()

        vm.openBid(vm.state.value.postings.single())

        // The server refuses it with rate_is_fixed: there is no price to change.
        assertThat(vm.state.value.editing).isNull()
    }

    @Test
    fun `a withdrawn bid gives the action back, and bidding again revives its row`() = runTest(dispatcher) {
        val withdrawn = DriverBid("b1", "a", "t1", Money(170_000), DriverBidStatus.WITHDRAWN)
        val repo = FakeBidding().apply {
            bids = listOf(withdrawn)
            bidResult = AppResult.Success(withdrawn.copy(status = DriverBidStatus.PENDING))
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()
        assertThat(vm.state.value.bidFor("a")).isNull()

        vm.submitBid("a", 1700, "t1", "")
        testScheduler.advanceUntilIdle()

        // Same id, back at pending: one row, not the withdrawn one with the new one behind it.
        assertThat(vm.state.value.myBids.map { it.status }).isEqualTo(listOf(DriverBidStatus.PENDING))
        assertThat(vm.state.value.bidFor("a")?.status).isEqualTo(DriverBidStatus.PENDING)
    }

    @Test
    fun `a refused edit re-reads the driver's bids`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            bids = listOf(DriverBid("b1", "a", "t1", Money(170_000), DriverBidStatus.PENDING))
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()
        val before = repo.myBidsReads

        vm.updateBid("b1", 150_000)
        testScheduler.advanceUntilIdle()

        assertThat(repo.myBidsReads).isEqualTo(before + 1)
        assertThat(vm.state.value.errorMessage).isEqualTo("timeout")
        assertThat(analytics.events.last().name).isEqualTo("Bid Update Failed")
    }

    @Test
    fun `a bid someone undercut offers to match them to the halala, or to go under them`() {
        val bid = DriverBid("b1", "a", "t1", Money(90_000), DriverBidStatus.PENDING, lowestCompetingBid = Money(85_050))

        assertThat(bid.undercut(range = null)).isEqualTo(Undercut(Money(85_050), matchCents = 85_050, goLowerSar = 850))
        // Asking the same or more is not undercut, and a decided bid has nothing left to answer.
        assertThat(bid.copy(lowestCompetingBid = Money(90_000)).undercut(null)).isNull()
        assertThat(bid.copy(status = DriverBidStatus.WON).undercut(null)).isNull()
        assertThat(bid.copy(lowestCompetingBid = null).undercut(null)).isNull()
    }

    @Test
    fun `an answer the range would refuse is not offered`() {
        val bid = DriverBid("b1", "a", "t1", Money(90_000), DriverBidStatus.PENDING, lowestCompetingBid = Money(85_050))

        // 850 riyals is under the floor; matching still fits.
        assertThat(bid.undercut(BidRange(minCents = 85_010))?.goLowerSar).isNull()
        assertThat(bid.undercut(BidRange(minCents = 85_010))?.matchCents).isEqualTo(85_050)
        // The range moved above the competing bid: neither answer can land.
        assertThat(bid.undercut(BidRange(minCents = 86_000))).isEqualTo(Undercut(Money(85_050), null, null))
    }

    @Test
    fun `an edit refused because the bid was decided closes the sheet and reloads the board`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            bids = listOf(DriverBid("b1", "a", "t1", Money(170_000), DriverBidStatus.PENDING))
            updateResult = AppResult.Failure(AppError("bid is not pending", reason = AppErrorReason.BID_DECIDED))
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()
        vm.openBid(vm.state.value.postings.single())

        vm.updateBid("b1", 150_000)
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.bidding).isNull()
        assertThat(vm.state.value.editing).isNull()
        assertThat(vm.state.value.notice).isEqualTo(BidNotice.DECIDED)
        assertThat(repo.postingCursors).isEqualTo(listOf(null, null))
    }

    @Test
    fun `a refused edit keeps the sheet on the bid it opened on`() = runTest(dispatcher) {
        val pending = DriverBid("b1", "a", "t1", Money(170_000), DriverBidStatus.PENDING)
        val repo = FakeBidding().apply {
            bids = listOf(pending)
            updateResult = AppResult.Failure(AppError("not active", reason = AppErrorReason.DRIVER_NOT_VERIFIED))
        }
        val vm = DriverPostingsViewModel(repo, analytics)
        testScheduler.advanceUntilIdle()
        vm.openBid(vm.state.value.postings.single())
        // Whatever the re-read finds, the open sheet is still this edit, not a new bid.
        repo.bids = listOf(pending.copy(status = DriverBidStatus.WON))

        vm.updateBid("b1", 150_000)
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.editing?.id).isEqualTo("b1")
        assertThat(vm.state.value.bidErrorReason).isEqualTo(AppErrorReason.DRIVER_NOT_VERIFIED)
    }
}
