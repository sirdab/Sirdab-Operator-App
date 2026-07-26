package co.sirdab.driver.shared.core.platform.locale

/** Platform device language subtag ("en", "ar", "ur", "hi", ...). */
expect fun deviceLanguage(): String

/**
 * Single source of truth for the effective app language across non-Compose layers.
 * The in-app language choice (LanguageStore) pushes an override here; everything else reads it.
 */
object AppLocale {
    private var override: String? = null
    fun setOverride(languageCode: String?) { override = languageCode }
    fun current(): String = override ?: deviceLanguage()
}

fun isRtlLanguage(code: String): Boolean = code == "ar" || code == "ur"

fun localizedText(en: String, ar: String): String =
    if (AppLocale.current() == "ar") ar else en
