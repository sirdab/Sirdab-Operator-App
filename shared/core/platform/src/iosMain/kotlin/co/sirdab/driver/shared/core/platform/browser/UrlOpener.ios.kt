package co.sirdab.driver.shared.core.platform.browser

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosUrlOpener : UrlOpener {

    override fun open(url: String) {
        val target = NSURL.URLWithString(url) ?: return
        if (!UIApplication.sharedApplication.canOpenURL(target)) return
        UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any?>(), null)
    }
}

actual fun platformUrlOpenerModule(): Module = module {
    single<UrlOpener> { IosUrlOpener() }
}
