package co.sirdab.driver.shared.feature.bidding.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.BidBreakdown
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.feature.bidding.api.BidCalculator
import co.sirdab.driver.shared.feature.bidding.api.BidRepository
import co.sirdab.driver.shared.feature.loadboard.api.LoadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BidComposerUiState(
    val load: Load? = null,
    val rateSar: Int = 0,
    val breakdown: BidBreakdown? = null,
    val minRate: Int = 0,
    val maxRate: Int = 0,
    val isSubmitting: Boolean = false,
)

class BidComposerViewModel(
    private val loadId: String,
    private val loadRepository: LoadRepository,
    private val bidRepository: BidRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BidComposerUiState())
    val state: StateFlow<BidComposerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val result = loadRepository.loadDetail(loadId)
            val load = (result as? AppResult.Success)?.data ?: return@launch
            val start = load.fixedRateSar ?: load.suggestedRateSar
            _state.value = BidComposerUiState(
                load = load,
                rateSar = start,
                breakdown = BidCalculator.breakdown(start, load.distanceKm, load.suggestedRateSar),
                minRate = (load.suggestedRateSar * 0.7).toInt(),
                maxRate = (load.suggestedRateSar * 1.4).toInt(),
            )
        }
    }

    fun onRateChange(rate: Int) {
        val load = _state.value.load ?: return
        _state.value = _state.value.copy(
            rateSar = rate,
            breakdown = BidCalculator.breakdown(rate, load.distanceKm, load.suggestedRateSar),
        )
    }

    fun submit(onSubmitted: () -> Unit) {
        val current = _state.value
        _state.value = current.copy(isSubmitting = true)
        viewModelScope.launch {
            bidRepository.placeBid(loadId, current.rateSar, null)
            _state.value = _state.value.copy(isSubmitting = false)
            onSubmitted()
        }
    }
}
