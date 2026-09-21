package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.bid_sent
import co.sirdab.driver.shared.core.ui.generated.resources.dur_days
import co.sirdab.driver.shared.core.ui.generated.resources.dur_hours
import co.sirdab.driver.shared.core.ui.generated.resources.dur_minutes
import co.sirdab.driver.shared.core.ui.generated.resources.postings_accept
import co.sirdab.driver.shared.core.ui.generated.resources.postings_bid
import co.sirdab.driver.shared.core.ui.generated.resources.postings_closed
import co.sirdab.driver.shared.core.ui.generated.resources.postings_closes_in
import co.sirdab.driver.shared.core.ui.generated.resources.postings_empty_body
import co.sirdab.driver.shared.core.ui.generated.resources.postings_empty_title
import co.sirdab.driver.shared.core.ui.generated.resources.postings_open_price
import co.sirdab.driver.shared.core.ui.generated.resources.postings_title
import co.sirdab.driver.shared.core.ui.generated.resources.trips_load_more
import co.sirdab.driver.shared.core.ui.generated.resources.trips_retry
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun DriverPostingsScreen(
    viewModel: DriverPostingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lang = AppLocale.current()
    val now = Clock.System.now().toEpochMilliseconds()

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(Res.string.postings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(Spacing.md),
        )

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
                    Text(
                        state.errorMessage ?: stringResource(Res.string.postings_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = if (state.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    if (state.errorMessage == null) {
                        Text(
                            stringResource(Res.string.postings_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        OutlinedButton(onClick = { viewModel.refresh() }) {
                            Text(stringResource(Res.string.trips_retry))
                        }
                    }
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.bidSent) {
                    item {
                        Text(
                            stringResource(Res.string.bid_sent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Green.c600,
                        )
                    }
                }

                items(state.postings, key = { it.id }) { posting ->
                    PostingCard(
                        posting = posting,
                        existingBid = state.bidFor(posting.id),
                        lang = lang,
                        nowMillis = now,
                        onBid = { viewModel.openBid(posting) },
                    )
                }

                if (state.canLoadMore) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(Res.string.trips_load_more))
                        }
                    }
                }
            }
        }
        }
    }

    state.bidding?.let { posting ->
        PlaceBidSheet(
            posting = posting,
            trucks = state.trucks,
            isSubmitting = state.isSubmitting,
            errorMessage = state.errorMessage,
            onDismiss = viewModel::dismissBid,
            onSubmit = { amountSar, truckId, note ->
                viewModel.submitBid(posting.id, amountSar, truckId, note)
            },
        )
    }
}

@Composable
private fun PostingCard(
    posting: DriverPosting,
    existingBid: DriverBid?,
    lang: String,
    nowMillis: Long,
    onBid: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${posting.origin.city ?: posting.origin.label} → " +
                        "${posting.destination.city ?: posting.destination.label}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Countdown(posting.biddingClosesAtMillis, nowMillis)
            }

            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TagChip(stringResource(posting.truckSize.labelRes()))
                TagChip(stringResource(posting.truckType.labelRes()), tone = ChipTone.PRIMARY)
            }

            Spacer(Modifier.height(Spacing.sm))
            val rate = posting.targetRate
            if (rate != null) {
                Text(
                    "${rate.display(lang)} ${stringResource(Res.string.unit_sar)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    stringResource(Res.string.postings_open_price),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            if (existingBid != null) {
                // One bid per carrier, so the outcome replaces the action.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TagChip(
                        stringResource(existingBid.status.label()),
                        tone = existingBid.status.tone(),
                    )
                    TagChip("${existingBid.amount.display(lang)} ${stringResource(Res.string.unit_sar)}")
                }
            } else {
                DriverButton(
                    text = if (rate != null) {
                        stringResource(
                            Res.string.postings_accept,
                            "${rate.display(lang)} ${stringResource(Res.string.unit_sar)}",
                        )
                    } else {
                        stringResource(Res.string.postings_bid)
                    },
                    onClick = onBid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
