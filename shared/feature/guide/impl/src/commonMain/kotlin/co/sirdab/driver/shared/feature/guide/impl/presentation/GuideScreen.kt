package co.sirdab.driver.shared.feature.guide.impl.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.StepProgress
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.exc_report
import co.sirdab.driver.shared.core.ui.generated.resources.guide_bid_body
import co.sirdab.driver.shared.core.ui.generated.resources.guide_bid_tip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_bid_title
import co.sirdab.driver.shared.core.ui.generated.resources.guide_done
import co.sirdab.driver.shared.core.ui.generated.resources.guide_loads_body
import co.sirdab.driver.shared.core.ui.generated.resources.guide_loads_tip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_loads_title
import co.sirdab.driver.shared.core.ui.generated.resources.guide_next
import co.sirdab.driver.shared.core.ui.generated.resources.guide_problem_body
import co.sirdab.driver.shared.core.ui.generated.resources.guide_problem_tip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_problem_title
import co.sirdab.driver.shared.core.ui.generated.resources.guide_profile_body
import co.sirdab.driver.shared.core.ui.generated.resources.guide_profile_tip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_profile_title
import co.sirdab.driver.shared.core.ui.generated.resources.guide_proof_body
import co.sirdab.driver.shared.core.ui.generated.resources.guide_proof_tip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_proof_title
import co.sirdab.driver.shared.core.ui.generated.resources.guide_skip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_stops_body
import co.sirdab.driver.shared.core.ui.generated.resources.guide_stops_tip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_stops_title
import co.sirdab.driver.shared.core.ui.generated.resources.guide_trips_body
import co.sirdab.driver.shared.core.ui.generated.resources.guide_trips_tip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_trips_title
import co.sirdab.driver.shared.core.ui.generated.resources.guide_welcome_body
import co.sirdab.driver.shared.core.ui.generated.resources.guide_welcome_tip
import co.sirdab.driver.shared.core.ui.generated.resources.guide_welcome_title
import co.sirdab.driver.shared.core.ui.generated.resources.postings_accept
import co.sirdab.driver.shared.core.ui.generated.resources.postings_bid
import co.sirdab.driver.shared.core.ui.generated.resources.postings_edit_bid
import co.sirdab.driver.shared.core.ui.generated.resources.sact_arrive
import co.sirdab.driver.shared.core.ui.generated.resources.sact_deliver
import co.sirdab.driver.shared.core.ui.generated.resources.sact_depart
import co.sirdab.driver.shared.core.ui.generated.resources.sact_finish_loading
import co.sirdab.driver.shared.core.ui.generated.resources.stop_choose_photo
import co.sirdab.driver.shared.core.ui.generated.resources.stop_take_photo
import co.sirdab.driver.shared.core.ui.generated.resources.tab_loads
import co.sirdab.driver.shared.core.ui.generated.resources.tab_profile
import co.sirdab.driver.shared.core.ui.generated.resources.tab_trip
import co.sirdab.driver.shared.core.ui.generated.resources.trip_start
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_call
import co.sirdab.driver.shared.core.ui.generated.resources.trip_stop_navigate
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * One page of the guide: where to look, and what to tap once there.
 *
 * Each has a picture of the screen it describes, drawn in [GuideIllustration], and a [tip] under
 * its text: the one thing worth knowing about that screen that a driver would otherwise only find
 * out by getting it wrong.
 */
