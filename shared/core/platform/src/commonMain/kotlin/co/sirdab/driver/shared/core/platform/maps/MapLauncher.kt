package co.sirdab.driver.shared.core.platform.maps

import org.koin.core.module.Module

/**
 * Hands a stop's coordinates to whatever the driver navigates with.
 *
 * Coordinates rather than the written address: a Saudi address is a label, a district and a
 * building number, and searching for one lands a lorry in the wrong Khumrah. The server sends a
 * pin because someone put it there, and the pin is the only part of an address that cannot be
 * misread at speed.
 *
 * Deliberately not a map in the app. A driver already has the navigation app they trust, with
 * their own traffic, their own voice and their own truck settings; the app's job is to hand the
 * destination over and get out of the way.
 */
interface MapLauncher {
    /**
     * Open directions to a point.
     *
     * [label] names the pin where the platform can show one, so the driver sees "Jeddah warehouse"
     * rather than a pair of numbers when they arrive at the chooser.
     */
    fun navigateTo(lat: Double, lng: Double, label: String? = null)
}

/** Platform binding for [MapLauncher], wired into Koin like the dialer and the browser. */
expect fun platformMapLauncherModule(): Module
