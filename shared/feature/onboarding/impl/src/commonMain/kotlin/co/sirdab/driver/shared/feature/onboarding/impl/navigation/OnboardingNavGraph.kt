package co.sirdab.driver.shared.feature.onboarding.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.onboarding.api.navigation.OnboardingRoute
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.docs.DocUploadScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.nafath.NafathScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.otp.OtpScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.phone.PhoneScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.splash.SplashScreen
import co.sirdab.driver.shared.feature.onboarding.impl.presentation.vehicle.VehicleScreen

/**
 * Registers the 6 onboarding screens. The host owns the back stack; navigation is delivered via
 * the callbacks (reference `*Entries` convention).
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
        OtpScreen(phone = route.phone, onVerified = { onNavigate(OnboardingRoute.Nafath) })
    }
    entry<OnboardingRoute.Nafath> {
        NafathScreen(onVerified = { onNavigate(OnboardingRoute.DocUpload) })
    }
    entry<OnboardingRoute.DocUpload> {
        DocUploadScreen(onDone = { onNavigate(OnboardingRoute.VehicleReg) })
    }
    entry<OnboardingRoute.VehicleReg> {
        VehicleScreen(onFinished = onFinished)
    }
}
