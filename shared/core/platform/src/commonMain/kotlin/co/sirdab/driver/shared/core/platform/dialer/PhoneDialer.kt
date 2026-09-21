package co.sirdab.driver.shared.core.platform.dialer

import org.koin.core.module.Module

/**
 * Opens the phone's dialer on a number.
 *
 * Deliberately the dialer and not a placed call: the driver sees the number and presses the button,
 * so a stray tap in a moving truck cannot dial a customer. It also means the app needs no call
 * permission on either platform.
 */
interface PhoneDialer {
    fun dial(phoneNumber: String)
}

/** Platform binding for [PhoneDialer], wired into Koin like the notifier. */
expect fun platformDialerModule(): Module
