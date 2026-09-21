package co.sirdab.driver.shared.feature.onboarding.impl.presentation.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.components.PhoneNumberField
import co.sirdab.driver.shared.core.ui.components.StepProgress
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_continue
import co.sirdab.driver.shared.core.ui.generated.resources.phone_error
import co.sirdab.driver.shared.core.ui.generated.resources.phone_label
import co.sirdab.driver.shared.core.ui.generated.resources.phone_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.phone_title
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class PhoneUiState(
    val phone: String = "",
    val isLoading: Boolean = false,
    val showError: Boolean = false,
    /** What the backend said. Null falls back to the local format message. */
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
)

class PhoneViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(PhoneUiState())
    val state: StateFlow<PhoneUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) {
        _state.value = _state.value.copy(phone = value, showError = false, errorMessage = null, errorReason = null)
    }

    fun submit(onSent: () -> Unit) {
        val current = _state.value
        if (current.phone.length < 9) {
            _state.value = current.copy(showError = true)
            return
        }
        _state.value = current.copy(isLoading = true)
        viewModelScope.launch {
            when (val result = authRepository.requestOtp("+966${current.phone}")) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false)
                    onSent()
                }
                is AppResult.Failure -> _state.value = _state.value.copy(
                    isLoading = false,
                    showError = true,
                    errorMessage = result.error.message,
                    errorReason = result.error.reason,
                )
            }
        }
    }
}

@Composable
fun PhoneScreen(
    onCodeSent: (String) -> Unit,
    viewModel: PhoneViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val reason = state.errorReason

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
        ) {
            StepProgress(current = 1, total = 5)
            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(Res.string.phone_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.phone_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            PhoneNumberField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(Res.string.phone_label),
                isError = state.showError,
                supportingText = when {
                    !state.showError -> null
                    reason != null -> stringResource(reason.labelRes())
                    state.errorMessage != null -> state.errorMessage
                    else -> stringResource(Res.string.phone_error)
                },
            )
            Spacer(Modifier.height(Spacing.xl))
            DriverButton(
                text = stringResource(Res.string.common_continue),
                onClick = { viewModel.submit { onCodeSent("+966${state.phone}") } },
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
