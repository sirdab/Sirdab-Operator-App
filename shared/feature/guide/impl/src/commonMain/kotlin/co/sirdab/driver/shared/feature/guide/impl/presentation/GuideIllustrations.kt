package co.sirdab.driver.shared.feature.guide.impl.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.bid_amount
import co.sirdab.driver.shared.core.ui.generated.resources.bid_truck
import co.sirdab.driver.shared.core.ui.generated.resources.dur_hours
import co.sirdab.driver.shared.core.ui.generated.resources.ek_delay
import co.sirdab.driver.shared.core.ui.generated.resources.ek_refused
import co.sirdab.driver.shared.core.ui.generated.resources.ek_vehicle_breakdown
import co.sirdab.driver.shared.core.ui.generated.resources.equip_body_box
import co.sirdab.driver.shared.core.ui.generated.resources.equip_size_heavy
import co.sirdab.driver.shared.core.ui.generated.resources.exc_report
import co.sirdab.driver.shared.core.ui.generated.resources.exc_title
import co.sirdab.driver.shared.core.ui.generated.resources.guide_open
import co.sirdab.driver.shared.core.ui.generated.resources.guide_sample_destination
import co.sirdab.driver.shared.core.ui.generated.resources.guide_sample_origin
import co.sirdab.driver.shared.core.ui.generated.resources.postings_bid
import co.sirdab.driver.shared.core.ui.generated.resources.postings_closes_in
import co.sirdab.driver.shared.core.ui.generated.resources.postings_distance
import co.sirdab.driver.shared.core.ui.generated.resources.postings_price
import co.sirdab.driver.shared.core.ui.generated.resources.postings_undercut
import co.sirdab.driver.shared.core.ui.generated.resources.profile_documents
import co.sirdab.driver.shared.core.ui.generated.resources.profile_language
import co.sirdab.driver.shared.core.ui.generated.resources.routes_row
import co.sirdab.driver.shared.core.ui.generated.resources.sact_arrive
import co.sirdab.driver.shared.core.ui.generated.resources.sst_pending
import co.sirdab.driver.shared.core.ui.generated.resources.stop_choose_photo
import co.sirdab.driver.shared.core.ui.generated.resources.stop_take_photo
import co.sirdab.driver.shared.core.ui.generated.resources.tab_loads
import co.sirdab.driver.shared.core.ui.generated.resources.tab_profile
import co.sirdab.driver.shared.core.ui.generated.resources.tab_trip
import co.sirdab.driver.shared.core.ui.generated.resources.tl_assigned
import co.sirdab.driver.shared.core.ui.generated.resources.trip_pending_sync
import co.sirdab.driver.shared.core.ui.generated.resources.trip_start
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_call
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_navigate
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_pickup
import co.sirdab.driver.shared.core.ui.generated.resources.trips_stops_count
import co.sirdab.driver.shared.core.ui.generated.resources.trucks_title
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.localizeDigits
import co.sirdab.driver.shared.core.util.toGroupedString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The picture at the top of a page: the part of the real screen the page is about, drawn still,
 * with the one thing to tap pulsing under a finger.
 *
 * Drawn rather than shipped as screenshots. A screenshot is in one language, goes stale the day
 * its screen changes, and cannot turn round for Arabic; these are built from the screens' own
 * words and colours, so they read in the driver's language and follow it when it changes.
 *
 * Nothing in them can be tapped, and a screen reader skips them: the words under the picture say
 * the same thing, and a button that does nothing when pressed is worse than no button at all.
 *
 * It fills the height it is given, and a scene taller than that is shrunk evenly to fit rather than
 * cut off, so the page decides how much room the picture gets.
 */
@Composable
internal fun GuideIllustration(page: GuidePage, canBid: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(AppColors.Primary.c50, RoundedCornerShape(Radius.xl))
            .padding(Spacing.md)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.shrinkToFit()) {
            Scene(page, canBid)
        }
    }
}

