package co.sirdab.driver.shared.core.platform.notification

import org.koin.core.module.Module

/**
 * Fires real local (OS) notifications. Backed by the platform notification centre:
 * framework NotificationManager on Android, UNUserNotificationCenter on iOS.
 * Simulation actors call this to make "you've been outbid", backhaul, etc. arrive live.
 */
interface Notifier {
    fun post(id: String, title: String, body: String)
    /** Request OS permission if required (Android 13+, iOS). Safe to call repeatedly. */
    suspend fun ensurePermission(): Boolean
}

/** Platform binding for [Notifier], wired into Koin like the persistence factory. */
expect fun platformNotifierModule(): Module
