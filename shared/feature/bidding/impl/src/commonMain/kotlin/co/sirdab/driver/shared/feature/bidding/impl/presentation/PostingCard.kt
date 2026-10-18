package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.sirdab.driver.shared.core.model.BidType
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverBidStatus
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.dur_days
import co.sirdab.driver.shared.core.ui.generated.resources.dur_hours
import co.sirdab.driver.shared.core.ui.generated.resources.dur_minutes
import co.sirdab.driver.shared.core.ui.generated.resources.postings_accept
import co.sirdab.driver.shared.core.ui.generated.resources.postings_bid
import co.sirdab.driver.shared.core.ui.generated.resources.postings_closed
import co.sirdab.driver.shared.core.ui.generated.resources.postings_closes_in
import co.sirdab.driver.shared.core.ui.generated.resources.postings_distance
import co.sirdab.driver.shared.core.ui.generated.resources.postings_edit_bid
import co.sirdab.driver.shared.core.ui.generated.resources.postings_fixed_price
import co.sirdab.driver.shared.core.ui.generated.resources.postings_open_price
import co.sirdab.driver.shared.core.ui.generated.resources.postings_price
import co.sirdab.driver.shared.core.ui.generated.resources.postings_undercut
import co.sirdab.driver.shared.core.ui.generated.resources.postings_you_bid
import co.sirdab.driver.shared.core.ui.generated.resources.postings_your_bid
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.toGroupedString
import org.jetbrains.compose.resources.stringResource

/**
 * A posting on the board, cut like a ticket: the load on top, and under the perforation, its
 * price and what the driver can do about it.
 *
 * [bid] is the driver's own offer on it. Once there is one, the bottom half is about that offer
 * instead, and the card is outlined so it stands out from the ones still to answer.
 */
@Composable
internal fun PostingCard(
    posting: DriverPosting,
    bid: DriverBid?,
    lang: String,
    nowMillis: Long,
    onBid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = MaterialTheme.colorScheme.surface
    val border = if (bid != null) AppColors.Primary.c400 else AppColors.Gray.c200

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .ticketHalf(Seam.BOTTOM, fill, border)
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        ) {
            // No time, no deadline and no bid leave nothing to say up top, and an empty row would
            // only push the lane down.
            val pickup = posting.pickupAtMillis ?: posting.pickupWindowStartMillis
            if (pickup != null || bid != null || posting.biddingClosesAtMillis != null) {
                Header(posting, bid, lang, nowMillis)
                Spacer(Modifier.height(Spacing.xs))
            }
            Route(
                origin = posting.origin.city ?: posting.origin.label,
                destination = posting.destination.city ?: posting.destination.label,
                distanceKm = posting.distanceKm,
                lang = lang,
                background = fill,
            )
            Spacer(Modifier.height(Spacing.xs))
            Specs(posting)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .ticketHalf(Seam.TOP, fill, border)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Footer(posting, bid, lang, onBid, Modifier.fillMaxWidth())
            bid?.undercut(posting.bidRange)?.let { undercut ->
                Spacer(Modifier.height(Spacing.xs))
                Undercut(undercut, lang, onClick = onBid)
            }
        }
    }
}

@Composable
private fun Header(posting: DriverPosting, bid: DriverBid?, lang: String, nowMillis: Long) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The order's own time first; the posting's window when the order has none.
        val pickup = posting.pickupAtMillis ?: posting.pickupWindowStartMillis
        Text(
            pickup?.let { pickupLabel(it, nowMillis, lang).uppercase() }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (bid != null) {
            TagChip(
                stringResource(Res.string.postings_you_bid),
                tone = ChipTone.PRIMARY,
                leading = { tint -> Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = tint) },
            )
        } else {
            Countdown(posting.biddingClosesAtMillis, nowMillis)
        }
    }
}

/** Origin, the lane between them with its distance over the truck, and destination. */
@Composable
private fun Route(origin: String, destination: String, distanceKm: Int?, lang: String, background: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CityName(origin, TextAlign.Start)
        Lane(
            distanceKm = distanceKm,
            lang = lang,
            background = background,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs),
        )
        CityName(destination, TextAlign.End)
    }
}

@Composable
private fun CityName(name: String, align: TextAlign) {
    Text(
        name,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        // Capped so two long names still leave the lane room to read.
        modifier = Modifier.widthIn(max = 116.dp),
    )
}

