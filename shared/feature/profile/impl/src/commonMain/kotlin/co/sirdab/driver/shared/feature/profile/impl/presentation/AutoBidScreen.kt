package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.autobid_active
import co.sirdab.driver.shared.core.ui.generated.resources.autobid_desc
import co.sirdab.driver.shared.core.ui.generated.resources.autobid_dest
import co.sirdab.driver.shared.core.ui.generated.resources.autobid_max_rate
import co.sirdab.driver.shared.core.ui.generated.resources.autobid_origin
import co.sirdab.driver.shared.core.ui.generated.resources.autobid_save
import co.sirdab.driver.shared.core.ui.generated.resources.autobid_title
import co.sirdab.driver.shared.core.ui.generated.resources.autobid_vehicle
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBidScreen(
    onBack: () -> Unit,
    viewModel: AutoBidViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lang = Locale.current.language
    fun cityName(id: String) = state.cities.firstOrNull { it.id == id }?.let { if (lang == "ar") it.nameAr else it.nameEn } ?: id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.autobid_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md)) {
            Text(stringResource(Res.string.autobid_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(Spacing.lg))
            Label(stringResource(Res.string.autobid_origin))
            ChipRow(state.cities.map { it.id }, state.originId, ::cityName) { viewModel.onOrigin(it) }

            Spacer(Modifier.height(Spacing.md))
            Label(stringResource(Res.string.autobid_dest))
            ChipRow(state.cities.map { it.id }, state.destId, ::cityName) { viewModel.onDest(it) }

            Spacer(Modifier.height(Spacing.md))
            Label(stringResource(Res.string.autobid_vehicle))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                VehicleType.entries.forEach { v ->
                    SelChip(stringResource(v.labelRes()), v == state.vehicle) { viewModel.onVehicle(v) }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            DriverTextField(
                value = state.maxRate,
                onValueChange = viewModel::onMaxRate,
                label = stringResource(Res.string.autobid_max_rate),
                keyboardType = KeyboardType.Number,
            )

            Spacer(Modifier.height(Spacing.lg))
            if (state.saved) {
                Surface(shape = RoundedCornerShape(Radius.md), color = AppColors.Green.c50, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.Green.c600)
                        Text(stringResource(Res.string.autobid_active), color = AppColors.Green.c700, style = MaterialTheme.typography.titleSmall)
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }
            DriverButton(
                text = stringResource(Res.string.autobid_save),
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.xxs))
}

@Composable
private fun ChipRow(ids: List<String>, selected: String, name: (String) -> String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ids.forEach { id -> SelChip(name(id), id == selected) { onSelect(id) } }
    }
}

@Composable
private fun SelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.pill),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AppColors.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}