@Composable
private fun Scene(page: GuidePage, canBid: Boolean) {
    when (page) {
        GuidePage.WELCOME -> WelcomeScene(canBid)
        GuidePage.LOADS -> LoadsScene()
        GuidePage.BID -> BidScene()
        GuidePage.TRIPS -> TripsScene()
        GuidePage.STOPS -> StopsScene()
        GuidePage.PROOF -> ProofScene()
        GuidePage.PROBLEM -> ProblemScene()
        GuidePage.PROFILE -> ProfileScene()
    }
}

/**
 * Lets what it holds be as tall as it likes, then scales it down, evenly and from the top centre,
 * until it fits the height on offer. Never up: a scene drawn at its real size already reads.
 */
private fun Modifier.shrinkToFit(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val scale = if (constraints.hasBoundedHeight && placeable.height > constraints.maxHeight) {
        constraints.maxHeight.toFloat() / placeable.height
    } else {
        1f
    }
    layout(placeable.width, (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

/** The road the app is for, and the tabs that everything else in it lives under. */
@Composable
private fun WelcomeScene(canBid: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        DrivingTruck()
        // The tabs this driver will have: no board for a company's driver.
        MockNavBar(tabs = MockTab.entries.filter { canBid || it != MockTab.LOADS })
    }
}

/** The board: the spinner a pull brings down, a load on it, and the tab it is under. */
@Composable
private fun LoadsScene() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PullToRefresh()
        MockPosting()
        MockNavBar(selected = MockTab.LOADS, tapped = MockTab.LOADS)
    }
}

/**
 * Sending a price: the button on the card, what the sheet it opens asks for, and the warning
 * the card shows when someone else asks less.
 */
@Composable
private fun BidScene() {
    val lang = AppLocale.current()
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            Modifier.fillMaxWidth().mockCard().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Price(lang, Modifier.weight(1f))
            TapHere(corner = Radius.md) {
                MockButton(stringResource(Res.string.postings_bid), filled = true, shape = RoundedCornerShape(Radius.md))
            }
        }

        Column(Modifier.fillMaxWidth().mockCard().padding(Spacing.md)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(2.dp, primary, RoundedCornerShape(Radius.md))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                Text(stringResource(Res.string.bid_amount), style = MaterialTheme.typography.labelSmall, color = primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        2400.toGroupedString(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Caret()
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(Res.string.bid_truck),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxs))
            MockOption(
                "${stringResource(Res.string.equip_size_heavy)} · ${stringResource(Res.string.equip_body_box)}",
                selected = true,
                icon = Icons.Outlined.LocalShipping,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(Radius.sm), clip = false)
                .background(AppColors.Amber.c50, RoundedCornerShape(Radius.sm))
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
                    "${2300.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                ),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.Amber.c800,
            )
        }
    }
}

/** A trip waiting on the driver, the button that sets it off, and the tab it is under. */
@Composable
private fun TripsScene() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.fillMaxWidth().mockCard().padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The trip's reference, which is the dispatcher's to choose.
                Placeholder(Modifier.width(96.dp), height = 14.dp)
                TagChip(stringResource(Res.string.tl_assigned), tone = ChipTone.PRIMARY)
            }
            Spacer(Modifier.height(Spacing.xs))
            TagChip(stringResource(Res.string.trips_stops_count, 2))
            Spacer(Modifier.height(Spacing.md))
            TapHere(Modifier.fillMaxWidth()) {
                MockButton(stringResource(Res.string.trip_start), filled = true, modifier = Modifier.fillMaxWidth())
            }
        }
        MockNavBar(selected = MockTab.TRIP)
    }
}

/**
 * A stop as the trip draws it: its number on the rail, what kind of stop it is, the two ways to
 * get help reaching it, and the button for when the driver has. Above it, the count of taps the
 * phone is holding until there is signal again.
 */
