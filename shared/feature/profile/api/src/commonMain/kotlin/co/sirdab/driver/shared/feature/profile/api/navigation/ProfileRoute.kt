package co.sirdab.driver.shared.feature.profile.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface ProfileRoute : NavKey {
    @Serializable data object Overview : ProfileRoute
    @Serializable data object DocumentVault : ProfileRoute
    @Serializable data object History : ProfileRoute
}
