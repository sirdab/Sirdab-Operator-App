package co.sirdab.driver.shared.feature.onboarding.impl.presentation.nafath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.common_continue
import co.sirdab.driver.shared.core.ui.generated.resources.nafath_title
import co.sirdab.driver.shared.core.ui.generated.resources.nafath_verified_as
import co.sirdab.driver.shared.core.ui.generated.resources.nafath_verifying
import co.sirdab.driver.shared.core.ui.theme.AppColors
import co.sirdab.driver.shared.core.ui.theme.Radius
import co.sirdab.driver.shared.core.ui.theme.Spacing
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class NafathUiState(
    val isVerifying: Boolean = true,
    val verifiedName: String? = null,
)

class NafathViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(NafathUiState())
    val state: StateFlow<NafathUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            delay(2500) // branded Nafath handoff spinner
            when (val result = authRepository.startNafath()) {
                is AppResult.Success -> _state.value = NafathUiState(isVerifying = false, verifiedName = result.data)
                is AppResult.Failure -> _state.value = NafathUiState(isVerifying = false, verifiedName = null)
            }
        }
    }
}

@Composable
fun NafathScreen(
    onVerified: () -> Unit,
    viewModel: NafathViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.lg),
                color = AppColors.Primary.c600,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("نفاذ", style = MaterialTheme.typography.headlineSmall, color = AppColors.White)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(Res.string.nafath_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.md))

            if (state.isVerifying) {
                CircularProgressIndicator()
                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(Res.string.nafath_verifying),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    stringResource(Res.string.nafath_verified_as),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    state.verifiedName.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.xl))
                DriverButton(
                    text = stringResource(Res.string.common_continue),
                    onClick = onVerified,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