@Composable
private fun StopsScene() {
    val lang = AppLocale.current()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(Radius.lg), clip = false)
                .background(AppColors.Amber.c50, RoundedCornerShape(Radius.lg))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AppColors.Amber.c800, modifier = Modifier.size(18.dp))
            Text(
                stringResource(Res.string.trip_pending_sync, 1).localizeDigits(lang),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AppColors.Amber.c800,
            )
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(
                Modifier.width(32.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StopNumber("1".localizeDigits(lang), current = true)
                Box(Modifier.width(2.dp).weight(1f).background(AppColors.Gray.c300))
                StopNumber("2".localizeDigits(lang), current = false)
            }
            Spacer(Modifier.width(Spacing.xs))
            Column(Modifier.weight(1f).mockCard().padding(Spacing.sm)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagChip(stringResource(Res.string.trip_stop_pickup), tone = ChipTone.PRIMARY)
                    TagChip(stringResource(Res.string.sst_pending))
                }
                Spacer(Modifier.height(Spacing.xs))
                // The address, which is the stop's own.
                Placeholder(Modifier.fillMaxWidth(0.7f), height = 12.dp)
                Spacer(Modifier.height(Spacing.xxs))
                Placeholder(Modifier.fillMaxWidth(0.5f))
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    MockButton(
                        stringResource(Res.string.trip_stop_navigate),
                        filled = false,
                        icon = Icons.Default.Directions,
                        modifier = Modifier.weight(1f),
                    )
                    MockButton(
                        stringResource(Res.string.trip_stop_call),
                        filled = false,
                        icon = Icons.Default.Phone,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                TapHere(Modifier.fillMaxWidth()) {
                    MockButton(stringResource(Res.string.sact_arrive), filled = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Spacing.xxs))
            }
        }
    }
}

/** The two photos a delivery takes, in order: the load, then the signed note. */
@Composable
private fun ProofScene() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Viewfinder(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AppColors.Primary.c700,
            )
            DeliveryNote(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TapHere(Modifier.weight(1f)) {
                MockButton(
                    stringResource(Res.string.stop_take_photo),
                    filled = true,
                    icon = Icons.Default.PhotoCamera,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MockButton(
                stringResource(Res.string.stop_choose_photo),
                filled = false,
                icon = Icons.Default.PhotoLibrary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The button that is on every trip, and the first thing the sheet it opens asks. */
@Composable
private fun ProblemScene() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        TapHere(Modifier.fillMaxWidth()) {
            MockButton(stringResource(Res.string.exc_report), filled = false, modifier = Modifier.fillMaxWidth())
        }
        Column(
            Modifier.fillMaxWidth().mockCard().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                stringResource(Res.string.exc_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            MockOption(stringResource(Res.string.ek_delay), selected = true)
            MockOption(stringResource(Res.string.ek_vehicle_breakdown), selected = false)
            MockOption(stringResource(Res.string.ek_refused), selected = false)
        }
    }
}

/** What the profile keeps, this guide among it, and the tab it is under. */
@Composable
private fun ProfileScene() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.fillMaxWidth().mockCard().padding(vertical = Spacing.xxs)) {
            ProfileRow(Icons.Outlined.Folder, Res.string.profile_documents)
            ProfileRow(Icons.Outlined.LocalShipping, Res.string.trucks_title)
            ProfileRow(Icons.Outlined.Place, Res.string.routes_row)
            ProfileRow(Icons.Default.Translate, Res.string.profile_language)
            ProfileRow(Icons.AutoMirrored.Filled.HelpOutline, Res.string.guide_open, highlighted = true)
        }
        MockNavBar(selected = MockTab.PROFILE, tapped = MockTab.PROFILE)
    }
}

// ---- The pieces the scenes are built from ----

/** The shell's tabs, with the icons and words the shell gives them. */
private enum class MockTab(val icon: ImageVector, val label: StringResource) {
    LOADS(Icons.Default.LocalShipping, Res.string.tab_loads),
    TRIP(Icons.Default.Map, Res.string.tab_trip),
    PROFILE(Icons.Default.Person, Res.string.tab_profile),
}

