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

    /**
     * Ask the server for the inbox again, which is what pulling the list down does.
     *
     * The contract has no notifications route yet, so the only implementation holds the list
     * locally and has nothing to fetch. The seam is here rather than in the screen so that adding
     * one later is a binding, not a change to the inbox.
     */
    suspend fun refresh()
}
