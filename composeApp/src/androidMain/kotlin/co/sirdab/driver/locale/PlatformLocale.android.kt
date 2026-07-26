package co.sirdab.driver.locale

import android.os.Build
import android.os.LocaleList
import java.util.Locale

actual fun applyPlatformLocale(languageCode: String) {
    val locale = Locale.forLanguageTag(languageCode)
    Locale.setDefault(locale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        LocaleList.setDefault(LocaleList(locale))
    }
}

actual val languageChangeRequiresRestart: Boolean = false
