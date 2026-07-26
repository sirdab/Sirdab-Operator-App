package co.sirdab.driver.shared.feature.bidding.api

import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Bid
import co.sirdab.driver.shared.core.model.BidEvent
import co.sirdab.driver.shared.core.model.CounterAction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface BiddingRoute : NavKey {
    @Serializable data class Compose(val loadId: String) : BiddingRoute
    @Serializable data object MyBids : BiddingRoute
}

interface BidRepository {
    suspend fun placeBid(loadId: String, amountSar: Int, note: String?): AppResult<Bid>
    suspend fun respondToCounter(bidId: String, action: CounterAction): AppResult<Bid>
    fun observeMyBids(): Flow<List<Bid>>
    fun observeBidEvents(): Flow<BidEvent> // Outbid, Awarded, Rejected, Countered
}
