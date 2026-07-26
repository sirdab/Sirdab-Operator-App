package co.sirdab.driver.shared.core.demo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

/**
 * Drives simulated time. Actors (competing bidders, GPS, geofence, backhaul) subscribe to [ticks].
 * The multiplier lets the demo run a full Riyadh→Dammam trip at 300× (plan §7 / §12).
 */
class DemoClock {
    val multiplier = MutableStateFlow(1)

    val ticks: Flow<Long> = flow {
        var t = 0L
        while (true) {
            delay(1000L / multiplier.value.coerceAtLeast(1))
            emit(++t)
        }
    }

    fun setMultiplier(value: Int) {
        multiplier.value = value.coerceIn(1, 300)
    }
}
