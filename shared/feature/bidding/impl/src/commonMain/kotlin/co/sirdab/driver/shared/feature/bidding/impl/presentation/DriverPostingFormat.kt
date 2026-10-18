package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import co.sirdab.driver.shared.core.model.BidRange
import co.sirdab.driver.shared.core.model.DriverBidStatus
import co.sirdab.driver.shared.core.model.Money
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.bid_range_between
import co.sirdab.driver.shared.core.ui.generated.resources.bid_range_from
import co.sirdab.driver.shared.core.ui.generated.resources.bid_range_up_to
import co.sirdab.driver.shared.core.ui.generated.resources.bs_expired
import co.sirdab.driver.shared.core.ui.generated.resources.bs_lost
import co.sirdab.driver.shared.core.ui.generated.resources.bs_pending
import co.sirdab.driver.shared.core.ui.generated.resources.bs_withdrawn
import co.sirdab.driver.shared.core.ui.generated.resources.bs_won
import co.sirdab.driver.shared.core.ui.generated.resources.day_today
import co.sirdab.driver.shared.core.ui.generated.resources.day_tomorrow
import co.sirdab.driver.shared.core.ui.generated.resources.price_between
import co.sirdab.driver.shared.core.ui.generated.resources.price_from
import co.sirdab.driver.shared.core.ui.generated.resources.price_up_to
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.generated.resources.weekday_fri
import co.sirdab.driver.shared.core.ui.generated.resources.weekday_mon
import co.sirdab.driver.shared.core.ui.generated.resources.weekday_sat
import co.sirdab.driver.shared.core.ui.generated.resources.weekday_sun
import co.sirdab.driver.shared.core.ui.generated.resources.weekday_thu
import co.sirdab.driver.shared.core.ui.generated.resources.weekday_tue
import co.sirdab.driver.shared.core.ui.generated.resources.weekday_wed
import co.sirdab.driver.shared.core.util.localizeDigits
import co.sirdab.driver.shared.core.util.toGroupedString
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

internal fun DriverBidStatus.label(): StringResource = when (this) {
    DriverBidStatus.PENDING -> Res.string.bs_pending
    DriverBidStatus.WON -> Res.string.bs_won
    DriverBidStatus.LOST -> Res.string.bs_lost
    DriverBidStatus.WITHDRAWN -> Res.string.bs_withdrawn
    DriverBidStatus.EXPIRED -> Res.string.bs_expired
}

internal fun DriverBidStatus.tone(): ChipTone = when (this) {
    DriverBidStatus.PENDING -> ChipTone.WARNING
    DriverBidStatus.WON -> ChipTone.SUCCESS
    DriverBidStatus.LOST, DriverBidStatus.EXPIRED -> ChipTone.NEUTRAL
    DriverBidStatus.WITHDRAWN -> ChipTone.DANGER
}

/** Riyals, grouped and localised. The wire carries halalas; drivers think in riyals. */
internal fun Money.display(lang: String): String = amountSar.toGroupedString(lang)

internal val unitSar = Res.string.unit_sar

/** What a bid may ask, in the whole riyals a driver can type. Null when neither bound is set. */
@Composable
internal fun BidRange.label(lang: String): String? {
    val unit = stringResource(unitSar)
    val min = minSar?.toGroupedString(lang)
    val max = maxSar?.toGroupedString(lang)
    return when {
        min != null && max != null -> stringResource(Res.string.bid_range_between, min, max, unit)
        min != null -> stringResource(Res.string.bid_range_from, min, unit)
        max != null -> stringResource(Res.string.bid_range_up_to, max, unit)
        else -> null
    }
}

/**
 * The card's short form of a range, the unit left to the caller: "900 – 1,200", "From 900".
 * Null when neither bound is set.
 */
@Composable
internal fun BidRange.compactLabel(lang: String): String? {
    val min = minSar?.toGroupedString(lang)
    val max = maxSar?.toGroupedString(lang)
    return when {
        min != null && max != null -> stringResource(Res.string.price_between, min, max)
        min != null -> stringResource(Res.string.price_from, min)
        max != null -> stringResource(Res.string.price_up_to, max)
        else -> null
    }
}

/** An amount with its currency set smaller after it, as the card's price line reads: "900 SAR". */
@Composable
internal fun withUnit(amount: String): AnnotatedString {
    val unit = stringResource(unitSar)
    return buildAnnotatedString {
        append(amount)
        withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) { append(" $unit") }
    }
}

/**
 * When a pickup is, the way a driver plans a week: "Today 16:00", "Tomorrow 8:00", "Sat 6:00",
 * and a date beyond the coming week or before today.
 *
 * In the phone's own zone, the one the driver lives by; the server sends Riyadh time.
 */
@Composable
internal fun pickupLabel(atMillis: Long, nowMillis: Long, lang: String): String {
    val zone = TimeZone.currentSystemDefault()
    val at = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(zone)
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    val day = when (today.daysUntil(at.date)) {
        0 -> stringResource(Res.string.day_today)
        1 -> stringResource(Res.string.day_tomorrow)
        in 2..6 -> stringResource(at.dayOfWeek.label())
        else -> "${at.day}/${at.month.number}"
    }
    val time = "${at.hour}:${at.minute.toString().padStart(2, '0')}"
    return "$day $time".localizeDigits(lang)
}

private fun DayOfWeek.label(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.weekday_mon
    DayOfWeek.TUESDAY -> Res.string.weekday_tue
    DayOfWeek.WEDNESDAY -> Res.string.weekday_wed
    DayOfWeek.THURSDAY -> Res.string.weekday_thu
    DayOfWeek.FRIDAY -> Res.string.weekday_fri
    DayOfWeek.SATURDAY -> Res.string.weekday_sat
    DayOfWeek.SUNDAY -> Res.string.weekday_sun
}

/**
 * How long is left, to the coarsest useful unit.
 *
 * A deadline two days out does not need minutes, and one twenty minutes out
 * does not need days. Returns null once it has passed.
 */
internal fun remainingParts(closesAtMillis: Long, nowMillis: Long): Pair<Int, DurationUnit>? {
    val remaining = closesAtMillis - nowMillis
    if (remaining <= 0) return null
    val minutes = remaining / 60_000
    return when {
        minutes >= 1440 -> (minutes / 1440).toInt() to DurationUnit.DAYS
        minutes >= 60 -> (minutes / 60).toInt() to DurationUnit.HOURS
        else -> minutes.toInt().coerceAtLeast(1) to DurationUnit.MINUTES
    }
}

internal enum class DurationUnit { DAYS, HOURS, MINUTES }
