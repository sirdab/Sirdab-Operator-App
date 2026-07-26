package co.sirdab.driver.shared.feature.profile.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.model.AutoBidRule
import co.sirdab.driver.shared.core.model.City
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.feature.bidding.api.BidRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AutoBidUiState(
    val cities: List<City> = emptyList(),
    val originId: String = "riyadh",
    val destId: String = "dammam",
    val vehicle: VehicleType = VehicleType.FLATBED,
    val maxRate: String = "1800",
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = originId != destId && maxRate.toIntOrNull() != null
}

class AutoBidViewModel(
    private val demoWorld: DemoWorld,
    private val bidRepository: BidRepository,
) : ViewModel() {

    private val _state: MutableStateFlow<AutoBidUiState>

    init {
        val world = demoWorld.state.value
        val rule = world.autoBidRule
        _state = MutableStateFlow(
            AutoBidUiState(
                cities = world.cities,
                originId = rule?.originCityId ?: "riyadh",
                destId = rule?.destinationCityId ?: "dammam",
                vehicle = rule?.vehicle ?: VehicleType.FLATBED,
                maxRate = (rule?.maxRateSar ?: 1800).toString(),
                saved = rule?.enabled == true,
            ),
        )
    }

    val state: StateFlow<AutoBidUiState> = _state.asStateFlow()

    fun onOrigin(id: String) { _state.value = _state.value.copy(originId = id, saved = false) }
    fun onDest(id: String) { _state.value = _state.value.copy(destId = id, saved = false) }
    fun onVehicle(v: VehicleType) { _state.value = _state.value.copy(vehicle = v, saved = false) }
    fun onMaxRate(v: String) { _state.value = _state.value.copy(maxRate = v.filter { it.isDigit() }, saved = false) }

    fun save() {
        val s = _state.value
        val max = s.maxRate.toIntOrNull() ?: return
        val rule = AutoBidRule(enabled = true, originCityId = s.originId, destinationCityId = s.destId, vehicle = s.vehicle, maxRateSar = max)
        demoWorld.saveAutoBid(rule)
        _state.value = s.copy(saved = true)
        // Demo: immediately auto-bid on the first matching load already on the board.
        viewModelScope.launch {
            val match = demoWorld.state.value.loads.firstOrNull {
                it.originCityId == rule.originCityId &&
                    it.destinationCityId == rule.destinationCityId &&
                    it.requiredVehicle == rule.vehicle &&
                    (it.fixedRateSar ?: it.suggestedRateSar) <= rule.maxRateSar
            }
            if (match != null) {
                val amount = minOf(match.fixedRateSar ?: match.suggestedRateSar, rule.maxRateSar)
                bidRepository.placeBid(match.id, amount, "Auto-bid")
            }
        }
    }
}
