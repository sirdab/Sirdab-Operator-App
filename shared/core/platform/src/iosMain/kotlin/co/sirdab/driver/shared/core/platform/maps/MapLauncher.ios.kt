package co.sirdab.driver.shared.core.platform.maps

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosMapLauncher : MapLauncher {

    override fun navigateTo(lat: Double, lng: Double, label: String?) {
        // Google Maps first, because it is what drivers here actually use. `canOpenURL` answers
        // true only when `comgooglemaps` is listed in LSApplicationQueriesSchemes, so without that
        // Info.plist entry this falls through to Apple Maps rather than failing.
        if (open("comgooglemaps://?daddr=$lat,$lng&directionsmode=driving")) return
        open("http://maps.apple.com/?daddr=$lat,$lng&dirflg=d")
    }

    private fun open(url: String): Boolean {
        val target = NSURL.URLWithString(url) ?: return false
        if (!UIApplication.sharedApplication.canOpenURL(target)) return false
        UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any?>(), null)
        return true
    }
}

actual fun platformMapLauncherModule(): Module = module {
    single<MapLauncher> { IosMapLauncher() }
}