/**
 * A dotted line from an open circle (where the load is) to a filled one (where it goes), with the
 * truck in the middle. The distance sits above the truck, and an equal gap below keeps the line
 * level with the city names either side.
 */
@Composable
private fun Lane(distanceKm: Int?, lang: String, background: Color, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (distanceKm != null) {
            Text(
                // As the crow flies: the contract asks for it to read as approximate.
                stringResource(Res.string.postings_distance, distanceKm.toGroupedString(lang)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.height(LANE_LABEL_HEIGHT),
            )
        }
        Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val y = size.height / 2
                val radius = 3.5.dp.toPx()
                val stroke = 1.5.dp.toPx()
                val left = radius + stroke / 2
                val right = size.width - radius - stroke / 2
                // The origin is wherever the reading starts, which is the right in Arabic.
                val (from, to) = if (rtl) right to left else left to right
                drawLine(
                    color = AppColors.Gray.c300,
                    start = Offset(left + radius + 4.dp.toPx(), y),
                    end = Offset(right - radius - 4.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
                )
                drawCircle(ink, radius, Offset(from, y), style = Stroke(stroke))
                drawCircle(ink, radius, Offset(to, y))
            }
            Icon(
                Icons.Outlined.LocalShipping,
                contentDescription = null,
                tint = AppColors.Accent.c800,
                modifier = Modifier
                    .background(background)
                    .padding(horizontal = 4.dp)
                    .size(18.dp)
                    // The truck drives the way the lane reads.
                    .graphicsLayer { scaleX = if (rtl) -1f else 1f },
            )
        }
        if (distanceKm != null) Spacer(Modifier.height(LANE_LABEL_HEIGHT))
    }
}

/** What the load needs. An axis the posting never classified is left out rather than guessed. */
@Composable
private fun Specs(posting: DriverPosting) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        posting.sizeClass?.let {
            Spec(stringResource(it.labelRes()), Icons.Outlined.LocalShipping, AppColors.Gray.c100, AppColors.Gray.c700)
        }
        posting.bodyType?.let {
            Spec(stringResource(it.labelRes()), Icons.Outlined.Inventory2, AppColors.Gray.c100, AppColors.Gray.c700)
        }
        posting.temperature?.let {
            // Cold chain reads blue at a glance; anything else wears the brand.
            val (bg, fg) = if (it == EquipmentTemperature.AMBIENT) {
                AppColors.Primary.c50 to AppColors.Primary.c800
            } else {
                AppColors.Blue.c50 to AppColors.Blue.c700
            }
            Spec(stringResource(it.labelRes()), Icons.Outlined.Thermostat, bg, fg)
        }
    }
}

