package co.sirdab.driver.shared.feature.loadboard.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.core.model.Shipper
import co.sirdab.driver.shared.feature.loadboard.api.LoadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoadDetailUiState(
    val load: Load? = null,
    val shipper: Shipper? = null,
    val isLoading: Boolean = true,
)

class LoadDetailViewModel(
    private val loadId: String,
    private val loadRepository: LoadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoadDetailUiState())
    val state: StateFlow<LoadDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val load = loadRepository.loadDetail(loadId)
            val loadData = (load as? co.sirdab.driver.shared.core.model.AppResult.Success)?.data
            val shipper = loadData?.let { loadRepository.shipper(it.shipperId) }
            _state.value = LoadDetailUiState(load = loadData, shipper = shipper, isLoading = false)
        }
    }
}
