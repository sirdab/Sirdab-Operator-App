package co.sirdab.driver.shared.feature.notifications.api

import co.sirdab.driver.shared.core.model.AppResult

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

/**
 * Supplies the push token from FCM or APNs.
 *
 * No implementation yet: the project has no Firebase configuration and no APNs entitlement, so
 * there is nothing to ask. [DeviceRegistry] is complete and exercised independently of this.
 */
interface PushTokenProvider {
    suspend fun currentToken(): String?
}
