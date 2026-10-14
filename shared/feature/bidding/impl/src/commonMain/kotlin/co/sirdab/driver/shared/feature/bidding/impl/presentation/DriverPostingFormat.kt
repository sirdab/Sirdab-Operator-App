package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.runtime.Composable
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
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.util.toGroupedString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
