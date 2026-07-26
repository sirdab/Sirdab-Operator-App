package co.sirdab.driver.shared.feature.loadboard.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.feature.loadboard.api.LoadRepository
import co.sirdab.driver.shared.feature.notifications.api.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoadBoardUiState(
    val loads: List<Load> = emptyList(),
    val isRefreshing: Boolean = false,
    val filterVehicle: VehicleType? = null,
)

class LoadBoardViewModel(
    private val loadRepository: LoadRepository,
    notificationRepository: NotificationRepository,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val filter = MutableStateFlow<VehicleType?>(null)

    val unreadCount = notificationRepository.unreadCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val state = combine(loadRepository.observeLoads(), filter, isRefreshing) { loads, f, refreshing ->
        LoadBoardUiState(
            loads = loads.filter { f == null || it.requiredVehicle == f },
            isRefreshing = refreshing,
            filterVehicle = f,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadBoardUiState())

    fun setFilter(vehicle: VehicleType?) { filter.value = vehicle }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            loadRepository.refresh()
            isRefreshing.value = false
        }
    }
}
