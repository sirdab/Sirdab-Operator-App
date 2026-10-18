package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.bid_sent
import co.sirdab.driver.shared.core.ui.generated.resources.bid_updated
import co.sirdab.driver.shared.core.ui.generated.resources.coming_soon
import co.sirdab.driver.shared.core.ui.generated.resources.postings_coming_soon_body
import co.sirdab.driver.shared.core.ui.generated.resources.postings_empty_body
import co.sirdab.driver.shared.core.ui.generated.resources.postings_count
import co.sirdab.driver.shared.core.ui.generated.resources.postings_empty_title
import co.sirdab.driver.shared.core.ui.generated.resources.postings_title
import co.sirdab.driver.shared.core.ui.generated.resources.trips_load_more
import co.sirdab.driver.shared.core.ui.generated.resources.trips_retry
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.toLocalizedString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun DriverPostingsScreen(
    viewModel: DriverPostingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lang = AppLocale.current()
    // Ticks, so the countdowns move. Read once per composition, "closes in 5 min" stayed at 5
    // until something unrelated recomposed the board.
    val now by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(COUNTDOWN_TICK_MS)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs)) {
            if (state.postings.isNotEmpty()) {
                // "50+" while more pages wait: the board only knows what it has read so far.
                val count = state.postings.size
                val shown = count.toLocalizedString(lang) + if (state.nextCursor != null) "+" else ""
                Text(
                    pluralStringResource(Res.plurals.postings_count, count, shown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(Res.string.postings_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            // Up here rather than as the list's first row: the list keeps its place when a row is
            // added above it, so a notice there would land off screen.
            state.notice?.let { notice ->
                val (text, colour) = when (notice) {
                    BidNotice.SENT -> Res.string.bid_sent to AppColors.Green.c600
                    BidNotice.UPDATED -> Res.string.bid_updated to AppColors.Green.c600
                    BidNotice.DECIDED -> AppErrorReason.BID_DECIDED.labelRes() to AppColors.Amber.c800
                    BidNotice.CLOSED -> AppErrorReason.BIDDING_CLOSED.labelRes() to AppColors.Amber.c800
                }
                Spacer(Modifier.height(Spacing.xxs))
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = colour)
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(pulled = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.isEmpty -> Box(
                // Scrollable so the pull gesture has something to grab even
                // when there is nothing to show.
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Three states share this space, and only one of them is a fault: nothing
                    // posted yet, the board not built yet, and a call that actually failed.
                    val failure = state.errorMessage
                        .takeIf { !state.isComingSoon && !state.isBoardOutOfScope }
                    val title = when {
                        state.isComingSoon -> stringResource(Res.string.coming_soon)
                        failure != null -> failure
                        else -> stringResource(Res.string.postings_empty_title)
                    }
                    val isFault = failure != null
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = if (isFault) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    when {
                        state.isComingSoon -> Text(
                            stringResource(Res.string.postings_coming_soon_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        isFault -> OutlinedButton(onClick = { viewModel.refresh() }) {
                            Text(stringResource(Res.string.trips_retry))
                        }
                        state.isCompanyDriver -> Text(
                            stringResource(AppErrorReason.NOT_A_BIDDER.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        else -> Text(
                            stringResource(Res.string.postings_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.postings, key = { it.id }) { posting ->
                    PostingCard(
                        posting = posting,
                        bid = state.bidFor(posting.id),
                        lang = lang,
                        nowMillis = now,
                        onBid = { viewModel.openBid(posting) },
                    )
                }

                if (state.canLoadMore && !state.failedLoadMore) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.trips_load_more))
                        }
                    }
                }

                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                // A failed page keeps the postings already on screen and puts the retry under them.
                if (state.failedLoadMore) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(Spacing.sm)) {
                            state.errorMessage?.let { message ->
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(Spacing.xs))
                            }
                            OutlinedButton(onClick = viewModel::loadMore) {
                                Text(stringResource(Res.string.trips_retry))
                            }
                        }
                    }
                }
            }
        }
        }
    }

    state.bidding?.let { posting ->
        val editing = state.editing
        PlaceBidSheet(
            posting = posting,
            trucks = state.trucks,
            editing = editing,
            isSubmitting = state.isSubmitting,
            // The driver's language for a refusal the app has words for; the server's otherwise.
            errorMessage = state.bidErrorReason?.let { stringResource(it.labelRes()) } ?: state.errorMessage,
            onDismiss = viewModel::dismissBid,
            onSubmit = { amountSar, truckId, note ->
                viewModel.submitBid(posting.id, amountSar, truckId, note)
            },
            onUpdate = { amountCents -> editing?.let { viewModel.updateBid(it.id, amountCents) } },
        )
    }
}

/** The countdowns read in minutes, so a tick much finer than one would only cost recompositions. */
private const val COUNTDOWN_TICK_MS = 30_000L
