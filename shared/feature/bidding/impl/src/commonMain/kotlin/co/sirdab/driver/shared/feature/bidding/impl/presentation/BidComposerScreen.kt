package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import co.sirdab.driver.shared.core.model.BidBreakdown
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.bc_commission
import co.sirdab.driver.shared.core.ui.generated.resources.bc_fuel
import co.sirdab.driver.shared.core.ui.generated.resources.bc_net_profit
import co.sirdab.driver.shared.core.ui.generated.resources.bc_rate_label
import co.sirdab.driver.shared.core.ui.generated.resources.bc_submit
import co.sirdab.driver.shared.core.ui.generated.resources.bc_title
import co.sirdab.driver.shared.core.ui.generated.resources.bc_tolls
import co.sirdab.driver.shared.core.ui.generated.resources.bc_win_high
import co.sirdab.driver.shared.core.ui.generated.resources.bc_win_low
import co.sirdab.driver.shared.core.ui.generated.resources.bc_win_medium
import co.sirdab.driver.shared.core.ui.generated.resources.bc_win_prob
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.toGroupedString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BidComposerScreen(
    loadId: String,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: BidComposerViewModel = koinViewModel { parametersOf(loadId) },
) {
    val state by viewModel.state.collectAsState()
    val lang = Locale.current.language

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.bc_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        val breakdown = state.breakdown
        if (breakdown == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.lg),
        ) {
            Text(stringResource(Res.string.bc_rate_label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${state.rateSar.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.Primary.c700,
            )
            Slider(
                value = state.rateSar.toFloat(),
                onValueChange = { viewModel.onRateChange(it.roundToInt()) },
                valueRange = state.minRate.toFloat()..state.maxRate.toFloat(),
            )

            Spacer(Modifier.height(Spacing.sm))
            WinProbabilityIndicator(breakdown.winProbability)

            Spacer(Modifier.height(Spacing.lg))
            Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.md)) {
                    CostRow(stringResource(Res.string.bc_fuel), breakdown.fuelCostSar, lang)
                    CostRow(stringResource(Res.string.bc_tolls), breakdown.tollsSar, lang)
                    CostRow(stringResource(Res.string.bc_commission), breakdown.commissionSar, lang)
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Surface(shape = RoundedCornerShape(Radius.lg), color = AppColors.Primary.c600, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(stringResource(Res.string.bc_net_profit), style = MaterialTheme.typography.titleMedium, color = AppColors.White)
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        "${breakdown.netProfitSar.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.White,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            DriverButton(
                text = stringResource(Res.string.bc_submit),
                onClick = { viewModel.submit(onSubmitted) },
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun CostRow(label: String, amount: Int, lang: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("- ${amount.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WinProbabilityIndicator(probability: Float) {
    val (label, color) = when {
        probability >= 0.65f -> stringResource(Res.string.bc_win_high) to AppColors.Green.c500
        probability >= 0.4f -> stringResource(Res.string.bc_win_medium) to AppColors.Amber.c500
        else -> stringResource(Res.string.bc_win_low) to AppColors.Red.c500
    }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.bc_win_prob), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(Spacing.xxs))
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(Radius.pill)).background(AppColors.Gray.c200),
        ) {
            Box(
                Modifier.fillMaxWidth(probability.coerceIn(0.05f, 1f)).height(8.dp).clip(RoundedCornerShape(Radius.pill)).background(color),
            )
        }
    }
}
