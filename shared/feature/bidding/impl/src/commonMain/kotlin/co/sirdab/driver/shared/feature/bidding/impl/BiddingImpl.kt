package co.sirdab.driver.shared.feature.bidding.impl

import co.sirdab.driver.shared.core.demo.DemoClock
import co.sirdab.driver.shared.core.demo.DemoControls
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.ForcedBidOutcome
import co.sirdab.driver.shared.core.demo.demoLatency
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Bid
import co.sirdab.driver.shared.core.model.BidEvent
import co.sirdab.driver.shared.core.model.BidStatus
import co.sirdab.driver.shared.core.model.CounterAction
import co.sirdab.driver.shared.core.model.NotificationKind
import co.sirdab.driver.shared.core.model.Trip
import co.sirdab.driver.shared.core.model.TripStatus
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.platform.notification.Notifier
import co.sirdab.driver.shared.feature.bidding.api.BidRepository
import co.sirdab.driver.shared.feature.bidding.impl.presentation.BidComposerViewModel
import co.sirdab.driver.shared.feature.bidding.impl.presentation.MyBidsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The heart of the demo: places a bid, then runs a scaled sequence — competing carriers undercut
 * (Outbid), then the shipper resolves (Awarded / Countered / Rejected), firing real notifications.
 * The demo control panel can force the outcome (DemoControls). Time is scaled by DemoClock so a
 * negotiation that takes ~30–120s live collapses at 60×/300×.
 */
