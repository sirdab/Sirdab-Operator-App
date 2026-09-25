package co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.LanguageOptionRow
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.capacity_tons
import co.sirdab.driver.shared.core.ui.generated.resources.plate_number
import co.sirdab.driver.shared.core.ui.generated.resources.signup_add_truck
import co.sirdab.driver.shared.core.ui.generated.resources.signup_remove_truck
import co.sirdab.driver.shared.core.ui.generated.resources.truck_size
import co.sirdab.driver.shared.core.ui.generated.resources.truck_type
import co.sirdab.driver.shared.core.ui.generated.resources.vehicle_subtitle
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileTruck
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class TruckUiState(
    val draft: TruckDraft = TruckDraft(),
    val trucks: List<ProfileTruck> = emptyList(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
)

/**
 * The truck, or trucks: a driver may run more than one, and each one needs its own registration.
 *
 * Adding a plate that is already on the profile updates that truck rather than making a second,
 * which is what makes this screen safe to come back to.
 */
class SignUpTruckViewModel(
    private val repository: DriverOnboardingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TruckUiState(trucks = repository.profile.value?.trucks.orEmpty()),
    )
    val state: StateFlow<TruckUiState> = _state.asStateFlow()

    fun edit(transform: (TruckDraft) -> TruckDraft) {
        _state.value = _state.value.copy(
            draft = transform(_state.value.draft),
            errorMessage = null,
            errorReason = null,
        )
    }

    fun add(onAdded: () -> Unit) {
        val draft = _state.value.draft
        if (!draft.canSubmit || _state.value.isSubmitting) return

        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null, errorReason = null)
        viewModelScope.launch {
            when (val result = repository.addTruck(draft)) {
                is AppResult.Success -> {
                    _state.value = TruckUiState(trucks = result.data.trucks)
                    onAdded()
                }
                is AppResult.Failure -> _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = result.error.message,
                    errorReason = result.error.reason,
                )
            }
        }
    }

    /** The server refuses the last one, and says so; the screen only has to relay it. */
    fun remove(truckId: String) {
        viewModelScope.launch {
            when (val result = repository.removeTruck(truckId)) {
                is AppResult.Success -> _state.value = _state.value.copy(
                    trucks = result.data.trucks,
                    errorMessage = null,
                    errorReason = null,
                )
                is AppResult.Failure -> _state.value = _state.value.copy(
                    errorMessage = result.error.message,
                    errorReason = result.error.reason,
                )
            }
        }
    }
}

@Composable
fun SignUpTruckScreen(
    onSaved: () -> Unit,
    viewModel: SignUpTruckViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val draft = state.draft

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            Text(stringResource(Res.string.signup_add_truck), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.vehicle_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.trucks.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    state.trucks.forEach { truck ->
                        TruckRow(truck = truck, onRemove = { viewModel.remove(truck.id) })
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            DriverTextField(
                value = draft.licencePlate,
                onValueChange = { value -> viewModel.edit { it.copy(licencePlate = value) } },
                label = stringResource(Res.string.plate_number),
            )

            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(Res.string.truck_type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.sm))
            // Built off the enum on purpose: the contract's truck vocabulary is due to widen, and
            // that should be an enum and its labels, not a screen.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                TruckType.entries.forEach { type ->
                    LanguageOptionRow(
                        label = stringResource(type.labelRes()),
                        selected = type == draft.truckType,
                        onClick = { viewModel.edit { it.copy(truckType = type) } },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(Res.string.truck_size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                TruckSize.entries.forEach { size ->
                    LanguageOptionRow(
                        label = stringResource(size.labelRes()),
                        selected = size == draft.truckSize,
                        onClick = { viewModel.edit { it.copy(truckSize = size) } },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))
            DriverTextField(
                value = draft.capacityTons,
                onValueChange = { value ->
                    viewModel.edit { it.copy(capacityTons = value.filter { c -> c.isDigit() || c == '.' }) }
                },
                label = stringResource(Res.string.capacity_tons),
                keyboardType = KeyboardType.Decimal,
            )

            val errorReason = state.errorReason
            val errorMessage = errorReason?.let { stringResource(it.labelRes()) } ?: state.errorMessage
            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.xl))
            DriverButton(
                text = stringResource(Res.string.signup_add_truck),
                onClick = { viewModel.add(onSaved) },
                enabled = draft.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun TruckRow(truck: ProfileTruck, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.md, end = Spacing.xs).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(vertical = Spacing.sm)) {
                Text(truck.licencePlate, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${stringResource(truck.truckSize.labelRes())} · ${stringResource(truck.truckType.labelRes())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove) {
                Text(
                    stringResource(Res.string.signup_remove_truck),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 0.dp),
                )
            }
        }
    }
}
