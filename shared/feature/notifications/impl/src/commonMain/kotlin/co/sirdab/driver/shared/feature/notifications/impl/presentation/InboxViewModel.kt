package co.sirdab.driver.shared.feature.notifications.impl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.feature.notifications.api.NotificationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(private val repository: NotificationRepository) : ViewModel() {
    val notifications = repository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<AppNotification>())

    fun markRead(id: String) {
        viewModelScope.launch { repository.markRead(id) }
    }
}