/** The shell's bottom bar: an icon over a label, the open tab's icon on a pill. */
@Composable
private fun MockNavBar(
    tabs: List<MockTab> = MockTab.entries,
    selected: MockTab? = null,
    tapped: MockTab? = null,
) {
    Row(
        Modifier.fillMaxWidth().mockCard().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (tab == tapped) {
                    TapHere(corner = Radius.lg) { NavItem(tab, selected = tab == selected) }
                } else {
                    NavItem(tab, selected = tab == selected)
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: MockTab, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 28.dp)
                .background(if (selected) colors.secondaryContainer else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tab.icon,
                contentDescription = null,
                tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else null,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Marks the one thing in a picture to tap: a ring swelling out of it, and a finger pressing it in
 * time with the ring. [corner] is the radius of what it wraps; a pill's is half its height, which
 * is what the default comes to.
 *
 * The finger hangs off the bottom corner without taking up room, so wrapping a button does not
 * move it. Whatever sits below needs a gap of [Spacing.md] to keep clear of it.
 */
@Composable
private fun TapHere(
    modifier: Modifier = Modifier,
    corner: Dp = Radius.pill,
    content: @Composable () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ring = MaterialTheme.colorScheme.primary
    val pulse = rememberInfiniteTransition(label = "tap").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearOutSlowInEasing)),
        label = "pulse",
    )
    Box(modifier) {
        Box(
            Modifier.drawBehind {
                val p = pulse.value
                val grow = 10.dp.toPx() * p
                drawRoundRect(
                    color = ring,
                    topLeft = Offset(-grow, -grow),
                    size = Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = CornerRadius(minOf(corner.toPx(), size.height / 2) + grow),
                    alpha = 0.6f * (1f - p),
                    style = Stroke(3.dp.toPx()),
                )
            },
        ) {
            content()
        }
        Icon(
            Icons.Default.TouchApp,
            contentDescription = null,
            tint = AppColors.Gray.c800,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 14.dp)
                .graphicsLayer {
                    // Down as each ring starts, lifting off as it spreads.
                    val pressed = (1f - pulse.value / 0.3f).coerceIn(0f, 1f)
                    val scale = 1f - 0.15f * pressed
                    // The hand points in from the side the reading ends on.
                    scaleX = if (rtl) -scale else scale
                    scaleY = scale
                }
                .size(30.dp),
        )
    }
}

/**
 * A button that looks like the screens' and does nothing: filled for the one that moves the work
 * on, outlined for the rest.
 */
@Composable
private fun MockButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = CircleShape,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (filled) colors.onPrimary else colors.primary
    Row(
        modifier
            .heightIn(min = 40.dp)
            .then(
                if (filled) {
                    Modifier.background(colors.primary, shape)
                } else {
                    Modifier.background(AppColors.Surface, shape).border(1.dp, colors.outline, shape)
                },
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One choice in a list of them, as the sheets draw it: the chosen one outlined and ticked. */
@Composable
private fun MockOption(label: String, selected: Boolean, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) colors.primaryContainer else AppColors.Surface, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp)) }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** A posting as the board cuts it: when it closes, the lane, what it needs, the price and the button. */
@Composable
private fun MockPosting() {
    val lang = AppLocale.current()
    Column(Modifier.fillMaxWidth().mockCard().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The pickup time, which is the order's own.
            Box(Modifier.weight(1f)) { Placeholder(Modifier.width(72.dp)) }
            TagChip(stringResource(Res.string.postings_closes_in, stringResource(Res.string.dur_hours, 3)))
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            City(stringResource(Res.string.guide_sample_origin))
            MockLane(
                distance = stringResource(Res.string.postings_distance, 950.toGroupedString(lang)),
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs),
            )
            City(stringResource(Res.string.guide_sample_destination))
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Spec(stringResource(Res.string.equip_size_heavy), Icons.Outlined.LocalShipping)
            Spec(stringResource(Res.string.equip_body_box), Icons.Outlined.Inventory2)
        }
        Spacer(Modifier.height(Spacing.sm))
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(
                color = AppColors.Gray.c300,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Price(lang, Modifier.weight(1f))
            MockButton(stringResource(Res.string.postings_bid), filled = true, shape = RoundedCornerShape(Radius.md))
        }
    }
}

@Composable
private fun City(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 96.dp),
    )
}

