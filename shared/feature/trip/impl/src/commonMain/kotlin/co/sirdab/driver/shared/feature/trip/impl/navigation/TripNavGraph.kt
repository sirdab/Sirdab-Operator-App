package co.sirdab.driver.shared.feature.trip.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.trip.api.TripRoute
import co.sirdab.driver.shared.feature.trip.impl.presentation.DriverTripDetailScreen

/**
 * The trip tab itself is hosted in MainShell; everything below is pushed over the shell.
 */
fun EntryProviderScope<NavKey>.tripEntries(
    onBack: () -> Unit,
) {
    entry<TripRoute.Detail> { route ->
        DriverTripDetailScreen(tripId = route.tripId, onBack = onBack)
    }
}
