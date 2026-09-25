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
import androidx.navigation3.runtime.NavEntryDecorator
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
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverDestination
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

    // One question on launch: restore the session, read the driver's profile, and say which of the
    // app's worlds this person is in. Everything below waits on that rather than guessing.
    //
    // The back stack is built from that answer once and then lives here, above the language key
    // below, together with the entries' saved state and ViewModels. Built inside the key, a language
    // change rebuilt it from the launch-time answer: a driver who signed in after launching was sent
    // back to the language picker with a live session, and everyone else lost where they were.
    var backStack by remember { mutableStateOf<NavBackStack<NavKey>?>(null) }
    LaunchedEffect(Unit) {
        backStack = NavBackStack(authRepository.resolveDestination().toRoute())
    }
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>(),
    )

    // Re-key on language so every stringResource re-reads the process locale set above.
    key(if (languageChangeRequiresRestart) Unit else language) {
        AppTheme(languageCode = language.code) {
            val stack = backStack
            if (stack == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AppNavHost(stack, entryDecorators)
            }
        }
    }
}

@Composable
private fun AppNavHost(
    backStack: NavBackStack<NavKey>,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
) {

    /** Every ending is the same move: forget how they got here, and open where they belong. */
    fun goTo(destination: DriverDestination) {
        backStack.clear()
        backStack.add(destination.toRoute())
    }

    Scaffold { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            entryDecorators = entryDecorators,
            entryProvider = entryProvider {
                onboardingEntries(
                    onNavigate = { route -> backStack.add(route) },
                    onBack = { backStack.removeLastOrNull() },
                    onDestination = ::goTo,
                )
                entry<MainRoute> { route ->
                    MainShell(
                        initialTab = route.tab,
                        onOpenInbox = { backStack.add(NotificationsRoute.Inbox) },
                        onOpenHistory = { backStack.add(ProfileRoute.History) },
                        onOpenTrip = { tripId -> backStack.add(TripRoute.Detail(tripId)) },
                        // Right back to the beginning: the session is gone, so every screen
                        // behind this one would be showing a driver who is no longer signed in.
                        onLoggedOut = { goTo(DriverDestination.SIGN_IN) },
                        // Pushed, not replaced: this driver is working, and finishing the
                        // paperwork should put them back where they were. The checklist takes
                        // itself off the stack once the server says nothing is missing.
                        onFixPaperwork = { backStack.add(OnboardingRoute.SignUp) },
                        // Pushed over the shell, like the checklist: the driver is looking at
                        // where their documents stand, not being held somewhere.
                        onOpenReview = { backStack.add(OnboardingRoute.UnderReview) },
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

/**
 * Where each of the app's worlds starts.
 *
 * [DriverDestination.SIGN_IN] opens the language picker rather than the phone screen: a phone that
 * has just changed hands is the common reason to be here, and the new driver may not read the last
 * one's language.
 */
private fun DriverDestination.toRoute(): NavKey = when (this) {
    DriverDestination.SIGN_IN -> OnboardingRoute.Splash
    DriverDestination.ONBOARDING -> OnboardingRoute.SignUp
    DriverDestination.BLOCKED -> OnboardingRoute.Blocked
    DriverDestination.MAIN -> MainRoute()
}
