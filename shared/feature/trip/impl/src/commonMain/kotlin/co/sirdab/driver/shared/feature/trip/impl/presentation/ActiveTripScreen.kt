package co.sirdab.driver.shared.feature.trip.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.TripStatus
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.MapCanvas
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.act_arrived_dropoff
import co.sirdab.driver.shared.core.ui.generated.resources.act_arrived_pickup
import co.sirdab.driver.shared.core.ui.generated.resources.act_capture_pod
import co.sirdab.driver.shared.core.ui.generated.resources.act_depart
import co.sirdab.driver.shared.core.ui.generated.resources.act_navigate_pickup
import co.sirdab.driver.shared.core.ui.generated.resources.act_start_loading
import co.sirdab.driver.shared.core.ui.generated.resources.act_start_unloading
import co.sirdab.driver.shared.core.ui.generated.resources.st_assigned
import co.sirdab.driver.shared.core.ui.generated.resources.st_at_dropoff
import co.sirdab.driver.shared.core.ui.generated.resources.st_at_pickup
import co.sirdab.driver.shared.core.ui.generated.resources.st_completed
import co.sirdab.driver.shared.core.ui.generated.resources.st_enroute_dropoff
import co.sirdab.driver.shared.core.ui.generated.resources.st_enroute_pickup
import co.sirdab.driver.shared.core.ui.generated.resources.st_loading
import co.sirdab.driver.shared.core.ui.generated.resources.st_pod_pending
import co.sirdab.driver.shared.core.ui.generated.resources.st_unloading
import co.sirdab.driver.shared.core.ui.generated.resources.trip_call_shipper
import co.sirdab.driver.shared.core.ui.generated.resources.trip_detention
import co.sirdab.driver.shared.core.ui.generated.resources.trip_detention_note
import co.sirdab.driver.shared.core.ui.generated.resources.trip_eta
import co.sirdab.driver.shared.core.ui.generated.resources.trip_min
import co.sirdab.driver.shared.core.ui.generated.resources.trip_no_active_body
import co.sirdab.driver.shared.core.ui.generated.resources.trip_no_active_title
import co.sirdab.driver.shared.core.ui.generated.resources.trip_title
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ActiveTripScreen(
    onCapturePod: (String) -> Unit,
    viewModel: ActiveTripViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val trip = state.trip

    if (trip == null) {
        Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.trip_no_active_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(Res.string.trip_no_active_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val driving = trip.status == TripStatus.EN_ROUTE_TO_PICKUP || trip.status == TripStatus.EN_ROUTE_TO_DROPOFF

    Column(Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(stringResource(Res.string.trip_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        state.load?.let {
            Text("${it.originName} → ${it.destinationName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))

        Surface(shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth().height(240.dp)) {
            state.bounds?.let { bounds ->
                MapCanvas(
                    routePoints = state.routePoints,
                    bounds = bounds,
                    currentPosition = trip.currentPosition ?: state.routePoints.firstOrNull(),
                    geofences = listOfNotNull(state.routePoints.firstOrNull(), state.routePoints.lastOrNull()),
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(trip.status.statusLabel()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Primary.c700)
        Spacer(Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { (trip.status.ordinal.toFloat() / (TripStatus.entries.size - 1)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (driving) {
                InfoTile(stringResource(Res.string.trip_eta), "${trip.etaMinutes ?: 0} ${stringResource(Res.string.trip_min)}", Modifier.weight(1f))
            }
            if (state.detentionSeconds > 0) {
                val mm = state.detentionSeconds / 60
                val ss = state.detentionSeconds % 60
                InfoTile(
                    stringResource(Res.string.trip_detention),
                    "${mm.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}",
                    Modifier.weight(1f),
                    warning = true,
                )
            }
        }
        if (state.detentionSeconds > 0) {
            Text(stringResource(Res.string.trip_detention_note), style = MaterialTheme.typography.labelMedium, color = AppColors.Amber.c800)
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Phone, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text("  ${stringResource(Res.string.trip_call_shipper)}")
        }
        Spacer(Modifier.height(Spacing.xs))

        when {
            driving -> {
                Surface(shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(trip.status.statusLabel()))
                    }
                }
            }
            trip.status == TripStatus.POD_PENDING -> {
                DriverButton(
                    text = stringResource(Res.string.act_capture_pod),
                    onClick = { onCapturePod(trip.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                trip.status.actionLabel()?.let { actionRes ->
                    DriverButton(
                        text = stringResource(actionRes),
                        onClick = viewModel::advance,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (warning) AppColors.Amber.c50 else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (warning) AppColors.Amber.c800 else MaterialTheme.colorScheme.onSurface)
        }
    }
}

fun TripStatus.statusLabel(): StringResource = when (this) {
    TripStatus.ASSIGNED -> Res.string.st_assigned
    TripStatus.EN_ROUTE_TO_PICKUP -> Res.string.st_enroute_pickup
    TripStatus.AT_PICKUP -> Res.string.st_at_pickup
    TripStatus.LOADING -> Res.string.st_loading
    TripStatus.EN_ROUTE_TO_DROPOFF -> Res.string.st_enroute_dropoff
    TripStatus.AT_DROPOFF -> Res.string.st_at_dropoff
    TripStatus.UNLOADING -> Res.string.st_unloading
    TripStatus.POD_PENDING -> Res.string.st_pod_pending
    TripStatus.COMPLETED -> Res.string.st_completed
}

private fun TripStatus.actionLabel(): StringResource? = when (this) {
    TripStatus.ASSIGNED -> Res.string.act_navigate_pickup
    TripStatus.AT_PICKUP -> Res.string.act_start_loading
    TripStatus.LOADING -> Res.string.act_depart
    TripStatus.AT_DROPOFF -> Res.string.act_start_unloading
    TripStatus.UNLOADING -> Res.string.act_capture_pod
    TripStatus.EN_ROUTE_TO_PICKUP -> Res.string.act_arrived_pickup
    TripStatus.EN_ROUTE_TO_DROPOFF -> Res.string.act_arrived_dropoff
    else -> null
}
