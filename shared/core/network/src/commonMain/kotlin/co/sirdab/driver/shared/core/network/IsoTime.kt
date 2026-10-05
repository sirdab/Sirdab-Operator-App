package co.sirdab.driver.shared.core.network

import kotlin.time.Instant

/**
 * Timestamps arrive as ISO-8601 with an explicit offset. An unparseable one becomes null rather
 * than throwing: a trip or posting with one bad date is still one the driver has to see, and losing
 * the whole screen over it would be worse than losing the time.
 */
fun String?.toEpochMillisOrNull(): Long? {
    if (this.isNullOrBlank()) return null
    return runCatching { Instant.parse(this).toEpochMilliseconds() }.getOrNull()
}
