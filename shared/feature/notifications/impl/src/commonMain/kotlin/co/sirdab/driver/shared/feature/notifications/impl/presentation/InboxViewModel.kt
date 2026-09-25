package co.sirdab.driver.shared.feature.notifications.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.feature.notifications.api.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(private val repository: NotificationRepository) : ViewModel() {
    val notifications = repository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<AppNotification>())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun markRead(id: String) {
        viewModelScope.launch { repository.markRead(id) }
    }

    /** The list itself arrives through [notifications], so nothing is assigned here. */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            repository.refresh()
            _isRefreshing.value = false
        }
    }
}
