package co.sirdab.driver.shared.feature.loadboard.impl.presentation

import co.sirdab.driver.shared.core.util.localizeDigits
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun formatWindow(startMillis: Long, endMillis: Long, lang: String): String {
    val tz = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(startMillis).toLocalDateTime(tz)
    val end = Instant.fromEpochMilliseconds(endMillis).toLocalDateTime(tz)
    fun hhmm(h: Int, m: Int) = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    val date = "${start.dayOfMonth}/${start.monthNumber}"
    return "$date  ${hhmm(start.hour, start.minute)}–${hhmm(end.hour, end.minute)}".localizeDigits(lang)
}
