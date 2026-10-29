package co.sirdab.driver.shared.feature.guide.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.guide.impl.presentation.GuideScreen
import kotlinx.serialization.Serializable

/**
 * How to use the app, a page at a time.
 *
 * [canBid] is the shell's answer for the Loads tab, taken when the guide opens. A driver whose
 * company bids for them has no board, and pages about one would send them looking for it.
 */
@Serializable
data class GuideRoute(val canBid: Boolean) : NavKey

/** Pushed over the shell: the first time a driver reaches it, and whenever Profile asks. */
fun EntryProviderScope<NavKey>.guideEntries(
    onClose: () -> Unit,
) {
    entry<GuideRoute> { route ->
        GuideScreen(canBid = route.canBid, onClose = onClose)
    }
}
