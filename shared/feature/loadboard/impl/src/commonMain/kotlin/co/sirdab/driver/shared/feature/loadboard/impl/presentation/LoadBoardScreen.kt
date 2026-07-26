package co.sirdab.driver.shared.feature.loadboard.impl.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.filter_all
import co.sirdab.driver.shared.core.ui.generated.resources.filter_title
import co.sirdab.driver.shared.core.ui.generated.resources.lb_away
import co.sirdab.driver.shared.core.ui.generated.resources.lb_empty
import co.sirdab.driver.shared.core.ui.generated.resources.lb_instant_book
import co.sirdab.driver.shared.core.ui.generated.resources.lb_suggested
import co.sirdab.driver.shared.core.ui.generated.resources.lb_title
import co.sirdab.driver.shared.core.ui.generated.resources.unit_km
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.toGroupedString
import co.sirdab.driver.shared.core.util.toLocalizedString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadBoardScreen(
    onOpenLoad: (String) -> Unit,
    onOpenInbox: () -> Unit,
    viewModel: LoadBoardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val unread by viewModel.unreadCount.collectAsState()
    var showFilter by remember { mutableStateOf(false) }
    var showDemoPanel by remember { mutableStateOf(false) }
    var logoTaps by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.lb_title),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            logoTaps++
                            if (logoTaps >= 5) { logoTaps = 0; showDemoPanel = true }
                        },
                    )
                },
                actions = {
                    IconButton(onClick = onOpenInbox) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                        }
                    }
                    TextButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(state.filterVehicle?.let { stringResource(it.labelRes()) } ?: stringResource(Res.string.filter_all))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.loads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.lb_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.loads, key = { it.id }) { load ->
                        LoadCard(load = load, onClick = { onOpenLoad(load.id) })
                    }
                }
            }
        }
    }

    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }) {
            Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                Text(stringResource(Res.string.filter_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Spacing.md))
                FilterOptionRow(stringResource(Res.string.filter_all), state.filterVehicle == null) {
                    viewModel.setFilter(null); showFilter = false
                }
                VehicleType.entries.forEach { vt ->
                    FilterOptionRow(stringResource(vt.labelRes()), state.filterVehicle == vt) {
                        viewModel.setFilter(vt); showFilter = false
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (showDemoPanel) {
        DemoControlPanel(onDismiss = { showDemoPanel = false })
    }
}

@Composable
private fun FilterOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.sm),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadCard(load: Load, onClick: () -> Unit) {
    val lang = Locale.current.language
    val rate = load.fixedRateSar ?: load.suggestedRateSar
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${load.originName} → ${load.destinationName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${load.distanceKm.toLocalizedString(lang)} ${stringResource(Res.string.unit_km)} • " +
                            "${load.distanceFromDriverKm.toLocalizedString(lang)} ${stringResource(Res.string.unit_km)} ${stringResource(Res.string.lb_away)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${rate.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary.c700,
                    )
                    if (!load.instantBook) {
                        Text(
                            stringResource(Res.string.lb_suggested),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                TagChip(stringResource(load.requiredVehicle.labelRes()), tone = ChipTone.PRIMARY)
                TagChip(stringResource(load.cargoType.labelRes()))
                if (load.instantBook) TagChip(stringResource(Res.string.lb_instant_book), tone = ChipTone.SUCCESS)
                load.handlingFlags.forEach { TagChip(stringResource(it.labelRes()), tone = ChipTone.WARNING) }
            }
        }
    }
}