@Composable
private fun Price(lang: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(Res.string.postings_price),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${2500.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The board card's lane between the two cities, with the distance over the truck. */
@Composable
private fun MockLane(distance: String, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            distance,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawLane(rtl, ink, line = AppColors.Gray.c300, dot = 3.5.dp, width = 2.dp, gap = 5.dp)
            }
            Icon(
                Icons.Outlined.LocalShipping,
                contentDescription = null,
                tint = AppColors.Accent.c800,
                modifier = Modifier
                    .background(AppColors.Surface)
                    .padding(horizontal = 4.dp)
                    .size(18.dp)
                    .graphicsLayer { scaleX = if (rtl) -1f else 1f },
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun Spec(text: String, icon: ImageVector) {
    Row(
        Modifier
            .background(AppColors.Gray.c100, RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Gray.c700.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = AppColors.Gray.c700)
    }
}

/**
 * A truck driving the board's lane from the open circle to the filled one, over and over. It sets
 * off from wherever reading starts, which is the right in Arabic.
 */
@Composable
private fun DrivingTruck() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink = MaterialTheme.colorScheme.onSurface
    val progress = rememberInfiniteTransition(label = "truck").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3600, easing = FastOutSlowInEasing)),
        label = "progress",
    )
    BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp)) {
        val travel = maxWidth - TRUCK_SIZE
        Canvas(Modifier.fillMaxWidth().height(16.dp).align(Alignment.BottomCenter)) {
            drawLane(rtl, ink, line = AppColors.Primary.c300, dot = 6.dp, width = 3.dp, gap = 8.dp)
        }
        Icon(
            Icons.Default.LocalShipping,
            contentDescription = null,
            tint = AppColors.Accent.c800,
            modifier = Modifier
                .align(Alignment.BottomStart)
                // The offset follows the layout direction, so this runs from start to end in both.
                .offset { IntOffset((travel.toPx() * progress.value).roundToInt(), 0) }
                .graphicsLayer {
                    val p = progress.value
                    // In from nothing and out the same way, so starting over is not a jump back.
                    alpha = minOf(1f, p / 0.1f, (1f - p) / 0.1f)
                    scaleX = if (rtl) -1f else 1f
                }
                .size(TRUCK_SIZE),
        )
    }
}

/**
 * The board's lane: a dotted line from an open circle where the load is to a filled one where it
 * goes, the origin on the side reading starts from.
 */
private fun DrawScope.drawLane(rtl: Boolean, ink: Color, line: Color, dot: Dp, width: Dp, gap: Dp) {
    val y = size.height / 2
    val radius = dot.toPx()
    val stroke = 1.5.dp.toPx()
    val left = radius + stroke / 2
    val right = size.width - radius - stroke / 2
    val (from, to) = if (rtl) right to left else left to right
    drawLine(
        color = line,
        start = Offset(left + radius + 4.dp.toPx(), y),
        end = Offset(right - radius - 4.dp.toPx(), y),
        strokeWidth = width.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, gap.toPx())),
    )
    drawCircle(ink, radius, Offset(from, y), style = Stroke(stroke))
    drawCircle(ink, radius, Offset(to, y))
}

/** The spinner a pull on the board brings down, coming down and turning as if being pulled. */
@Composable
private fun PullToRefresh() {
    val pull = rememberInfiniteTransition(label = "pull").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pull",
    )
    Box(
        Modifier
            .graphicsLayer {
                translationY = 6.dp.toPx() * pull.value
                rotationZ = 180f * pull.value
            }
            .size(32.dp)
            .shadow(3.dp, CircleShape)
            .background(AppColors.Surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A text cursor, blinking after the price as if the driver had just typed it. */
@Composable
private fun Caret() {
    val blink = rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 1000
            },
        ),
        label = "blink",
    )
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .padding(start = 2.dp)
            .width(2.dp)
            .height(20.dp)
            .graphicsLayer { alpha = blink.value }
            .background(color),
    )
}

