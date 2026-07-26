package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.Bid
import co.sirdab.driver.shared.core.model.CounterAction
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.feature.bidding.api.BidRepository
import co.sirdab.driver.shared.feature.loadboard.api.LoadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MyBidRow(val bid: Bid, val load: Load?)

data class MyBidsUiState(val rows: List<MyBidRow> = emptyList())

class MyBidsViewModel(
    private val bidRepository: BidRepository,
    loadRepository: LoadRepository,
) : ViewModel() {

    val state = combine(
        bidRepository.observeMyBids(),
        loadRepository.observeLoads(),
    ) { bids, loads ->
        val byId = loads.associateBy { it.id }
        MyBidsUiState(
            rows = bids.sortedByDescending { it.updatedAtMillis }
                .map { MyBidRow(it, byId[it.loadId]) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MyBidsUiState())

    fun accept(bidId: String) {
        viewModelScope.launch { bidRepository.respondToCounter(bidId, CounterAction.ACCEPT) }
    }

    fun reject(bidId: String) {
        viewModelScope.launch { bidRepository.respondToCounter(bidId, CounterAction.REJECT) }
    }
}
