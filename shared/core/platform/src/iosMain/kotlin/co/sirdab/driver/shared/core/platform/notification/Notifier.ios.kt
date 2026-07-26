package co.sirdab.driver.shared.core.platform.notification

import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

class IosNotifier : Notifier {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun post(id: String, title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(0.1, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    override suspend fun ensurePermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ ->
            cont.resume(granted)
        }
    }
}

actual fun platformNotifierModule(): Module = module {
    single<Notifier> { IosNotifier() }
}
