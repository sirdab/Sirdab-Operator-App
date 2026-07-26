package co.sirdab.driver.shared.core.preferences.locale

import co.sirdab.driver.shared.core.platform.locale.AppLocale
import co.sirdab.driver.shared.core.platform.locale.deviceLanguage
import co.sirdab.driver.shared.core.preferences.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppLanguage(val code: String, val displayName: String) {
    ARABIC("ar", "العربية"),
    ENGLISH("en", "English"),
    URDU("ur", "اردو"),
    HINDI("hi", "हिन्दी");

    val isRtl: Boolean get() = this == ARABIC || this == URDU

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

private const val LANGUAGE_KEY = "app_language"

/**
 * Single source of truth for the in-app language choice. Direction and font family derive from
 * this, not the device locale (plan §9). Persists to DataStore; pushes an override into AppLocale
 * so non-Compose layers follow. Eagerly created at Koin startup.
 */
class LanguageStore(private val kv: KeyValueStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _language = MutableStateFlow(AppLanguage.fromCode(deviceLanguage()))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    init {
        AppLocale.setOverride(_language.value.code)
        scope.launch {
            val persisted = kv.getString(LANGUAGE_KEY)
            if (persisted != null) {
                val lang = AppLanguage.fromCode(persisted)
                AppLocale.setOverride(lang.code)
                _language.value = lang
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        AppLocale.setOverride(language.code)
        _language.value = language
        scope.launch { kv.putString(LANGUAGE_KEY, language.code) }
    }
}
