package co.sirdab.driver.shared.feature.onboarding.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface OnboardingRoute : NavKey {
    @Serializable data object Splash : OnboardingRoute
    @Serializable data object PhoneEntry : OnboardingRoute
    @Serializable data class Otp(val phone: String) : OnboardingRoute
}
