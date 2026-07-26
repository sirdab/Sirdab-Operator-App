package co.sirdab.driver.shared.feature.notifications.api

import androidx.navigation3.runtime.NavKey
import co.sirdab.driver.shared.core.model.AppNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface NotificationsRoute : NavKey {
    @Serializable data object Inbox : NotificationsRoute
}

interface NotificationRepository {
    fun observe(): Flow<List<AppNotification>>
    fun unreadCount(): Flow<Int>
    suspend fun markRead(id: String)
}
