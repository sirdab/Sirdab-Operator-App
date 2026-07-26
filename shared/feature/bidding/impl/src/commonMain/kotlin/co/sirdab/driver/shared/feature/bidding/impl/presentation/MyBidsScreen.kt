package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.BidStatus
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.mb_accept
import co.sirdab.driver.shared.core.ui.generated.resources.mb_counter_offer
import co.sirdab.driver.shared.core.ui.generated.resources.mb_countered
import co.sirdab.driver.shared.core.ui.generated.resources.mb_empty
import co.sirdab.driver.shared.core.ui.generated.resources.mb_lost
import co.sirdab.driver.shared.core.ui.generated.resources.mb_pending
import co.sirdab.driver.shared.core.ui.generated.resources.mb_reject
import co.sirdab.driver.shared.core.ui.generated.resources.mb_title
import co.sirdab.driver.shared.core.ui.generated.resources.mb_withdrawn
import co.sirdab.driver.shared.core.ui.generated.resources.mb_won
import co.sirdab.driver.shared.core.ui.generated.resources.mb_your_bid
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.toGroupedString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBidsScreen(
    viewModel: MyBidsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lang = Locale.current.language

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.mb_title)) }) },
    ) { padding ->
        if (state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.mb_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(state.rows, key = { it.bid.id }) { row ->
                BidRow(
                    route = row.load?.let { "${it.originName} → ${it.destinationName}" } ?: row.bid.loadId,
                    amountSar = row.bid.amountSar,
                    status = row.bid.status,
                    counterSar = row.bid.counterAmountSar,
                    lang = lang,
                    onAccept = { viewModel.accept(row.bid.id) },
                    onReject = { viewModel.reject(row.bid.id) },
                )
            }
        }
    }
}

@Composable
private fun BidRow(
    route: String,
    amountSar: Int,
    status: BidStatus,
    counterSar: Int?,
    lang: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(route, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val (labelRes, tone) = status.display()
                TagChip(stringResource(labelRes), tone = tone)
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                "${stringResource(Res.string.mb_your_bid)}: ${amountSar.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status == BidStatus.COUNTERED && counterSar != null) {
                Spacer(Modifier.height(Spacing.sm))
                Surface(shape = RoundedCornerShape(Radius.md), color = AppColors.Amber.c50, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.sm)) {
                        Text(
                            "${stringResource(Res.string.mb_counter_offer)}: ${counterSar.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Amber.c800,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            DriverButton(
                                text = stringResource(Res.string.mb_accept),
                                onClick = onAccept,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                                Text(stringResource(Res.string.mb_reject))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BidStatus.display(): Pair<StringResource, ChipTone> = when (this) {
    BidStatus.PENDING -> Res.string.mb_pending to ChipTone.NEUTRAL
    BidStatus.COUNTERED -> Res.string.mb_countered to ChipTone.WARNING
    BidStatus.WON -> Res.string.mb_won to ChipTone.SUCCESS
    BidStatus.LOST -> Res.string.mb_lost to ChipTone.DANGER
    BidStatus.WITHDRAWN -> Res.string.mb_withdrawn to ChipTone.NEUTRAL
}
