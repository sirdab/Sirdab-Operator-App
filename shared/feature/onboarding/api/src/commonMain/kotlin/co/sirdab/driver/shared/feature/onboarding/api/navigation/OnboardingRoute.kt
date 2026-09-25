package co.sirdab.driver.shared.feature.onboarding.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface OnboardingRoute : NavKey {
    @Serializable data object Splash : OnboardingRoute
    @Serializable data object PhoneEntry : OnboardingRoute
    @Serializable data class Otp(val phone: String) : OnboardingRoute

    /**
     * Sign-up's hub: what the server says is still missing, in any order.
     *
     * Carries no arguments. The profile is the state, the session is the identity, and both are
     * read fresh every time this opens.
     */
    @Serializable data object SignUp : OnboardingRoute

    /** Name, nationality and licence. */
    @Serializable data object SignUpDetails : OnboardingRoute

    /** The truck, or trucks. */
    @Serializable data object SignUpTruck : OnboardingRoute

    /** Everything handed over; ops has not approved it yet. */
    @Serializable data object UnderReview : OnboardingRoute

    /** Suspended by ops. */
    @Serializable data object Blocked : OnboardingRoute
}
