package co.sirdab.driver.shared.feature.onboarding.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.onboarding.api.navigation.OnboardingRoute
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.otp.OtpScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.phone.PhoneScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.splash.SplashScreen

/**
 * Three screens: pick a language, prove a phone, done.
 *
 * There is no sign-up wizard, because a driver does not sign themselves up. A dispatcher creates
 * the driver row against a phone number, and verifying that number is what links the two. Either
 * the code lands them in the shell or the OTP screen tells them their number was never added.
 */
fun EntryProviderScope<NavKey>.onboardingEntries(
    onNavigate: (OnboardingRoute) -> Unit,
    onFinished: () -> Unit,
) {
    entry<OnboardingRoute.Splash> {
        SplashScreen(onContinue = { onNavigate(OnboardingRoute.PhoneEntry) })
    }
    entry<OnboardingRoute.PhoneEntry> {
        PhoneScreen(onCodeSent = { phone -> onNavigate(OnboardingRoute.Otp(phone)) })
    }
    entry<OnboardingRoute.Otp> { route ->
        OtpScreen(phone = route.phone, onVerified = { onFinished() })
    }
}
