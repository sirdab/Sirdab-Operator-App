package co.sirdab.driver.shared.core.preferences.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun preferencesPlatformModule(): Module = module {
    single<DataStore<Preferences>> {
        val context = androidContext()
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                context.filesDir.resolve("driver.preferences_pb").absolutePath.toPath()
            },
        )
    }
}
