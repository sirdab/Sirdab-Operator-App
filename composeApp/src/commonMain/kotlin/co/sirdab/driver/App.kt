package co.sirdab.driver

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import co.sirdab.driver.locale.applyPlatformLocale
import co.sirdab.driver.locale.languageChangeRequiresRestart
import co.sirdab.driver.shared.core.preferences.locale.LanguageStore
import co.sirdab.driver.shared.core.ui.theme.AppTheme
import co.sirdab.driver.shared.feature.notifications.api.NotificationsRoute
import co.sirdab.driver.shared.feature.notifications.impl.navigation.notificationsEntries
import co.sirdab.driver.shared.feature.profile.api.navigation.ProfileRoute
import co.sirdab.driver.shared.feature.profile.impl.navigation.profileEntries
import co.sirdab.driver.shared.feature.trip.api.TripRoute
import co.sirdab.driver.shared.feature.trip.impl.navigation.tripEntries
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.StartDestination
import co.sirdab.driver.shared.feature.onboarding.api.navigation.OnboardingRoute
import co.sirdab.driver.shared.feature.onboarding.impl.navigation.onboardingEntries
import org.koin.compose.koinInject

@Composable
fun App() {
    val languageStore = koinInject<LanguageStore>()
    val language by languageStore.language.collectAsState()
    // Android switches live; iOS applies the new bundle on next launch (see PlatformLocale).
    remember(language) { applyPlatformLocale(language.code) }

    val authRepository = koinInject<AuthRepository>()

    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        authRepository.ensureReady()
        ready = true
    }

    // Re-key on language so every stringResource re-reads the process locale set above.
    key(if (languageChangeRequiresRestart) Unit else language) {
        AppTheme(languageCode = language.code) {
            if (!ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AppNavHost(authRepository)
            }
        }
    }
}

@Composable
private fun AppNavHost(authRepository: AuthRepository) {
    val backStack = remember {
        val initial: NavKey = when (authRepository.resolveStartDestination()) {
            StartDestination.MAIN -> MainRoute()
            StartDestination.ONBOARDING -> OnboardingRoute.Splash
        }
        NavBackStack<NavKey>(initial)
    }

    Scaffold { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                onboardingEntries(
                    onNavigate = { route -> backStack.add(route) },
                    onFinished = {
                        backStack.clear()
                        backStack.add(MainRoute())
                    },
                )
                entry<MainRoute> { route ->
                    MainShell(
                        initialTab = route.tab,
                        onOpenInbox = { backStack.add(NotificationsRoute.Inbox) },
                        onOpenHistory = { backStack.add(ProfileRoute.History) },
                        onOpenTrip = { tripId -> backStack.add(TripRoute.Detail(tripId)) },
                    )
                }
                tripEntries(
                    onBack = { backStack.removeLastOrNull() },
                )
                notificationsEntries(
                    onBack = { backStack.removeLastOrNull() },
                )
                profileEntries(
                    onBack = { backStack.removeLastOrNull() },
                )
            },
        )
    }
}
