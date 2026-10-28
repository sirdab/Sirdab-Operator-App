package co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.ErrorText
import co.sirdab.driver.shared.core.ui.components.equipmentLabel
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.truck_change_pending
import co.sirdab.driver.shared.core.ui.generated.resources.truck_change_rejected
import co.sirdab.driver.shared.core.ui.generated.resources.truck_change_review_note
import co.sirdab.driver.shared.core.ui.generated.resources.truck_change_save
import co.sirdab.driver.shared.core.ui.generated.resources.truck_change_send
import co.sirdab.driver.shared.core.ui.generated.resources.truck_change_title
import co.sirdab.driver.shared.core.ui.generated.resources.truck_change_type
import co.sirdab.driver.shared.core.ui.generated.resources.truck_change_withdraw
import co.sirdab.driver.shared.core.ui.generated.resources.truck_types_empty
import co.sirdab.driver.shared.core.ui.generated.resources.trucks_add
import co.sirdab.driver.shared.core.ui.generated.resources.trucks_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.trucks_title
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileTruck
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class TrucksUiState(
    val trucks: List<ProfileTruck> = emptyList(),
    /** True once ops has verified the driver: a new type then waits for their review. */
    val changesReviewed: Boolean = false,
    /** What the driver may pick. Null until the first read lands. */
    val types: List<TruckType>? = null,
    val typesError: AppError? = null,
    /** The truck the type sheet is open for, or null while it is closed. */
    val changing: ProfileTruck? = null,
    val picked: TruckType? = null,
    val isSubmitting: Boolean = false,
    /** The truck whose pending change is being taken back. */
    val withdrawing: String? = null,
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
)

/**
 * The trucks a driver already has, and changing what type each one is.
 *
 * The plate is what the server keys a truck on, so a truck is never renamed here: a type that was
 * wrong is changed, and a different truck is added as one. Once ops has verified the driver, the
 * new type is a request they approve or reject and the truck keeps its old type until then, in
 * every fleet, which is what this screen has to make plain.
 *
 * The trucks come from the cached profile rather than this screen's own copy, so one added on the
 * add screen is here when the driver comes back.
 */
