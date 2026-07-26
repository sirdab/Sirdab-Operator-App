package co.sirdab.driver.shared.core.preferences.di

import co.sirdab.driver.shared.core.preferences.KeyValueStore
import co.sirdab.driver.shared.core.preferences.locale.LanguageStore
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/** Platform binding that provides the DataStore<Preferences> instance. */
expect fun preferencesPlatformModule(): Module

val preferencesModule: Module = module {
    single { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    single { KeyValueStore(get()) }
    // Eager: seed AppLocale from the persisted choice before the first UI frame.
    single(createdAtStart = true) { LanguageStore(get()) }
}
