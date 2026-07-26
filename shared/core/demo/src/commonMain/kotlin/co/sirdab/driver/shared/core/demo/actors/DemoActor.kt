package co.sirdab.driver.shared.core.demo.actors

import kotlinx.coroutines.CoroutineScope

/**
 * A background simulation actor driven by DemoClock ticks. Pass 1 defines the contract and a
 * registry; the behaviours (competing bidders, shipper decisions, GPS driver, geofence watcher,
 * backhaul injector) are implemented in the bidding and trip passes.
 */
interface DemoActor {
    val id: String
    fun start(scope: CoroutineScope)
    fun stop()
}

/** Starts/stops the set of registered actors. Empty in Pass 1. */
class SimulationEngine(private val actors: List<DemoActor>) {
    fun startAll(scope: CoroutineScope) = actors.forEach { it.start(scope) }
    fun stopAll() = actors.forEach { it.stop() }
}
