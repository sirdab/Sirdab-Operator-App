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
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.network.BackendMode
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.labelRes
import co.sirdab.driver.shared.core.ui.components.DriverOtpField
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.otp_code_sent
import co.sirdab.driver.shared.core.ui.generated.resources.otp_hint_demo
import co.sirdab.driver.shared.core.ui.generated.resources.otp_resend_in
import co.sirdab.driver.shared.core.ui.generated.resources.otp_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.otp_title
import co.sirdab.driver.shared.core.ui.generated.resources.otp_wrong_number
import co.sirdab.driver.shared.core.ui.generated.resources.resend_code
import co.sirdab.driver.shared.core.ui.generated.resources.verify
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.core.util.localizeDigits
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** How long a code is worth waiting for before offering another. Supabase's own floor is 60s. */
private const val RESEND_COOLDOWN_SECONDS = 60

data class OtpUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    /**
     * Why the last attempt failed. A wrong code and "your dispatcher has not added you yet" both
     * arrive here, and they are not the same thing to the driver holding the phone.
     */
    val errorMessage: String? = null,
    val errorReason: AppErrorReason? = null,
    /**
     * Seconds until another code can be asked for.
     *
     * A driver whose SMS has not arrived needs to know whether to wait or to act, and a dead
     * "resend" button answers neither. Zero means the button is live.
     */
    val secondsUntilResend: Int = RESEND_COOLDOWN_SECONDS,
    val isResending: Boolean = false,
    /** Set when a fresh code went out, so the screen can say so rather than looking inert. */
    val codeResent: Boolean = false,
) {
    val canResend: Boolean get() = secondsUntilResend == 0 && !isResending && !isLoading
}

class OtpViewModel(
    val phone: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private var countdown: Job? = null

    init {
        // The code was sent by the screen before this one, so the first cooldown starts with the
        // screen rather than with the first resend.
        startCountdown()
    }

    fun onCodeChange(value: String) {
        _state.value = _state.value.copy(
            code = value,
            errorMessage = null,
            errorReason = null,
            codeResent = false,
        )
    }

    /**
     * [onVerified] receives where the driver belongs, which the profile decides.
     *
     * A driver the platform already knows and ops has approved goes straight to the shell; one
     * with anything still missing goes to sign-up, which is theirs to finish. A number nobody has
     * ever seen is not an error here — reading the profile is what creates it.
     */
    fun verify(onVerified: (DriverDestination) -> Unit) {
        _state.value = _state.value.copy(isLoading = true, codeResent = false)
        viewModelScope.launch {
            when (val result = authRepository.verifyOtp(_state.value.code)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false)
                    onVerified(result.data)
                }
                is AppResult.Failure -> _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = result.error.message,
                    errorReason = result.error.reason,
                )
            }
        }
    }

    /** Ask for another code. The cooldown restarts whether or not the send succeeded. */
    fun resend() {
        if (!_state.value.canResend) return
        _state.value = _state.value.copy(
            isResending = true,
            errorMessage = null,
            errorReason = null,
            codeResent = false,
        )
        viewModelScope.launch {
            val result = authRepository.requestOtp(phone)
            _state.value = when (result) {
                is AppResult.Success -> _state.value.copy(
                    isResending = false,
                    // The old code is dead now, so clearing the boxes saves a driver typing the
                    // digits from the wrong message.
                    code = "",
                    codeResent = true,
                )
                is AppResult.Failure -> _state.value.copy(
                    isResending = false,
                    errorMessage = result.error.message,
                    errorReason = result.error.reason,
                )
            }
            startCountdown()
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var remaining = RESEND_COOLDOWN_SECONDS
            _state.value = _state.value.copy(secondsUntilResend = remaining)
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                _state.value = _state.value.copy(secondsUntilResend = remaining)
            }
        }
    }
}

@Composable
fun OtpScreen(
    phone: String,
    onVerified: (DriverDestination) -> Unit,
    onChangeNumber: () -> Unit,
    viewModel: OtpViewModel = koinViewModel { parametersOf(phone) },
    backendMode: BackendMode = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val lang = Locale.current.language

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
            } else if (state.codeResent) {
                Text(
                    stringResource(Res.string.otp_code_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
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
            TextButton(
                onClick = viewModel::resend,
                enabled = state.canResend,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.secondsUntilResend > 0) {
                        stringResource(Res.string.otp_resend_in, state.secondsUntilResend.asCountdown(lang))
                    } else {
                        stringResource(Res.string.resend_code)
                    },
                )
            }
            // A mistyped digit in the number is the other reason no code arrives, and waiting out
            // a resend cooldown will not fix it.
            TextButton(onClick = onChangeNumber, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.otp_wrong_number),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** `m:ss`, because a bare "47" reads as neither seconds nor minutes at a glance. */
private fun Int.asCountdown(languageCode: String): String {
    val minutes = this / 60
    val seconds = this % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}".localizeDigits(languageCode)
}
