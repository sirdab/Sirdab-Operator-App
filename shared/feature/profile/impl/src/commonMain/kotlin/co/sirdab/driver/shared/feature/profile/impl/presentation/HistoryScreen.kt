package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.history_completed_trips
import co.sirdab.driver.shared.core.ui.generated.resources.history_earnings
import co.sirdab.driver.shared.core.ui.generated.resources.history_empty
import co.sirdab.driver.shared.core.ui.generated.resources.history_title
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.localizeDigits
import co.sirdab.driver.shared.core.util.toGroupedString
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lang = Locale.current.language

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.history_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            )
        },
    ) { padding ->
        // One list rather than a column with a list inside it, so the pull gesture works over the
        // chart as well as the rows — a driver pulls wherever their thumb happens to be.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item {
                    Text(stringResource(Res.string.history_earnings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        EarningsBarChart(state.bars, lang, modifier = Modifier.fillMaxWidth().height(200.dp).padding(Spacing.md))
                    }
                }
                item {
                    Spacer(Modifier.height(Spacing.md))
                    Text(stringResource(Res.string.history_completed_trips), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (state.earnings.isEmpty()) {
                    item {
                        Text(stringResource(Res.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.earnings, key = { it.id }) { txn ->
                    Surface(shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(Spacing.md).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (lang == "ar") txn.descriptionAr else txn.descriptionEn, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(2.dp))
                                // Every trip is paid on delivery, so there is no
                                // settlement state to show. The date is what the
                                // operator actually wants to scan for.
                                Text(
                                    formatEarnedOn(txn.earnedAtMillis, lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("${txn.amountSar.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppColors.Green.c600)
                        }
                    }
                }
            }
        }
    }
}

/** Hand-rolled Compose Canvas bar chart — no chart library (plan §2). */
@Composable
private fun EarningsBarChart(bars: List<WeekBar>, lang: String, modifier: Modifier = Modifier) {
    val maxVal = (bars.maxOfOrNull { it.amountSar } ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val gap = size.width * 0.03f
        val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
        val labelSpace = 0f
        val chartHeight = size.height - labelSpace
        bars.forEachIndexed { i, bar ->
            val h = chartHeight * (bar.amountSar.toFloat() / maxVal)
            val x = i * (barWidth + gap)
            val y = chartHeight - h
            // track
            drawRoundedBar(x, 0f, barWidth, chartHeight, AppColors.Primary.c50)
            // value
            drawRoundedBar(x, y, barWidth, h, AppColors.Primary.c500)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedBar(
    x: Float, y: Float, w: Float, h: Float, color: androidx.compose.ui.graphics.Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(w, h.coerceAtLeast(2f)),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
    )
}

@OptIn(ExperimentalTime::class)
private fun formatEarnedOn(millis: Long, lang: String): String {
    val at = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${at.dayOfMonth}/${at.monthNumber}/${at.year}".localizeDigits(lang)
}
