package co.sirdab.driver.shared.feature.profile.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.profile.api.navigation.ProfileRoute
import co.sirdab.driver.shared.feature.profile.impl.presentation.HistoryScreen

/** Profile overview is hosted in MainShell's Profile tab; sub-screens are pushed over the shell. */
fun EntryProviderScope<NavKey>.profileEntries(
    onBack: () -> Unit,
) {
    entry<ProfileRoute.History> { HistoryScreen(onBack = onBack) }
}
