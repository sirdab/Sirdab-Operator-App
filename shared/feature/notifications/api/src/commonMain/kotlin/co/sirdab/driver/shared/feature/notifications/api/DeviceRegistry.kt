package co.sirdab.driver.shared.feature.notifications.api

import co.sirdab.driver.shared.core.model.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DevicePlatform(val wire: String) { ANDROID("android"), IOS("ios") }

/**
 * Registers this phone for push.
 *
 * The TMS keys a device by its push token and binds it to the signed-in person, so registering a
 * token that already exists moves it: the previous holder stops receiving that phone's
 * notifications. That is the intended answer to a reinstall and to a phone changing hands, and it
 * is why this runs on every sign-in and on every token change, not once at install.
 */
interface DeviceRegistry {
    suspend fun register(
        platform: DevicePlatform,
        pushToken: String,
        appVersion: String?,
        locale: String?,
    ): AppResult<Unit>
}

/** What this install is, as the device registry wants it. Bound by each platform's entry point. */
data class PushDevice(val platform: DevicePlatform, val appVersion: String?)

/**
 * The current Firebase Cloud Messaging token.
 *
 * The platform writes it: on Android the messaging service and the first token fetch, on iOS the
 * Firebase messaging delegate. FCM on both, so the server sends to one provider either way.
 */
class PushTokens {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    fun update(token: String) {
        if (token.isNotBlank()) _token.value = token
    }
}