class TrucksViewModel(
    private val repository: DriverOnboardingRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(TrucksUiState())
    val state: StateFlow<TrucksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.profile.collect { profile ->
                _state.update {
                    it.copy(
                        trucks = profile?.trucks.orEmpty(),
                        changesReviewed = profile?.truckChangesReviewed == true,
                    )
                }
            }
        }
        loadTypes()
    }

    fun loadTypes() {
        _state.update { it.copy(typesError = null) }
        viewModelScope.launch {
            when (val result = repository.truckTypes()) {
                is AppResult.Success -> _state.update { it.copy(types = result.data) }
                is AppResult.Failure -> _state.update { it.copy(typesError = result.error) }
            }
        }
    }

    /** Opens on the type already asked for, if there is one, so changing a request starts from it. */
    fun openChange(truck: ProfileTruck) {
        val pending = truck.pendingChange
        _state.update { state ->
            state.copy(
                changing = truck,
                picked = pending?.let { change ->
                    state.types?.firstOrNull { it.matches(change.bodyType, change.sizeClass, change.temperature) }
                },
                errorMessage = null,
                errorReason = null,
            )
        }
        if (_state.value.types == null) loadTypes()
    }

    fun closeChange() {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(changing = null, picked = null, errorMessage = null, errorReason = null) }
    }

    fun pick(type: TruckType) {
        _state.update { it.copy(picked = type, errorMessage = null, errorReason = null) }
    }

    fun submitChange() {
        val state = _state.value
        val truck = state.changing ?: return
        val type = state.picked ?: return
        if (state.isSubmitting) return

        _state.update { it.copy(isSubmitting = true, errorMessage = null, errorReason = null) }
        viewModelScope.launch {
            when (val result = repository.changeTruckType(truck, type)) {
                is AppResult.Success -> {
                    analytics.track(
                        AnalyticsEvent.truckChangeRequested(
                            body = type.bodyType,
                            size = type.sizeClass,
                            temperature = type.temperature,
                            pendingReview = result.data.trucks.any { it.id == truck.id && it.pendingChange != null },
                        ),
                    )
                    _state.update { it.copy(isSubmitting = false, changing = null, picked = null) }
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.truckChangeFailed(result.error))
                    val notOffered = result.error.reason == AppErrorReason.EQUIPMENT_NOT_OFFERED
                    _state.update {
                        it.copy(
                            // The fleet retired the type since the list was read.
                            picked = if (notOffered) null else it.picked,
                            isSubmitting = false,
                            errorMessage = result.error.message,
                            errorReason = result.error.reason,
                        )
                    }
                    if (notOffered) loadTypes()
                }
            }
        }
    }

    fun withdraw(truckId: String) {
        if (_state.value.withdrawing != null) return
        _state.update { it.copy(withdrawing = truckId, errorMessage = null, errorReason = null) }
        viewModelScope.launch {
            when (val result = repository.withdrawTruckChange(truckId)) {
                is AppResult.Success -> {
                    analytics.track(AnalyticsEvent.truckChangeWithdrawn())
                    _state.update { it.copy(withdrawing = null) }
                }
                is AppResult.Failure -> {
                    analytics.track(AnalyticsEvent.truckChangeWithdrawFailed(result.error))
                    _state.update {
                        it.copy(
                            withdrawing = null,
                            errorMessage = result.error.message,
                            errorReason = result.error.reason,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrucksScreen(
    onAddTruck: () -> Unit,
    viewModel: TrucksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lang = AppLocale.current()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            Text(stringResource(Res.string.trucks_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.trucks_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.trucks.forEach { truck ->
                    TruckCard(
                        truck = truck,
                        typeName = typeName(state.types, lang, truck.bodyType, truck.sizeClass, truck.temperature),
                        requestedTypeName = truck.pendingChange?.let {
                            typeName(state.types, lang, it.bodyType, it.sizeClass, it.temperature)
                        },
                        isWithdrawing = state.withdrawing == truck.id,
                        onChange = { viewModel.openChange(truck) },
                        onWithdraw = { viewModel.withdraw(truck.id) },
                    )
                }
            }

            // The sheet shows its own failures; this is for the ones that happen behind it.
            if (state.changing == null && (state.errorReason != null || state.errorMessage != null)) {
                Spacer(Modifier.height(Spacing.sm))
                ErrorText(state.errorReason, state.errorMessage)
            }

            Spacer(Modifier.height(Spacing.lg))
            OutlinedButton(onClick = onAddTruck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.trucks_add))
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    val changing = state.changing
    if (changing != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeChange,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ChangeTypeSheet(
                truck = changing,
                state = state,
                lang = lang,
                onPick = viewModel::pick,
                onRetry = viewModel::loadTypes,
                onSubmit = viewModel::submitChange,
            )
        }
    }
}

/** The fleet's own name for a type when it runs one, and the three axes spelled out when not. */
@Composable
private fun typeName(
    types: List<TruckType>?,
    lang: String,
    body: EquipmentBody,
    size: EquipmentSize,
    temperature: EquipmentTemperature,
): String = types?.firstOrNull { it.matches(body, size, temperature) }?.displayName(lang)
    ?: equipmentLabel(body, size, temperature)

@Composable
private fun TruckCard(
    truck: ProfileTruck,
    typeName: String,
    requestedTypeName: String?,
    isWithdrawing: Boolean,
    onChange: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.xxs)) {
            Text(truck.licencePlate, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(typeName, style = MaterialTheme.typography.bodyMedium)

            val rejection = truck.changeRejection
            when {
                requestedTypeName != null -> {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        stringResource(Res.string.truck_change_pending, requestedTypeName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                rejection != null -> {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        stringResource(Res.string.truck_change_rejected, rejection.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (requestedTypeName != null) {
                    if (isWithdrawing) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        TextButton(onClick = onWithdraw) {
                            Text(stringResource(Res.string.truck_change_withdraw), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                TextButton(onClick = onChange) {
                    Text(stringResource(Res.string.truck_change_type))
                }
            }
        }
    }
}

/**
 * The fleet's types, less the one the truck already is: sending that would change nothing, and for
 * a verified driver would not even take back a request they have pending. Withdrawing does that.
 */
@Composable
private fun ChangeTypeSheet(
    truck: ProfileTruck,
    state: TrucksUiState,
    lang: String,
    onPick: (TruckType) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
) {
    val others = state.types?.filterNot { it.matches(truck.bodyType, truck.sizeClass, truck.temperature) }
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        Text(stringResource(Res.string.truck_change_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            truck.licencePlate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.changesReviewed) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.truck_change_review_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.md))
        // The list scrolls and the button stays put: a fleet can run twenty types, and a button
        // below all of them is one the driver has to go looking for after every pick.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (others?.isEmpty() == true) {
                Text(
                    stringResource(Res.string.truck_types_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TruckTypePicker(
                    types = others,
                    error = state.typesError,
                    selected = state.picked,
                    lang = lang,
                    onPick = onPick,
                    onRetry = onRetry,
                )
            }
        }

        if (state.errorReason != null || state.errorMessage != null) {
            Spacer(Modifier.height(Spacing.sm))
            ErrorText(state.errorReason, state.errorMessage)
        }

        Spacer(Modifier.height(Spacing.md))
        DriverButton(
            text = stringResource(
                if (state.changesReviewed) Res.string.truck_change_send else Res.string.truck_change_save,
            ),
            onClick = onSubmit,
            enabled = state.picked != null,
            isLoading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.lg))
    }
}