@Composable
private fun Spec(text: String, icon: ImageVector, background: Color, content: Color) {
    Row(
        Modifier
            .background(background, RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/**
 * The price and the one thing to do about it. With a bid in, that is the driver's own amount and
 * where it stands, and an edit while it is still pending on a price that can move.
 */
@Composable
private fun Footer(
    posting: DriverPosting,
    bid: DriverBid?,
    lang: String,
    onBid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rate = posting.targetRate
    val (label, value) = when {
        bid != null -> stringResource(Res.string.postings_your_bid) to withUnit(bid.amount.display(lang))
        rate != null -> stringResource(Res.string.postings_fixed_price) to withUnit(rate.display(lang))
        else -> stringResource(Res.string.postings_price) to (
            posting.bidRange?.compactLabel(lang)?.let { withUnit(it) }
                ?: AnnotatedString(stringResource(Res.string.postings_open_price))
            )
    }

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        when {
            bid != null -> {
                TagChip(
                    stringResource(bid.status.label()),
                    tone = bid.status.tone(),
                    leading = { tint -> Box(Modifier.size(6.dp).background(tint, CircleShape)) },
                )
                // A fixed rate has no price to change, and a decided bid nothing left to change.
                if (bid.status == DriverBidStatus.PENDING && posting.bidType != BidType.FIXED) {
                    OutlinedButton(
                        onClick = onBid,
                        shape = RoundedCornerShape(Radius.md),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = CardButtonPadding,
                        modifier = Modifier.heightIn(min = CARD_BUTTON_HEIGHT),
                    ) {
                        Text(stringResource(Res.string.postings_edit_bid), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            else -> Button(
                onClick = onBid,
                shape = RoundedCornerShape(Radius.md),
                contentPadding = CardButtonPadding,
                modifier = Modifier.heightIn(min = CARD_BUTTON_HEIGHT),
            ) {
                Text(
                    stringResource(if (rate != null) Res.string.postings_accept else Res.string.postings_bid),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/**
 * Someone else asking less than the driver's pending bid. Said on the card, not only in the
 * sheet, so the driver learns it from the board; a tap opens the sheet on "go lower or match?".
 */
@Composable
private fun Undercut(undercut: Undercut, lang: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(AppColors.Amber.c50)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = AppColors.Amber.c800,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(
                Res.string.postings_undercut,
                "${undercut.lowest.display(lang)}\u00A0${stringResource(unitSar)}",
            ),
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.Amber.c800,
        )
    }
}

@Composable
private fun Countdown(closesAtMillis: Long?, nowMillis: Long) {
    if (closesAtMillis == null) return
    val parts = remainingParts(closesAtMillis, nowMillis)

    if (parts == null) {
        TagChip(stringResource(Res.string.postings_closed), tone = ChipTone.DANGER)
        return
    }

    val (value, unit) = parts
    val text = when (unit) {
        DurationUnit.DAYS -> stringResource(Res.string.dur_days, value)
        DurationUnit.HOURS -> stringResource(Res.string.dur_hours, value)
        DurationUnit.MINUTES -> stringResource(Res.string.dur_minutes, value)
    }
    // Anything under an hour is the one a driver should answer now.
    TagChip(
        stringResource(Res.string.postings_closes_in, text),
        tone = if (unit == DurationUnit.MINUTES) ChipTone.WARNING else ChipTone.NEUTRAL,
    )
}

/** Which edge of a [ticketHalf] meets the other half. */
private enum class Seam { TOP, BOTTOM }

/**
 * One half of the ticket: a rounded rectangle with half-circle notches cut where it meets the
 * other half, and the perforation drawn along that seam by the top half.
 *
 * Drawn as two halves, each knowing its own height, so the notches land on the seam without
 * measuring where the content put it. The outline skips the seam itself, which would otherwise
 * draw a solid line under the dashed one.
 */
private fun Modifier.ticketHalf(seam: Seam, fill: Color, border: Color): Modifier = drawWithCache {
    val corner = Radius.lg.toPx()
    val notch = NOTCH_RADIUS.toPx()
    val w = size.width
    val h = size.height

    // Starts at one end of the seam and runs round the outside to the other, so leaving it open
    // gives the border and closing it gives the fill.
    fun outline(close: Boolean) = Path().apply {
        when (seam) {
            Seam.BOTTOM -> {
                moveTo(notch, h)
                arcTo(Rect(Offset(0f, h), notch), 0f, -90f, false)
                lineTo(0f, corner)
                arcTo(Rect(Offset(corner, corner), corner), 180f, 90f, false)
                lineTo(w - corner, 0f)
                arcTo(Rect(Offset(w - corner, corner), corner), 270f, 90f, false)
                lineTo(w, h - notch)
                arcTo(Rect(Offset(w, h), notch), 270f, -90f, false)
            }
            Seam.TOP -> {
                moveTo(w - notch, 0f)
                arcTo(Rect(Offset(w, 0f), notch), 180f, -90f, false)
                lineTo(w, h - corner)
                arcTo(Rect(Offset(w - corner, h - corner), corner), 0f, 90f, false)
                lineTo(corner, h)
                arcTo(Rect(Offset(corner, h - corner), corner), 90f, 90f, false)
                lineTo(0f, notch)
                arcTo(Rect(Offset(0f, 0f), notch), 90f, -90f, false)
            }
        }
        if (close) close()
    }
    val body = outline(close = true)
    val edge = outline(close = false)
    val stroke = Stroke(1.dp.toPx())
    val gap = 6.dp.toPx()

    onDrawBehind {
        drawPath(body, fill)
        drawPath(edge, border, style = stroke)
        if (seam == Seam.BOTTOM) {
            drawLine(
                color = AppColors.Gray.c300,
                start = Offset(notch + gap, h),
                end = Offset(w - notch - gap, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

private val NOTCH_RADIUS = 7.dp
private val LANE_LABEL_HEIGHT = 14.dp
private val CARD_BUTTON_HEIGHT = 44.dp
private val CardButtonPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)
