package co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.ErrorText
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.OptionRow
import co.sirdab.driver.shared.core.ui.components.equipmentLabel
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.capacity_tons
import co.sirdab.driver.shared.core.ui.generated.resources.plate_number
import co.sirdab.driver.shared.core.ui.generated.resources.signup_add_truck
import co.sirdab.driver.shared.core.ui.generated.resources.signup_remove_truck
import co.sirdab.driver.shared.core.ui.generated.resources.trips_retry
import co.sirdab.driver.shared.core.ui.generated.resources.truck_type
import co.sirdab.driver.shared.core.ui.generated.resources.truck_types_empty
import co.sirdab.driver.shared.core.ui.generated.resources.vehicle_subtitle
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileTruck
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class TruckUiState(
    val draft: TruckDraft = TruckDraft(),
    val trucks: List<ProfileTruck> = emptyList(),
    /** What the driver may pick. Null until the first read lands. */
    val types: List<TruckType>? = null,
    /** Why the list could not be read. Only shown while there is no list to offer. */
    val typesError: AppError? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
)

/**
 * The truck, or trucks: a driver may run more than one, and each one needs its own registration.
 *
 * Adding a plate that is already on the profile updates that truck rather than making a second,
 * which is what makes this screen safe to come back to.
 *
 * The type is picked from the workspace's own list rather than built from three free axes: the
 * server refuses a combination none of the driver's workspaces runs, and three pickers would let a
 * driver assemble exactly that.
 */
class SignUpTruckViewModel(
    private val repository: DriverOnboardingRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TruckUiState(trucks = repository.profile.value?.trucks.orEmpty()),
    )
    val state: StateFlow<TruckUiState> = _state.asStateFlow()

    init {
        loadTypes()
    }

    /** Read the list again. A list already on screen stays there while it runs. */
    fun loadTypes() {
        _state.value = _state.value.copy(typesError = null)
        viewModelScope.launch {
            _state.value = when (val result = repository.truckTypes()) {
                is AppResult.Success -> _state.value.copy(types = result.data)
                is AppResult.Failure -> _state.value.copy(typesError = result.error)
            }
        }
    }

    fun edit(transform: (TruckDraft) -> TruckDraft) {
        _state.value = _state.value.copy(
            draft = transform(_state.value.draft),
            errorMessage = null,
            errorReason = null,
        )
    }

    fun add(onAdded: () -> Unit) {
        val draft = _state.value.draft
        val type = draft.type
        if (type == null || !draft.canSubmit || _state.value.isSubmitting) return

        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null, errorReason = null)
        viewModelScope.launch {
            when (val result = repository.addTruck(draft)) {
                is AppResult.Success -> {
                    analytics.track(
                        AnalyticsEvent.truckAdded(
                            body = type.bodyType,
                            size = type.sizeClass,
                            temperature = type.temperature,
                            truckCount = result.data.trucks.size,
                        ),
                    )
                    _state.value = TruckUiState(trucks = result.data.trucks, types = _state.value.types)
                    onAdded()
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.truckAddFailed(result.error))
                    val notOffered = result.error.reason == AppErrorReason.EQUIPMENT_NOT_OFFERED
                    _state.value = _state.value.copy(
                        // The workspace retired the type since the list was read. Unpicking it
                        // keeps the driver from sending the same refusal twice.
                        draft = if (notOffered) draft.copy(type = null) else _state.value.draft,
                        isSubmitting = false,
                        errorMessage = result.error.message,
                        errorReason = result.error.reason,
                    )
                    if (notOffered) loadTypes()
                }
            }
        }
    }

    /** The server refuses the last one, and says so; the screen only has to relay it. */
    fun remove(truckId: String) {
        viewModelScope.launch {
            when (val result = repository.removeTruck(truckId)) {
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.truckRemoved(result.data.trucks.size))
                    _state.value = _state.value.copy(
                        trucks = result.data.trucks,
                        errorMessage = null,
                        errorReason = null,
                    )
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.truckRemoveFailed(result.error))
                    _state.value = _state.value.copy(
                        errorMessage = result.error.message,
                        errorReason = result.error.reason,
                    )
                }
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
    val lang = AppLocale.current()

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

            // An empty list means none of the driver's workspaces runs a type a truck can be written
            // as. A form nobody can submit would only look broken, so the screen says so instead.
            if (state.types?.isEmpty() == true) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    stringResource(Res.string.truck_types_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
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
                TruckTypePicker(
                    types = state.types,
                    error = state.typesError,
                    selected = draft.type,
                    lang = lang,
                    onPick = { type -> viewModel.edit { it.copy(type = type) } },
                    onRetry = viewModel::loadTypes,
                )

                Spacer(Modifier.height(Spacing.md))
                DriverTextField(
                    value = draft.capacityTons,
                    onValueChange = { value ->
                        viewModel.edit { it.copy(capacityTons = value.filter { c -> c.isDigit() || c == '.' }) }
                    },
                    label = stringResource(Res.string.capacity_tons),
                    keyboardType = KeyboardType.Decimal,
                )
            }

            if (state.errorReason != null || state.errorMessage != null) {
                Spacer(Modifier.height(Spacing.sm))
                ErrorText(state.errorReason, state.errorMessage)
            }

            if (state.types?.isEmpty() != true) {
                Spacer(Modifier.height(Spacing.xl))
                DriverButton(
                    text = stringResource(Res.string.signup_add_truck),
                    onClick = { viewModel.add(onSaved) },
                    enabled = draft.canSubmit,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * The workspace's truck types, by their own names. A failed read with nothing to show yet gets a
 * retry, since the form cannot be submitted without a pick.
 */
@Composable
private fun TruckTypePicker(
    types: List<TruckType>?,
    error: AppError?,
    selected: TruckType?,
    lang: String,
    onPick: (TruckType) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        types == null && error != null -> Column {
            ErrorText(error)
            Spacer(Modifier.height(Spacing.xs))
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(Res.string.trips_retry))
            }
        }

        types == null -> Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            types.forEach { type ->
                OptionRow(
                    label = type.displayName(lang),
                    selected = type == selected,
                    onClick = { onPick(type) },
                )
            }
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
                    equipmentLabel(truck.bodyType, truck.sizeClass, truck.temperature),
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