/** A stop's place on the rail: the stop to work now filled in, the rest left grey. */
@Composable
private fun StopNumber(number: String, current: Boolean) {
    Box(
        Modifier
            .size(28.dp)
            .background(if (current) AppColors.Primary.c700 else AppColors.Surface, CircleShape)
            .then(if (current) Modifier else Modifier.border(1.dp, AppColors.Gray.c300, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (current) AppColors.Gray.c50 else AppColors.Gray.c700,
        )
    }
}

/**
 * The camera, pointed at the load. It flashes now and then, as a photo being taken.
 */
@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    val flash = rememberInfiniteTransition(label = "flash").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2800
                0f at 0
                0f at 2000
                0.85f at 2080
                0f at 2500
                0f at 2800
            },
        ),
        label = "flash",
    )
    Box(
        modifier
            .aspectRatio(0.8f)
            .background(AppColors.Gray.c800, RoundedCornerShape(Radius.lg))
            .drawWithContent {
                drawContent()
                // The frame's four corners, the way a camera marks what it will take.
                val inset = 12.dp.toPx()
                val arm = 14.dp.toPx()
                val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                val w = size.width
                val h = size.height
                listOf(
                    Offset(inset, inset) to Offset(1f, 1f),
                    Offset(w - inset, inset) to Offset(-1f, 1f),
                    Offset(inset, h - inset) to Offset(1f, -1f),
                    Offset(w - inset, h - inset) to Offset(-1f, -1f),
                ).forEach { (corner, dir) ->
                    val bracket = Path().apply {
                        moveTo(corner.x, corner.y + arm * dir.y)
                        lineTo(corner.x, corner.y)
                        lineTo(corner.x + arm * dir.x, corner.y)
                    }
                    drawPath(bracket, Color.White, style = stroke)
                }
                drawRoundRect(
                    Color.White,
                    cornerRadius = CornerRadius(Radius.lg.toPx()),
                    alpha = flash.value,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Inventory2,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(44.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.sm)
                .size(20.dp)
                .border(3.dp, Color.White, CircleShape),
        )
    }
}

/**
 * The delivery note: its printed lines, the receiver's signature writing itself along the
 * bottom, and a tick once it is photographed.
 */
@Composable
private fun DeliveryNote(modifier: Modifier = Modifier) {
    val ink = AppColors.Blue.c700
    val signed = rememberInfiniteTransition(label = "sign").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2800
                0f at 0
                1f at 1600
                1f at 2800
            },
        ),
        label = "signed",
    )
    // Reused every frame: the signature is cut to length from the whole, not rebuilt.
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }
    Box(modifier.aspectRatio(0.8f)) {
        Column(
            Modifier
                .fillMaxSize()
                .mockCard(Radius.md)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Placeholder(Modifier.fillMaxWidth(0.55f), height = 8.dp)
            Placeholder(Modifier.fillMaxWidth(), height = 6.dp)
            Placeholder(Modifier.fillMaxWidth(0.85f), height = 6.dp)
            Placeholder(Modifier.fillMaxWidth(0.7f), height = 6.dp)
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val w = size.width
                val h = size.height
                val whole = Path().apply {
                    moveTo(w * 0.05f, h * 0.7f)
                    cubicTo(w * 0.15f, h * 0.05f, w * 0.25f, h * 0.05f, w * 0.28f, h * 0.6f)
                    cubicTo(w * 0.32f, h * 1.0f, w * 0.4f, h * 0.2f, w * 0.48f, h * 0.45f)
                    cubicTo(w * 0.56f, h * 0.7f, w * 0.62f, h * 0.1f, w * 0.7f, h * 0.4f)
                    cubicTo(w * 0.78f, h * 0.7f, w * 0.86f, h * 0.55f, w * 0.95f, h * 0.35f)
                }
                measure.setPath(whole, forceClosed = false)
                segment.reset()
                measure.getSegment(0f, measure.length * signed.value, segment, startWithMoveTo = true)
                drawPath(segment, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Placeholder(Modifier.fillMaxWidth(), height = 2.dp)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(22.dp)
                .background(AppColors.Green.c500, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, label: StringResource, highlighted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlighted) FontWeight.Bold else null,
            color = if (highlighted) colors.primary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.Gray.c400,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Text the picture has no need to spell out: an address, a reference, a time. */
@Composable
private fun Placeholder(modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(modifier.height(height).background(AppColors.Gray.c100, CircleShape))
}

/** White, rounded and lifted off the picture's ground, like a card on the screen it stands for. */
private fun Modifier.mockCard(radius: Dp = Radius.lg): Modifier {
    val shape = RoundedCornerShape(radius)
    // Not clipped, so a finger reaching past the edge of what it taps is not cut off.
    return shadow(2.dp, shape, clip = false).background(AppColors.Surface, shape)
}

private val TRUCK_SIZE = 56.dp
