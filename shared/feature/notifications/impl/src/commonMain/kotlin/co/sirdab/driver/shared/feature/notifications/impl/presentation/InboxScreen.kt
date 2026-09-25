package co.sirdab.driver.shared.feature.notifications.impl.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.core.model.NotificationKind
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.notif_inbox_empty
import co.sirdab.driver.shared.core.ui.generated.resources.notif_inbox_title
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    viewModel: InboxViewModel = koinViewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lang = Locale.current.language

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.notif_inbox_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (notifications.isEmpty()) {
                Box(
                    // Scrollable so the pull gesture has something to grab on an empty inbox,
                    // which is the state a driver is most likely to pull on.
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.notif_inbox_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(notifications, key = { it.id }) { n ->
                        NotificationRow(n, lang) { viewModel.markRead(n.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, lang: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (n.read) MaterialTheme.colorScheme.surface else AppColors.Primary.c50,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                Modifier.size(40.dp).background(AppColors.Primary.c100, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(n.kind.icon(), contentDescription = null, tint = AppColors.Primary.c700, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    if (lang == "ar") n.titleAr else n.titleEn,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(if (lang == "ar") n.bodyAr else n.bodyEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!n.read) {
                Box(Modifier.size(10.dp).background(AppColors.Accent.c500, CircleShape))
            }
        }
    }
}

private fun NotificationKind.icon(): ImageVector = when (this) {
    NotificationKind.OUTBID -> Icons.Default.TrendingDown
    NotificationKind.AWARDED -> Icons.Default.EmojiEvents
    NotificationKind.COUNTERED -> Icons.Default.SwapHoriz
    NotificationKind.REJECTED -> Icons.Default.Info
    NotificationKind.GEOFENCE -> Icons.Default.LocationOn
    NotificationKind.DETENTION -> Icons.Default.Timer
    NotificationKind.BACKHAUL -> Icons.Default.Loop
    NotificationKind.PAYOUT -> Icons.Default.AttachMoney
    NotificationKind.DOCUMENT -> Icons.Default.Description
    NotificationKind.SYSTEM -> Icons.Default.Info
}
