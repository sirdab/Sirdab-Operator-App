package co.sirdab.driver.shared.core.demo

import kotlinx.coroutines.flow.MutableStateFlow

/** Outcome the demo operator forces for the next bid, overriding the weighted simulation. */
enum class ForcedBidOutcome { AUTO, WIN, COUNTER, LOSE }

/** One-tap demo scenarios (plan §10). */
enum class DemoScenario { NEW_DRIVER, BROWSING, BID_PENDING, TRIP_MID, POD_PENDING, PAID }

/**
 * Shared knobs the demo control panel writes and the simulation reads, so a live demo becomes a
 * rehearsed performance rather than a prayer (plan §10).
 */
class DemoControls {
    val forcedOutcome = MutableStateFlow(ForcedBidOutcome.AUTO)

    /** Read the forced outcome and reset to AUTO so it only applies to the next bid. */
    fun consumeForcedOutcome(): ForcedBidOutcome {
        val value = forcedOutcome.value
        forcedOutcome.value = ForcedBidOutcome.AUTO
        return value
    }
}
