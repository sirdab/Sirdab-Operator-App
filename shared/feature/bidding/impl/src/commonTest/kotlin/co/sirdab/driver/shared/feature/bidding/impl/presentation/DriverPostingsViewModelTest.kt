package co.sirdab.driver.shared.feature.bidding.impl.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.model.PostingPlace
import co.sirdab.driver.shared.core.model.PostingStatus
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
    var myBidsReads = 0

    /** Holds the second page open, so a test can tap again while it is in flight. */
    val secondPage = CompletableDeferred<AppResult<Page<DriverPosting>>>()
    var bidResult: AppResult<DriverBid> = AppResult.Failure(AppError("timeout"))
    var firstPage: AppResult<Page<DriverPosting>> = AppResult.Success(Page(listOf(posting("a")), "c1"))

    override suspend fun postings(cursor: String?, limit: Int): AppResult<Page<DriverPosting>> {
        postingCursors += cursor
        return if (cursor == null) firstPage else secondPage.await()
    }

    override suspend fun trucks(): AppResult<List<DriverTruck>> = AppResult.Success(emptyList())

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

    override suspend fun myBids(cursor: String?, limit: Int): AppResult<Page<DriverBid>> {
        myBidsReads++
        return AppResult.Success(Page(emptyList(), null))
    }
}

class DriverPostingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a double tap on load more fetches the page once`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo)
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
    fun `a company driver's refusal is a state of its own, not a fault`() = runTest(dispatcher) {
        val repo = FakeBidding().apply {
            firstPage = AppResult.Failure(
                AppError("only independent operators bid from the app", reason = AppErrorReason.NOT_A_BIDDER),
            )
        }
        val vm = DriverPostingsViewModel(repo)
        testScheduler.advanceUntilIdle()

        // No red text and no retry button: retrying would be refused the same way for ever.
        assertThat(vm.state.value.isCompanyDriver).isEqualTo(true)
        assertThat(vm.state.value.isBoardOutOfScope).isEqualTo(true)
    }

    @Test
    fun `retrying the same offer reuses its key`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo)
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
        val vm = DriverPostingsViewModel(repo)
        testScheduler.advanceUntilIdle()

        vm.submitBid("p1", 1700, "t1", "")
        testScheduler.advanceUntilIdle()
        vm.submitBid("p1", 1500, "t1", "")
        testScheduler.advanceUntilIdle()

        // The server refuses a key reused for a different body.
        assertThat(repo.bidKeys[0]).isNotEqualTo(repo.bidKeys[1])
    }

    @Test
    fun `a failed bid re-reads the driver's bids`() = runTest(dispatcher) {
        val repo = FakeBidding()
        val vm = DriverPostingsViewModel(repo)
        testScheduler.advanceUntilIdle()
        val before = repo.myBidsReads

        vm.submitBid("p1", 1700, "t1", "")
        testScheduler.advanceUntilIdle()

        assertThat(repo.myBidsReads).isEqualTo(before + 1)
    }
}
