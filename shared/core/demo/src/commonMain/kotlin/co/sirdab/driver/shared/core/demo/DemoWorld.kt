package co.sirdab.driver.shared.core.demo

import co.sirdab.driver.shared.core.model.AutoBidRule
import co.sirdab.driver.shared.core.model.Bid
import co.sirdab.driver.shared.core.model.BidStatus
import co.sirdab.driver.shared.core.model.Trip
import co.sirdab.driver.shared.core.model.TripStatus
import co.sirdab.driver.shared.core.model.TxnStatus
import co.sirdab.driver.shared.core.model.Vehicle
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.core.model.VerificationState
import co.sirdab.driver.shared.core.preferences.KeyValueStore
import co.sirdab.driver.shared.core.util.testing.OpenForTesting
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.random.Random

private const val WORLD_KEY = "demo_world_snapshot"

/** Artificial network-like latency so loading states are honest (plan §4). */
suspend fun demoLatency() {
    kotlinx.coroutines.delay(Random.nextLong(300, 800))
}

/**
 * Holds the whole simulated world in one MutableStateFlow, snapshotting to DataStore on mutation.
 * Mock repositories read/write through this; simulation actors mutate it over time.
 */
@OpenForTesting
class DemoWorld(
    private val kv: KeyValueStore,
    private val json: Json,
    private val fixtureLoader: FixtureLoader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(WorldState())
    val state: StateFlow<WorldState> = _state.asStateFlow()

    private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        val snapshot = kv.getString(WORLD_KEY)
        val restored = snapshot?.let {
            runCatching { json.decodeFromString<WorldState>(it) }.getOrNull()
        }
        _state.value = restored ?: fixtureLoader.buildInitialWorld()
        loaded = true
    }

    fun update(block: (WorldState) -> WorldState) {
        _state.value = block(_state.value)
        persist()
    }

    fun setDriverPhone(phone: String) = update { it.copy(driver = it.driver.copy(phone = phone)) }

    fun saveAutoBid(rule: AutoBidRule) = update { it.copy(autoBidRule = rule) }

    /** Jump the world to a demo scenario in one tap (plan §10). */
    @OptIn(ExperimentalTime::class)
    suspend fun applyScenario(scenario: DemoScenario) {
        val now = Clock.System.now().toEpochMilliseconds()
        when (scenario) {
            DemoScenario.NEW_DRIVER -> reset()
            DemoScenario.BROWSING -> {
                switchPersona("flatbed_verified")
                update { it.copy(bids = emptyList(), trips = emptyList()) }
            }
            DemoScenario.BID_PENDING -> {
                switchPersona("flatbed_verified")
                update { w ->
                    val load = w.loads.firstOrNull { it.requiredVehicle == VehicleType.FLATBED } ?: return@update w
                    val bid = Bid(
                        id = "bid-scn-$now",
                        loadId = load.id,
                        amountSar = load.suggestedRateSar,
                        note = "Demo",
                        status = BidStatus.PENDING,
                        placedAtMillis = now,
                        updatedAtMillis = now,
                    )
                    w.copy(bids = listOf(bid) + w.bids.filter { it.status != BidStatus.PENDING })
                }
            }
            DemoScenario.TRIP_MID -> {
                switchPersona("flatbed_verified")
                update { w ->
                    val load = w.loads.firstOrNull { it.requiredVehicle == VehicleType.FLATBED } ?: return@update w
                    val trip = Trip("trip-scn-$now", load.id, TripStatus.EN_ROUTE_TO_DROPOFF, load.suggestedRateSar, routeProgress = 0.6f, etaMinutes = 90, startedAtMillis = now)
                    w.copy(trips = listOf(trip) + w.trips.filter { it.status == TripStatus.COMPLETED })
                }
            }
            DemoScenario.POD_PENDING -> {
                switchPersona("flatbed_verified")
                update { w ->
                    val load = w.loads.firstOrNull { it.requiredVehicle == VehicleType.FLATBED } ?: return@update w
                    val trip = Trip("trip-scn-$now", load.id, TripStatus.POD_PENDING, load.suggestedRateSar, routeProgress = 1f, startedAtMillis = now)
                    w.copy(trips = listOf(trip) + w.trips.filter { it.status == TripStatus.COMPLETED })
                }
            }
            DemoScenario.PAID -> update { w ->
                w.copy(wallet = w.wallet.copy(
                    availableSar = w.wallet.availableSar + w.wallet.pendingSar,
                    pendingSar = 0,
                    transactions = w.wallet.transactions.map { it.copy(status = TxnStatus.SETTLED) },
                ))
            }
        }
    }

    /** Swap the active driver to one of the seeded demo personas. */
    fun switchPersona(personaKey: String) = update { w ->
        val persona = w.personas.firstOrNull { it.personaKey == personaKey } ?: return@update w
        w.copy(driver = persona, onboardingComplete = persona.verification == VerificationState.VERIFIED)
    }

    /** Flip the fresh driver into the verified flatbed persona once onboarding finishes. */
    fun completeOnboarding(fullNameEn: String, fullNameAr: String, vehicle: Vehicle) {
        val seed = fixtureLoader.verifiedSeed()
        update { w ->
            w.copy(
                driver = w.driver.copy(
                    fullNameEn = fullNameEn,
                    fullNameAr = fullNameAr,
                    verification = VerificationState.VERIFIED,
                    carrierScore = seed.carrierScore,
                    tripsCompleted = seed.tripsCompleted,
                    onTimePercent = seed.onTimePercent,
                    vehicle = vehicle,
                    personaKey = "flatbed_verified",
                ),
                wallet = seed.wallet,
                documents = seed.documents,
                notifications = seed.notifications,
                onboardingComplete = true,
            )
        }
    }

    suspend fun reset() {
        _state.value = fixtureLoader.buildInitialWorld()
        kv.putString(WORLD_KEY, json.encodeToString(_state.value))
    }

    private fun persist() {
        val current = _state.value
        scope.launch { kv.putString(WORLD_KEY, json.encodeToString(current)) }
    }
}
