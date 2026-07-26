package co.sirdab.driver.shared.feature.loadboard.impl.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.core.model.Shipper
import co.sirdab.driver.shared.core.ui.components.ChipTone
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.TagChip
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.ld_book_now
import co.sirdab.driver.shared.core.ui.generated.resources.ld_cargo
import co.sirdab.driver.shared.core.ui.generated.resources.ld_cert_required
import co.sirdab.driver.shared.core.ui.generated.resources.ld_distance
import co.sirdab.driver.shared.core.ui.generated.resources.ld_dropoff
import co.sirdab.driver.shared.core.ui.generated.resources.ld_handling
import co.sirdab.driver.shared.core.ui.generated.resources.ld_loads_posted
import co.sirdab.driver.shared.core.ui.generated.resources.ld_pickup
import co.sirdab.driver.shared.core.ui.generated.resources.ld_place_bid
import co.sirdab.driver.shared.core.ui.generated.resources.ld_shipper
import co.sirdab.driver.shared.core.ui.generated.resources.ld_temperature
import co.sirdab.driver.shared.core.ui.generated.resources.ld_title
import co.sirdab.driver.shared.core.ui.generated.resources.ld_weight
import co.sirdab.driver.shared.core.ui.generated.resources.lb_suggested
import co.sirdab.driver.shared.core.ui.generated.resources.unit_km
import co.sirdab.driver.shared.core.ui.generated.resources.unit_sar
import co.sirdab.driver.shared.core.ui.generated.resources.unit_ton
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.localizeDigits
import co.sirdab.driver.shared.core.util.toGroupedString
import co.sirdab.driver.shared.core.util.toLocalizedString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadDetailScreen(
    loadId: String,
    onBack: () -> Unit,
    onPlaceBid: (String) -> Unit,
    viewModel: LoadDetailViewModel = koinViewModel { parametersOf(loadId) },
) {
    val state by viewModel.state.collectAsState()
    val lang = Locale.current.language

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.ld_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            val load = state.load
            if (load != null) {
                Surface(shadowElevation = 8.dp) {
                    DriverButton(
                        text = if (load.instantBook) stringResource(Res.string.ld_book_now) else stringResource(Res.string.ld_place_bid),
                        onClick = { onPlaceBid(load.id) },
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    )
                }
            }
        },
    ) { padding ->
        val load = state.load
        if (state.isLoading || load == null) {
            co.sirdab.driver.shared.core.ui.components.SkeletonCard(Modifier.fillMaxWidth().padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
        ) {
            Text(
                "${load.originName} → ${load.destinationName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.xs))
            val rate = load.fixedRateSar ?: load.suggestedRateSar
            Text(
                "${rate.toGroupedString(lang)} ${stringResource(Res.string.unit_sar)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.Primary.c700,
            )
            if (!load.instantBook) {
                Text(stringResource(Res.string.lb_suggested), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(Spacing.md))
            if (load.requiresCertification != null) {
                CertWarning()
                Spacer(Modifier.height(Spacing.md))
            }

            InfoRow(stringResource(Res.string.ld_distance), "${load.distanceKm.toLocalizedString(lang)} ${stringResource(Res.string.unit_km)}")
            InfoRow(stringResource(Res.string.ld_weight), "${load.weightTons.toString().localizeDigits(lang)} ${stringResource(Res.string.unit_ton)}")
            InfoRow(stringResource(Res.string.ld_cargo), stringResource(load.cargoType.labelRes()))
            InfoRow(stringResource(Res.string.ld_pickup), formatWindow(load.pickupWindow.startMillis, load.pickupWindow.endMillis, lang))
            InfoRow(stringResource(Res.string.ld_dropoff), formatWindow(load.dropoffWindow.startMillis, load.dropoffWindow.endMillis, lang))
            load.reeferRange?.let {
                InfoRow(stringResource(Res.string.ld_temperature), "${it.minC.toLocalizedString(lang)}° … ${it.maxC.toLocalizedString(lang)}°C")
            }

            if (load.handlingFlags.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(Res.string.ld_handling), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    load.handlingFlags.forEach { TagChip(stringResource(it.labelRes()), tone = ChipTone.WARNING) }
                }
            }

            state.shipper?.let { shipper ->
                Spacer(Modifier.height(Spacing.md))
                ShipperCard(shipper, lang)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CertWarning() {
    Surface(shape = RoundedCornerShape(Radius.md), color = AppColors.Amber.c50, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.Amber.c800)
            Text(stringResource(Res.string.ld_cert_required), style = MaterialTheme.typography.bodyMedium, color = AppColors.Amber.c800)
        }
    }
}

@Composable
private fun ShipperCard(shipper: Shipper, lang: String) {
    Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md)) {
            Text(stringResource(Res.string.ld_shipper), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(if (lang == "ar") shipper.nameAr else shipper.nameEn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "★ ${shipper.rating.toString().localizeDigits(lang)} • ${shipper.loadsPosted.toLocalizedString(lang)} ${stringResource(Res.string.ld_loads_posted)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
