package co.sirdab.driver.shared.feature.onboarding.impl.presentation.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.LanguageOptionRow
import co.sirdab.driver.shared.core.ui.components.StepProgress
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.capacity_tons
import co.sirdab.driver.shared.core.ui.generated.resources.common_finish
import co.sirdab.driver.shared.core.ui.generated.resources.plate_number
import co.sirdab.driver.shared.core.ui.generated.resources.vehicle_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.vehicle_title
import co.sirdab.driver.shared.core.ui.generated.resources.vehicle_type
import co.sirdab.driver.shared.core.ui.generated.resources.vt_container
import co.sirdab.driver.shared.core.ui.generated.resources.vt_curtain
import co.sirdab.driver.shared.core.ui.generated.resources.vt_dry_van
import co.sirdab.driver.shared.core.ui.generated.resources.vt_flatbed
import co.sirdab.driver.shared.core.ui.generated.resources.vt_lowbed
import co.sirdab.driver.shared.core.ui.generated.resources.vt_reefer
import co.sirdab.driver.shared.core.ui.generated.resources.vt_tanker
import co.sirdab.driver.shared.core.ui.generated.resources.vt_van_3t
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

fun VehicleType.labelRes(): StringResource = when (this) {
    VehicleType.FLATBED -> Res.string.vt_flatbed
    VehicleType.CURTAIN_SIDER -> Res.string.vt_curtain
    VehicleType.REEFER -> Res.string.vt_reefer
    VehicleType.CONTAINER_40FT -> Res.string.vt_container
    VehicleType.DRY_VAN -> Res.string.vt_dry_van
    VehicleType.VAN_3T -> Res.string.vt_van_3t
    VehicleType.LOWBED -> Res.string.vt_lowbed
    VehicleType.TANKER -> Res.string.vt_tanker
}

data class VehicleUiState(
    val type: VehicleType = VehicleType.FLATBED,
    val plate: String = "",
    val capacity: String = "",
    val isLoading: Boolean = false,
)

class VehicleViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(VehicleUiState())
    val state: StateFlow<VehicleUiState> = _state.asStateFlow()

    fun onType(type: VehicleType) { _state.value = _state.value.copy(type = type) }
    fun onPlate(value: String) { _state.value = _state.value.copy(plate = value) }
    fun onCapacity(value: String) { _state.value = _state.value.copy(capacity = value.filter { it.isDigit() || it == '.' }) }

    fun submit(onFinished: () -> Unit) {
        val current = _state.value
        _state.value = current.copy(isLoading = true)
        viewModelScope.launch {
            val capacity = current.capacity.toDoubleOrNull() ?: 0.0
            when (authRepository.submitVehicle(current.type, current.plate, capacity)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false)
                    onFinished()
                }
                is AppResult.Failure -> _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}

@Composable
fun VehicleScreen(
    onFinished: () -> Unit,
    viewModel: VehicleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.lg),
        ) {
            StepProgress(current = 5, total = 6)
            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(Res.string.vehicle_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.vehicle_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(stringResource(Res.string.vehicle_type), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                VehicleType.entries.forEach { type ->
                    LanguageOptionRow(
                        label = stringResource(type.labelRes()),
                        selected = type == state.type,
                        onClick = { viewModel.onType(type) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            DriverTextField(
                value = state.plate,
                onValueChange = viewModel::onPlate,
                label = stringResource(Res.string.plate_number),
            )
            Spacer(Modifier.height(Spacing.sm))
            DriverTextField(
                value = state.capacity,
                onValueChange = viewModel::onCapacity,
                label = stringResource(Res.string.capacity_tons),
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(Spacing.xl))
            DriverButton(
                text = stringResource(Res.string.common_finish),
                onClick = { viewModel.submit(onFinished) },
                enabled = state.plate.isNotBlank() && state.capacity.isNotBlank(),
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
