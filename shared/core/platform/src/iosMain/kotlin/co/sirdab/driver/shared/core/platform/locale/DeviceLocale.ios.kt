package co.sirdab.driver.shared.core.platform.locale

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun deviceLanguage(): String {
    val tag = NSLocale.preferredLanguages.firstOrNull() as? String ?: return "en"
    return tag.substringBefore('-').substringBefore('_').lowercase()
}
