package co.sirdab.driver.shared.feature.notifications.impl

import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.feature.notifications.api.NotificationRepository
import co.sirdab.driver.shared.feature.notifications.impl.presentation.InboxViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

class NotificationRepositoryMock(private val world: DemoWorld) : NotificationRepository {
    override fun observe(): Flow<List<AppNotification>> =
        world.state.map { it.notifications.sortedByDescending { n -> n.createdAtMillis } }

    override fun unreadCount(): Flow<Int> =
        world.state.map { w -> w.notifications.count { !it.read } }

    override suspend fun markRead(id: String) {
        world.update { w -> w.copy(notifications = w.notifications.map { if (it.id == id) it.copy(read = true) else it }) }
    }
}

val notificationsModule: Module = module {
    single { NotificationRepositoryMock(get()) } bind NotificationRepository::class
    viewModelOf(::InboxViewModel)
}