@OptIn(ExperimentalTime::class)
class BidRepositoryMock(
    private val world: DemoWorld,
    private val notifier: Notifier,
    private val clock: DemoClock,
    private val controls: DemoControls,
) : BidRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val events = MutableSharedFlow<BidEvent>(extraBufferCapacity = 32)

    private fun now() = Clock.System.now().toEpochMilliseconds()

    override suspend fun placeBid(loadId: String, amountSar: Int, note: String?): AppResult<Bid> {
        demoLatency()
        val ts = now()
        val bid = Bid(
            id = "bid-$ts",
            loadId = loadId,
            amountSar = amountSar,
            note = note,
            status = BidStatus.PENDING,
            placedAtMillis = ts,
            updatedAtMillis = ts,
        )
        world.update { it.copy(bids = listOf(bid) + it.bids) }
        scope.launch { runBiddingSequence(bid.id) }
        return AppResult.Success(bid)
    }

    override suspend fun respondToCounter(bidId: String, action: CounterAction): AppResult<Bid> {
        demoLatency()
        val bid = currentBid(bidId) ?: return AppResult.Failure(AppError("Bid not found"))
        return when (action) {
            CounterAction.ACCEPT -> { award(bidId); AppResult.Success(currentBid(bidId) ?: bid) }
            CounterAction.REJECT -> { reject(bidId); AppResult.Success(currentBid(bidId) ?: bid) }
            CounterAction.COUNTER -> AppResult.Success(bid)
        }
    }

    override fun observeMyBids(): Flow<List<Bid>> = world.state.map { it.bids }

    override fun observeBidEvents(): Flow<BidEvent> = events.asSharedFlow()

    // --- Simulation ---------------------------------------------------------

    private suspend fun runBiddingSequence(bidId: String) {
        delayScaled(6_000)
        val afterOutbid = currentBid(bidId) ?: return
        if (afterOutbid.status != BidStatus.PENDING) return
        events.tryEmit(BidEvent.Outbid(bidId, leadingAmountSar = afterOutbid.amountSar - 40))
        pushNotification(
            NotificationKind.OUTBID,
            "You’ve been outbid", "تمت المزايدة عليك",
            "A competing carrier bid lower on your load.", "قدّم ناقل منافس سعرًا أقل على شحنتك.",
        )

        delayScaled(6_000)
        val bid = currentBid(bidId) ?: return
        if (bid.status != BidStatus.PENDING) return
        val load = world.state.value.loads.firstOrNull { it.id == bid.loadId }
        val suggested = load?.suggestedRateSar ?: bid.amountSar
        val outcome = controls.consumeForcedOutcome().let {
            if (it == ForcedBidOutcome.AUTO) weightedOutcome(bid.amountSar, suggested) else it
        }
        when (outcome) {
            ForcedBidOutcome.WIN -> award(bidId)
            ForcedBidOutcome.COUNTER -> counter(bidId, suggested)
            ForcedBidOutcome.LOSE -> reject(bidId)
            ForcedBidOutcome.AUTO -> award(bidId)
        }
    }

    private fun counter(bidId: String, suggested: Int) {
        val bid = currentBid(bidId) ?: return
        val counterAmount = ((bid.amountSar + suggested) / 2)
        updateBid(bidId) { it.copy(status = BidStatus.COUNTERED, counterAmountSar = counterAmount, updatedAtMillis = now()) }
        events.tryEmit(BidEvent.Countered(bidId, counterAmount))
        pushNotification(
            NotificationKind.COUNTERED,
            "Counteroffer received", "وصل عرض مضاد",
            "The shipper proposed a new rate.", "اقترح الشاحن سعرًا جديدًا.",
        )
    }

    private fun award(bidId: String) {
        val bid = currentBid(bidId) ?: return
        val agreed = bid.counterAmountSar ?: bid.amountSar
        updateBid(bidId) { it.copy(status = BidStatus.WON, updatedAtMillis = now()) }
        events.tryEmit(BidEvent.Awarded(bidId))
        // Create the active trip so Pass 3's trip screen has something to drive.
        world.update { w ->
            if (w.trips.any { it.loadId == bid.loadId }) w
            else w.copy(
                trips = w.trips + Trip(
                    id = "trip-${now()}",
                    loadId = bid.loadId,
                    status = TripStatus.ASSIGNED,
                    agreedRateSar = agreed,
                    startedAtMillis = now(),
                ),
            )
        }
        pushNotification(
            NotificationKind.AWARDED,
            "Load awarded 🎉", "تم منحك الشحنة 🎉",
            "You won the load. Head to your trip.", "لقد فزت بالشحنة. توجّه إلى رحلتك.",
            deepLink = "trip",
        )
    }

    private fun reject(bidId: String) {
        updateBid(bidId) { it.copy(status = BidStatus.LOST, updatedAtMillis = now()) }
        events.tryEmit(BidEvent.Rejected(bidId))
        pushNotification(
            NotificationKind.REJECTED,
            "Bid not accepted", "لم تُقبل المزايدة",
            "The shipper went with another carrier.", "اختار الشاحن ناقلًا آخر.",
        )
    }

    private fun weightedOutcome(amountSar: Int, suggested: Int): ForcedBidOutcome {
        val ratio = amountSar.toDouble() / suggested
        val r = Random.nextDouble()
        return if (ratio <= 1.05) {
            when { r < 0.75 -> ForcedBidOutcome.WIN; r < 0.95 -> ForcedBidOutcome.COUNTER; else -> ForcedBidOutcome.LOSE }
        } else {
            when { r < 0.60 -> ForcedBidOutcome.COUNTER; r < 0.85 -> ForcedBidOutcome.LOSE; else -> ForcedBidOutcome.WIN }
        }
    }

    private suspend fun delayScaled(ms: Long) {
        delay(ms / clock.multiplier.value.coerceAtLeast(1))
    }

    private fun currentBid(bidId: String): Bid? = world.state.value.bids.firstOrNull { it.id == bidId }

    private fun updateBid(bidId: String, transform: (Bid) -> Bid) {
        world.update { w -> w.copy(bids = w.bids.map { if (it.id == bidId) transform(it) else it }) }
    }

    private fun pushNotification(
        kind: NotificationKind,
        titleEn: String,
        titleAr: String,
        bodyEn: String,
        bodyAr: String,
        deepLink: String? = null,
    ) {
        val ts = now()
        val notif = AppNotification(
            id = "n-$ts-${kind.name}",
            kind = kind,
            titleEn = titleEn, titleAr = titleAr,
            bodyEn = bodyEn, bodyAr = bodyAr,
            read = false, createdAtMillis = ts, deepLink = deepLink,
        )
        world.update { it.copy(notifications = listOf(notif) + it.notifications) }
        val ar = AppLocale.current() == "ar"
        notifier.post(notif.id, if (ar) titleAr else titleEn, if (ar) bodyAr else bodyEn)
    }
}

val biddingModule: Module = module {
    single { BidRepositoryMock(get(), get(), get(), get()) } bind BidRepository::class
    viewModel { (loadId: String) -> BidComposerViewModel(loadId, get(), get()) }
    viewModelOf(::MyBidsViewModel)
}
