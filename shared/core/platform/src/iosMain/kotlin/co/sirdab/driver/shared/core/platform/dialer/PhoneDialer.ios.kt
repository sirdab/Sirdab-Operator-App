package co.sirdab.driver.shared.core.platform.dialer

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosPhoneDialer : PhoneDialer {

    override fun dial(phoneNumber: String) {
        val url = NSURL.URLWithString("tel://$phoneNumber") ?: return
        // False on an iPad or the simulator, where there is no phone to hand it to.
        if (!UIApplication.sharedApplication.canOpenURL(url)) return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
    }
}

actual fun platformDialerModule(): Module = module {
    single<PhoneDialer> { IosPhoneDialer() }
}
