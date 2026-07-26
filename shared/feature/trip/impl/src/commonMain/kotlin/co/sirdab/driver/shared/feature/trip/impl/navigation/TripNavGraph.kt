package co.sirdab.driver.shared.feature.trip.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.trip.api.TripRoute
import co.sirdab.driver.shared.feature.trip.impl.presentation.PodScreen

/** Active trip is hosted in MainShell's Trip tab; only POD capture is pushed over the shell. */
fun EntryProviderScope<NavKey>.tripEntries(
    onBack: () -> Unit,
    onPodDone: () -> Unit,
) {
    entry<TripRoute.Pod> { route ->
        PodScreen(tripId = route.tripId, onBack = onBack, onDone = onPodDone)
    }
}
