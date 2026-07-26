package co.sirdab.driver.shared.feature.loadboard.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.loadboard.api.LoadboardRoute
import co.sirdab.driver.shared.feature.loadboard.impl.presentation.LoadDetailScreen

/**
 * The load board itself is hosted inside MainShell's Loads tab; only Load detail is pushed onto
 * the back stack over the shell.
 */
fun EntryProviderScope<NavKey>.loadboardEntries(
    onBack: () -> Unit,
    onPlaceBid: (String) -> Unit,
) {
    entry<LoadboardRoute.LoadDetail> { route ->
        LoadDetailScreen(loadId = route.loadId, onBack = onBack, onPlaceBid = onPlaceBid)
    }
}
