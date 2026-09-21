package co.sirdab.driver

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class MainRoute(val tab: MainTab = MainTab.LOADS) : NavKey

@Serializable
enum class MainTab { LOADS, TRIP, PROFILE }
