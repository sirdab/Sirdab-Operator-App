package co.sirdab.driver.shared.feature.onboarding.impl.presentation.splash

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import co.sirdab.driver.shared.core.preferences.locale.LanguageStore
import co.sirdab.driver.shared.core.ui.components.DriverButton
import co.sirdab.driver.shared.core.ui.components.LanguageOptionRow
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.choose_language
import co.sirdab.driver.shared.core.ui.generated.resources.get_started
import co.sirdab.driver.shared.core.ui.generated.resources.onboarding_welcome_subtitle
import co.sirdab.driver.shared.core.ui.generated.resources.onboarding_welcome_title
import co.sirdab.driver.shared.core.ui.theme.Spacing
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class SplashViewModel(private val languageStore: LanguageStore) : ViewModel() {
    val language: StateFlow<AppLanguage> = languageStore.language
    fun select(language: AppLanguage) = languageStore.setLanguage(language)
}

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val current by viewModel.language.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            Spacer(Modifier.height(Spacing.xl))
            Text(
                stringResource(Res.string.onboarding_welcome_title),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(Res.string.onboarding_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                stringResource(Res.string.choose_language),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AppLanguage.entries.forEach { lang ->
                    LanguageOptionRow(
                        label = lang.displayName,
                        selected = lang == current,
                        onClick = { viewModel.select(lang) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            DriverButton(
                text = stringResource(Res.string.get_started),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
