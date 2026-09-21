package co.sirdab.driver.shared.core.network

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Tracks how far the device clock has drifted from the server's.
 *
 * Driver writes carry the device's own `occurredAt`, and the contract is explicit that it must be
 * sent as recorded rather than corrected: a driver who taps "arrived" in a dead zone and syncs
 * forty minutes later still arrived when they arrived. So this never rewrites a timestamp. It only
 * makes the drift visible, so a phone with a badly wrong clock can be flagged instead of silently
 * poisoning dispatch's record.
 */
class ServerClock(private val deviceClock: Clock = Clock.System) {

    @Volatile
    private var offset: Duration = Duration.ZERO

    /** Drift, positive when the server is ahead of the device. */
    val skew: Duration get() = offset

    /** Wide enough to ignore ordinary latency, narrow enough that a wrong date stands out. */
    val isSuspect: Boolean get() = offset.absoluteValue > SUSPECT_THRESHOLD

    /** Called with the `Date` header of any successful response. */
    fun observeServerDate(serverTime: Instant) {
        offset = serverTime - deviceClock.now()
    }

    fun deviceNow(): Instant = deviceClock.now()

    private companion object {
        val SUSPECT_THRESHOLD = 5.minutes
    }
}
