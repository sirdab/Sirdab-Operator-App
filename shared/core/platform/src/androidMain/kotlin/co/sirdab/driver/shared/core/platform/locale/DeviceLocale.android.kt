package co.sirdab.driver.shared.core.platform.locale

import java.util.Locale

actual fun deviceLanguage(): String = Locale.getDefault().language.lowercase()
