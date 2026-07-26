package co.sirdab.driver.locale

import platform.Foundation.NSUserDefaults

actual fun applyPlatformLocale(languageCode: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(languageCode), forKey = "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}

actual val languageChangeRequiresRestart: Boolean = true
