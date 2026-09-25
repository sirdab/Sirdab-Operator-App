package co.sirdab.driver.shared.feature.onboarding.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverDestination
import co.sirdab.driver.shared.feature.onboarding.api.navigation.OnboardingRoute
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.otp.OtpScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.phone.PhoneScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.splash.SplashScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup.BlockedScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup.SignUpChecklistScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup.SignUpDetailsScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup.SignUpTruckScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.signup.UnderReviewScreen

/**
 * Pick a language, prove a phone, then fill in whatever the profile says is missing.
 *
 * There is no sign-up wizard with a fixed order any more. Verifying a code reads the driver's
 * profile — which is also what creates it — and the server's own list of what is still missing
 * decides both whether these screens appear at all and what they ask for.
 *
 * [onDestination] is how the flow hands back to the app: every one of these screens can end in a
 * different place depending on what the server said, so none of them navigates to the shell itself.
 */
fun EntryProviderScope<NavKey>.onboardingEntries(
    onNavigate: (OnboardingRoute) -> Unit,
    onBack: () -> Unit,
    onDestination: (DriverDestination) -> Unit,
) {
    entry<OnboardingRoute.Splash> {
        SplashScreen(onContinue = { onNavigate(OnboardingRoute.PhoneEntry) })
    }
    entry<OnboardingRoute.PhoneEntry> {
        PhoneScreen(onCodeSent = { phone -> onNavigate(OnboardingRoute.Otp(phone)) })
    }
    entry<OnboardingRoute.Otp> { route ->
        OtpScreen(
            phone = route.phone,
            onVerified = onDestination,
            onChangeNumber = onBack,
        )
    }
    entry<OnboardingRoute.SignUp> {
        SignUpChecklistScreen(
            onOpenDetails = { onNavigate(OnboardingRoute.SignUpDetails) },
            onOpenTruck = { onNavigate(OnboardingRoute.SignUpTruck) },
            onCompleted = onDestination,
        )
    }
    entry<OnboardingRoute.SignUpDetails> {
        SignUpDetailsScreen(onSaved = onBack)
    }
    entry<OnboardingRoute.SignUpTruck> {
        SignUpTruckScreen(onSaved = onBack)
    }
    entry<OnboardingRoute.UnderReview> {
        UnderReviewScreen(
            onApproved = onBack,
            onFixRejected = { onNavigate(OnboardingRoute.SignUp) },
        )
    }
    entry<OnboardingRoute.Blocked> {
        BlockedScreen(onSignedOut = { onDestination(DriverDestination.SIGN_IN) })
    }
}
