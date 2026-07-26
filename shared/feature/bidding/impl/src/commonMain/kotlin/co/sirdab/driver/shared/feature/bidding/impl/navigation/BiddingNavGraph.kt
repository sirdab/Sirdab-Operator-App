package co.sirdab.driver.shared.feature.bidding.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.feature.bidding.api.BiddingRoute
import co.sirdab.driver.shared.feature.bidding.impl.presentation.BidComposerScreen
import co.sirdab.driver.shared.feature.bidding.impl.presentation.MyBidsScreen

fun EntryProviderScope<NavKey>.biddingEntries(
    onBack: () -> Unit,
    onBidPlaced: () -> Unit,
) {
    entry<BiddingRoute.Compose> { route ->
        BidComposerScreen(loadId = route.loadId, onBack = onBack, onSubmitted = onBidPlaced)
    }
    entry<BiddingRoute.MyBids> {
        MyBidsScreen()
    }
}
