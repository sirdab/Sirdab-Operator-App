package co.sirdab.driver.shared.feature.trip.impl

import co.sirdab.driver.shared.core.demo.DemoClock
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.RouteUtil
import co.sirdab.driver.shared.core.demo.demoLatency
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.LatLng
import co.sirdab.driver.shared.core.model.NotificationKind
import co.sirdab.driver.shared.core.model.ProofOfDelivery
import co.sirdab.driver.shared.core.model.Trip
import co.sirdab.driver.shared.core.model.TripStatus
import co.sirdab.driver.shared.core.model.TxnStatus
import co.sirdab.driver.shared.core.model.TxnType
import co.sirdab.driver.shared.core.model.WalletTxn
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.platform.notification.Notifier
import co.sirdab.driver.shared.feature.trip.api.LocationRepository
import co.sirdab.driver.shared.feature.trip.api.TripRepository
import co.sirdab.driver.shared.feature.trip.impl.presentation.ActiveTripViewModel
import co.sirdab.driver.shared.feature.trip.impl.presentation.PodViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
class TripRepositoryMock(
    private val world: DemoWorld,
    private val clock: DemoClock,
    private val notifier: Notifier,
) : TripRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var drivingJob: Job? = null

    private fun now() = Clock.System.now().toEpochMilliseconds()

    override fun observeActiveTrip(): Flow<Trip?> =
        world.state.map { w -> w.trips.firstOrNull { it.status != TripStatus.COMPLETED } }

    override suspend fun routePoints(tripId: String): List<LatLng> {
        val trip = currentTrip(tripId) ?: return emptyList()
        val load = world.state.value.loads.firstOrNull { it.id == trip.loadId } ?: return emptyList()
        val cities = world.state.value.cities
        val from = cities.firstOrNull { it.id == load.originCityId }?.location ?: return emptyList()
        val to = cities.firstOrNull { it.id == load.destinationCityId }?.location ?: return emptyList()
        return RouteUtil.buildRoute(from, to)
    }

    override suspend fun advanceStatus(tripId: String): AppResult<Trip> {
        demoLatency()
        val trip = currentTrip(tripId) ?: return AppResult.Failure(AppError("Trip not found"))
        val next = nextStatus(trip.status)
        when (next) {
            TripStatus.EN_ROUTE_TO_PICKUP -> { setStatus(tripId, next); startDrive(tripId, pickupLeg = true) }
            TripStatus.LOADING -> setStatus(tripId, next) { it.copy(detentionStartedAtMillis = now()) }
            TripStatus.EN_ROUTE_TO_DROPOFF -> { setStatus(tripId, next); startDrive(tripId, pickupLeg = false) }
            else -> setStatus(tripId, next)
        }
        return AppResult.Success(currentTrip(tripId) ?: trip)
    }

    override suspend fun submitPod(pod: ProofOfDelivery): AppResult<Unit> {
        demoLatency()
        val trip = currentTrip(pod.tripId) ?: return AppResult.Failure(AppError("Trip not found"))
        setStatus(pod.tripId, TripStatus.COMPLETED) { it.copy(completedAtMillis = now(), routeProgress = 1f) }
        creditWallet(trip)
        pushNotification(
            NotificationKind.PAYOUT,
            "Delivery complete 🎉", "اكتمل التسليم 🎉",
            "Payment is on the way to your wallet.", "الدفعة في طريقها إلى محفظتك.",
        )
        return AppResult.Success(Unit)
    }

    // --- Simulation ---------------------------------------------------------

    private fun startDrive(tripId: String, pickupLeg: Boolean) {
        drivingJob?.cancel()
        drivingJob = scope.launch {
            val route = routePoints(tripId)
            if (route.isEmpty()) return@launch
            val trip = currentTrip(tripId) ?: return@launch
            val load = world.state.value.loads.firstOrNull { it.id == trip.loadId }
            val totalMinutes = if (pickupLeg) 20 else ((load?.distanceKm ?: 400) / 70.0 * 60).toInt()
            val steps = if (pickupLeg) 12 else 44
            var backhaulFired = false
            for (step in 0..steps) {
                val p = step.toFloat() / steps
                val pos = if (pickupLeg) route.first() else RouteUtil.pointAt(route, p)
                val eta = ((1f - p) * totalMinutes).toInt()
                setTrip(tripId) {
                    it.copy(
                        currentPosition = pos,
                        routeProgress = if (pickupLeg) it.routeProgress else p,
                        etaMinutes = eta,
                    )
                }
                // Backhaul fires ~20 simulated minutes before arrival — scripted to land on cue.
                if (!pickupLeg && !backhaulFired && p >= 0.55f) {
                    backhaulFired = true
                    pushNotification(
                        NotificationKind.BACKHAUL,
                        "Backhaul available", "حمولة عودة متاحة",
                        "A return load is available near your drop-off — no empty running.",
                        "تتوفّر شحنة عودة قرب وجهتك — دون سير فارغ.",
                    )
                }
                delayScaled(700)
            }
            setStatus(tripId, if (pickupLeg) TripStatus.AT_PICKUP else TripStatus.AT_DROPOFF) {
                it.copy(etaMinutes = 0)
            }
        }
    }

    private fun creditWallet(trip: Trip) {
        val ts = now()
        val load = world.state.value.loads.firstOrNull { it.id == trip.loadId }
        val txn = WalletTxn(
            id = "tx-$ts",
            type = TxnType.EARNING,
            amountSar = trip.agreedRateSar,
            status = TxnStatus.PENDING,
            descriptionEn = load?.let { "${it.originName} → ${it.destinationName}" } ?: "Trip earning",
            descriptionAr = load?.let { "${it.originName} ← ${it.destinationName}" } ?: "أرباح رحلة",
            createdAtMillis = ts,
        )
        world.update { w ->
            w.copy(wallet = w.wallet.copy(
                pendingSar = w.wallet.pendingSar + trip.agreedRateSar,
                transactions = listOf(txn) + w.wallet.transactions,
            ))
        }
    }

    private fun nextStatus(status: TripStatus): TripStatus = when (status) {
        TripStatus.ASSIGNED -> TripStatus.EN_ROUTE_TO_PICKUP
        TripStatus.EN_ROUTE_TO_PICKUP -> TripStatus.AT_PICKUP
        TripStatus.AT_PICKUP -> TripStatus.LOADING
        TripStatus.LOADING -> TripStatus.EN_ROUTE_TO_DROPOFF
        TripStatus.EN_ROUTE_TO_DROPOFF -> TripStatus.AT_DROPOFF
        TripStatus.AT_DROPOFF -> TripStatus.UNLOADING
        TripStatus.UNLOADING -> TripStatus.POD_PENDING
        TripStatus.POD_PENDING -> TripStatus.COMPLETED
        TripStatus.COMPLETED -> TripStatus.COMPLETED
    }

    private suspend fun delayScaled(ms: Long) {
        delay(ms / clock.multiplier.value.coerceAtLeast(1))
    }

    private fun currentTrip(tripId: String): Trip? = world.state.value.trips.firstOrNull { it.id == tripId }

    private fun setStatus(tripId: String, status: TripStatus, extra: (Trip) -> Trip = { it }) {
        setTrip(tripId) { extra(it).copy(status = status) }
    }

    private fun setTrip(tripId: String, transform: (Trip) -> Trip) {
        world.update { w -> w.copy(trips = w.trips.map { if (it.id == tripId) transform(it) else it }) }
    }

    private fun pushNotification(
        kind: NotificationKind,
        titleEn: String, titleAr: String,
        bodyEn: String, bodyAr: String,
    ) {
        val ts = now()
        val notif = AppNotification(
            id = "n-$ts-${kind.name}", kind = kind,
            titleEn = titleEn, titleAr = titleAr, bodyEn = bodyEn, bodyAr = bodyAr,
            read = false, createdAtMillis = ts,
        )
        world.update { it.copy(notifications = listOf(notif) + it.notifications) }
        val ar = AppLocale.current() == "ar"
        notifier.post(notif.id, if (ar) titleAr else titleEn, if (ar) bodyAr else bodyEn)
    }
}

class LocationRepositoryMock(private val world: DemoWorld) : LocationRepository {
    override fun observeDriverLocation(): Flow<LatLng?> =
        world.state.map { w -> w.trips.firstOrNull { it.status != TripStatus.COMPLETED }?.currentPosition }
}

val tripModule: Module = module {
    single { TripRepositoryMock(get(), get(), get()) } bind TripRepository::class
    single { LocationRepositoryMock(get()) } bind LocationRepository::class
    viewModelOf(::ActiveTripViewModel)
    viewModel { (tripId: String) -> PodViewModel(tripId, get()) }
}
