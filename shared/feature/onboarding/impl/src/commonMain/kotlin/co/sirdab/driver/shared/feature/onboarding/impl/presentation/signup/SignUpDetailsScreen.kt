package co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.DriverDateField
import co.sirdab.driver.shared.core.ui.components.DriverDropdownField
import co.sirdab.driver.shared.core.ui.components.DriverTextField
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_continue
import co.sirdab.driver.shared.core.ui.generated.resources.signup_date_hint
import co.sirdab.driver.shared.core.ui.generated.resources.signup_details_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.signup_details_title
import co.sirdab.driver.shared.core.ui.generated.resources.signup_licence_expiry
import co.sirdab.driver.shared.core.ui.generated.resources.signup_licence_number
import co.sirdab.driver.shared.core.ui.generated.resources.signup_name
import co.sirdab.driver.shared.core.ui.generated.resources.signup_nationality
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.DetailsDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class DetailsUiState(
    val draft: DetailsDraft = DetailsDraft(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
)

/**
 * Name, nationality and licence: one PATCH to the driver's own profile.
 *
 * Seeded from whatever the profile already holds, because this screen is reachable again after it
 * has been filled in — a driver correcting a mistyped licence number should not start from blank.
 */
class SignUpDetailsViewModel(
    private val repository: DriverOnboardingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DetailsUiState(
            draft = repository.profile.value?.let {
                DetailsDraft(
                    name = it.name,
                    nationality = it.nationality,
                    licenceNumber = it.licenceNumber.orEmpty(),
                    licenceExpiresAt = it.licenceExpiresAt.orEmpty(),
                )
            } ?: DetailsDraft(),
        ),
    )
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    fun edit(transform: (DetailsDraft) -> DetailsDraft) {
        _state.value = _state.value.copy(
            draft = transform(_state.value.draft),
            errorMessage = null,
            errorReason = null,
        )
    }

    fun submit(onSaved: () -> Unit) {
        val draft = _state.value.draft
        if (!draft.canSubmit || _state.value.isSubmitting) return

        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null, errorReason = null)
        viewModelScope.launch {
            when (val result = repository.saveDetails(draft)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(isSubmitting = false)
                    onSaved()
                }
                is AppResult.Failure -> _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = result.error.message,
                    errorReason = result.error.reason,
                )
            }
        }
    }
}

@Composable
fun SignUpDetailsScreen(
    onSaved: () -> Unit,
    viewModel: SignUpDetailsViewModel = koinViewModel(),
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
            Text(stringResource(Res.string.signup_details_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.signup_details_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))
            DriverTextField(
                value = draft.name,
                onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
                label = stringResource(Res.string.signup_name),
            )
            Spacer(Modifier.height(Spacing.sm))
            DriverDropdownField(
                label = stringResource(Res.string.signup_nationality),
                selected = draft.nationality,
                options = Nationality.entries,
                optionLabel = { nationality -> stringResource(nationality.labelRes()) },
                onSelect = { value -> viewModel.edit { it.copy(nationality = value) } },
            )
            Spacer(Modifier.height(Spacing.sm))
            DriverTextField(
                value = draft.licenceNumber,
                onValueChange = { value -> viewModel.edit { it.copy(licenceNumber = value) } },
                label = stringResource(Res.string.signup_licence_number),
            )
            Spacer(Modifier.height(Spacing.sm))
            // Picked, not typed: the contract takes one date format, and a driver typing it by hand
            // gets it wrong in a way that fails the write for a reason they cannot see.
            DriverDateField(
                value = draft.licenceExpiresAt,
                onValueChange = { value -> viewModel.edit { it.copy(licenceExpiresAt = value) } },
                label = stringResource(Res.string.signup_licence_expiry),
                placeholder = stringResource(Res.string.signup_date_hint),
            )

            val errorReason = state.errorReason
            val errorMessage = errorReason?.let { stringResource(it.labelRes()) } ?: state.errorMessage
            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.xl))
            DriverButton(
                text = stringResource(Res.string.common_continue),
                onClick = { viewModel.submit(onSaved) },
                enabled = draft.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
