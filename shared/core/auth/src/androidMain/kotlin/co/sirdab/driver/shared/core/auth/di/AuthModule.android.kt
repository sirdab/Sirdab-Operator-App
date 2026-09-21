package co.sirdab.driver.shared.core.auth.di

import co.sirdab.driver.shared.core.auth.AndroidSecureStore
import co.sirdab.driver.shared.core.auth.SecureStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun authPlatformModule(): Module = module {
    single<SecureStore> { AndroidSecureStore(androidContext()) }
}
