package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.trips_empty_body
import co.sirdab.driver.shared.core.ui.generated.resources.trips_empty_title
import co.sirdab.driver.shared.core.ui.generated.resources.trips_in_city
import co.sirdab.driver.shared.core.ui.generated.resources.trips_load_more
import co.sirdab.driver.shared.core.ui.generated.resources.trips_retry
import co.sirdab.driver.shared.core.ui.generated.resources.trips_stops_count
import co.sirdab.driver.shared.core.ui.generated.resources.trips_title
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripsScreen(
    onOpenTrip: (String) -> Unit,
    viewModel: DriverTripsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(Res.string.trips_title),
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

            // Nothing on screen and a failure behind it: the error is the whole page, with the
            // only useful action on it.
            state.isEmpty -> EmptyOrError(
                // A driver working independently is refused every trip read. That is not a fault
                // to report to them — there is simply nothing assigned yet.
                message = state.errorMessage
                    .takeIf { state.errorReason != AppErrorReason.NOT_PROVISIONED },
                onRetry = { viewModel.refresh() },
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.trips, key = { it.id }) { trip ->
                    TripCard(trip = trip, onClick = { onOpenTrip(trip.id) })
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

                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                // A failed page load keeps the rows already on screen and puts the retry under
                // them, rather than throwing the list away.
                state.errorMessage?.let { message ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(Spacing.sm)) {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            OutlinedButton(onClick = viewModel::retry) {
                                Text(stringResource(Res.string.trips_retry))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TripCard(trip: DriverTrip, onClick: () -> Unit) {
    val lang = AppLocale.current()

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trip.reference,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TagChip(stringResource(trip.status.label()), tone = trip.status.tone())
            }

            Spacer(Modifier.height(Spacing.xs))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TagChip(stringResource(Res.string.trips_stops_count, trip.stopCount))
                if (trip.inCity) {
                    TagChip(stringResource(Res.string.trips_in_city), tone = ChipTone.PRIMARY)
                }
            }

            trip.scheduledAtMillis?.let { at ->
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    formatStopTime(at, lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyOrError(message: String?, onRetry: () -> Unit) {
    Box(
        // Scrollable so the pull gesture has something to grab even when there
        // is nothing to show, which is the moment a driver most wants to pull.
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message ?: stringResource(Res.string.trips_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = if (message != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(Spacing.xs))
            if (message == null) {
                Text(
                    stringResource(Res.string.trips_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(Res.string.trips_retry))
                }
            }
        }
    }
}