internal enum class GuidePage(
    val title: StringResource,
    val body: StringResource,
    val tip: StringResource,
    /**
     * The tabs and buttons [body] names, in its placeholders' order. Read from the screens' own
     * strings rather than written out again, so a button renamed there is renamed here.
     */
    val labels: List<StringResource> = emptyList(),
    /** The same, for [tip]. */
    val tipLabels: List<StringResource> = emptyList(),
    /** About the board, which a driver whose company bids for them does not have. */
    val bidding: Boolean = false,
) {
    WELCOME(Res.string.guide_welcome_title, Res.string.guide_welcome_body, Res.string.guide_welcome_tip),
    LOADS(
        Res.string.guide_loads_title,
        Res.string.guide_loads_body,
        Res.string.guide_loads_tip,
        labels = listOf(Res.string.tab_loads),
        bidding = true,
    ),
    BID(
        Res.string.guide_bid_title,
        Res.string.guide_bid_body,
        Res.string.guide_bid_tip,
        labels = listOf(Res.string.postings_bid, Res.string.postings_accept),
        tipLabels = listOf(Res.string.postings_edit_bid),
        bidding = true,
    ),
    TRIPS(
        Res.string.guide_trips_title,
        Res.string.guide_trips_body,
        Res.string.guide_trips_tip,
        labels = listOf(Res.string.tab_trip, Res.string.trip_start),
    ),
    STOPS(
        Res.string.guide_stops_title,
        Res.string.guide_stops_body,
        Res.string.guide_stops_tip,
        labels = listOf(
            Res.string.sact_arrive,
            Res.string.sact_depart,
            Res.string.trip_stop_navigate,
            Res.string.trip_stop_call,
        ),
    ),
    PROOF(
        Res.string.guide_proof_title,
        Res.string.guide_proof_body,
        Res.string.guide_proof_tip,
        labels = listOf(Res.string.stop_take_photo, Res.string.sact_finish_loading, Res.string.sact_deliver),
        tipLabels = listOf(Res.string.stop_choose_photo),
    ),
    PROBLEM(
        Res.string.guide_problem_title,
        Res.string.guide_problem_body,
        Res.string.guide_problem_tip,
        labels = listOf(Res.string.exc_report),
    ),
    PROFILE(
        Res.string.guide_profile_title,
        Res.string.guide_profile_body,
        Res.string.guide_profile_tip,
        labels = listOf(Res.string.tab_profile),
    ),
}

/** The pages this driver gets, in the order they work: find a load, drive it, then the rest. */
internal fun guidePages(canBid: Boolean): List<GuidePage> =
    GuidePage.entries.filter { canBid || !it.bidding }

@Composable
fun GuideScreen(
    canBid: Boolean,
    onClose: () -> Unit,
    analytics: Analytics = koinInject(),
) {
    val pages = remember(canBid) { guidePages(canBid) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val onLastPage = pager.currentPage == pages.lastIndex
    // Set only by the last page's button: the one way out that means the driver read to the end.
    var finished by remember { mutableStateOf(false) }

    // On the way out, whichever way the driver took. The system back is one of them, and it pops
    // this entry without asking it first, so the buttons alone would miss it.
    DisposableEffect(Unit) {
        onDispose {
            analytics.track(
                AnalyticsEvent.guideClosed(
                    page = pager.currentPage + 1,
                    pageCount = pages.size,
                    finished = finished,
                ),
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        // Hidden on the last page rather than removed, so the pages do not jump when it goes. The
        // button below says the same thing there.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(
                onClick = onClose,
                enabled = !onLastPage,
                modifier = Modifier.alpha(if (onLastPage) 0f else 1f),
            ) {
                Text(stringResource(Res.string.guide_skip))
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            GuidePageContent(pages[index], canBid)
        }

        Spacer(Modifier.height(Spacing.md))
        StepProgress(current = pager.currentPage, total = pages.size)
        Spacer(Modifier.height(Spacing.lg))
        DriverButton(
            text = stringResource(if (onLastPage) Res.string.guide_done else Res.string.guide_next),
            onClick = {
                if (onLastPage) {
                    finished = true
                    onClose()
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GuidePageContent(page: GuidePage, canBid: Boolean) {
    val labels = page.labels.map { stringResource(it) }
    val tipLabels = page.tipLabels.map { stringResource(it) }

    // The words are measured first and the picture gets the room they leave, shrinking to fit it,
    // so a page fits an ordinary phone whole: a tip hidden below the fold is a tip nobody reads.
    // Only when the words alone fill the page, Urdu at the largest font size on a small phone,
    // does it scroll, with the picture at its smallest.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = constraints.maxHeight
        Layout(
            content = {
                GuideIllustration(page, canBid)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(page.title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(page.body, *labels.toTypedArray()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Tip(stringResource(page.tip, *tipLabels.toTypedArray()))
                }
            },
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) { (picture, words), constraints ->
            val width = constraints.maxWidth
            val gap = Spacing.lg.roundToPx()
            val placedWords = words.measure(Constraints(maxWidth = width))
            val room = (pageHeight - placedWords.height - gap)
                .coerceIn(PICTURE_MIN_HEIGHT.roundToPx(), PICTURE_MAX_HEIGHT.roundToPx())
            val placedPicture = picture.measure(Constraints.fixed(width, room))
            layout(width, maxOf(pageHeight, room + gap + placedWords.height)) {
                placedPicture.place(0, 0)
                placedWords.place(0, room + gap)
            }
        }
    }
}

private val PICTURE_MIN_HEIGHT = 160.dp
private val PICTURE_MAX_HEIGHT = 300.dp

@Composable
private fun Tip(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.md))
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = AppColors.Amber.c600,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
