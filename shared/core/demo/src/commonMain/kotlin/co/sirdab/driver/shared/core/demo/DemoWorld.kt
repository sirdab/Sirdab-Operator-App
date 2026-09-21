package co.sirdab.driver.shared.core.demo

import co.sirdab.driver.shared.core.model.Vehicle
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
                earnings = seed.earnings,
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
