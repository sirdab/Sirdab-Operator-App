package co.sirdab.driver.shared.feature.onboarding.impl.presentation.otp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.network.BackendMode
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.components.DriverOtpField
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.otp_hint_demo
import co.sirdab.driver.shared.core.ui.generated.resources.otp_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.otp_title
import co.sirdab.driver.shared.core.ui.generated.resources.resend_code
import co.sirdab.driver.shared.core.ui.generated.resources.verify
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.StartDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class OtpUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    /**
     * Why the last attempt failed. A wrong code and "your dispatcher has not added you yet" both
     * arrive here, and they are not the same thing to the driver holding the phone.
     */
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
)

class OtpViewModel(
    val phone: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    fun onCodeChange(value: String) {
        _state.value = _state.value.copy(code = value, errorMessage = null, errorReason = null)
    }

    /**
     * [onVerified] receives where the driver belongs next.
     *
     * A driver a dispatcher already created has nothing left to fill in, so they
     * go straight to the shell; the remaining onboarding screens exist for a
     * self-signup flow the API does not offer yet.
     */
    fun verify(onVerified: (StartDestination) -> Unit) {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            when (val result = authRepository.verifyOtp(_state.value.code)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false)
                    onVerified(authRepository.resolveStartDestination())
                }
                is AppResult.Failure -> _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = result.error.message,
                    errorReason = result.error.reason,
                )
            }
        }
    }
}

@Composable
fun OtpScreen(
    phone: String,
    onVerified: (StartDestination) -> Unit,
    viewModel: OtpViewModel = koinViewModel { parametersOf(phone) },
    backendMode: BackendMode = koinInject(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
        ) {
            Text(stringResource(Res.string.otp_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${stringResource(Res.string.otp_subtitle)}  $phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            DriverOtpField(value = state.code, onValueChange = viewModel::onCodeChange)
            Spacer(Modifier.height(Spacing.sm))
            val errorReason = state.errorReason
            val errorMessage = errorReason?.let { stringResource(it.labelRes()) } ?: state.errorMessage
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (backendMode == BackendMode.DEMO) {
                // Only true of the demo world. Against a real Supabase the code
                // is checked, and promising otherwise sends the driver in circles.
                Text(
                    stringResource(Res.string.otp_hint_demo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            DriverButton(
                text = stringResource(Res.string.verify),
                onClick = { viewModel.verify(onVerified) },
                enabled = state.code.length == 6,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.resend_code))
            }
        }
    }
}
