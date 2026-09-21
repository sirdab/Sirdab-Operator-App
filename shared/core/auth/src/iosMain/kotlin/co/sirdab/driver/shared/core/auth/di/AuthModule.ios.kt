package co.sirdab.driver.shared.core.auth.di

import co.sirdab.driver.shared.core.auth.KeychainSecureStore
import co.sirdab.driver.shared.core.auth.SecureStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun authPlatformModule(): Module = module {
    single<SecureStore> { KeychainSecureStore() }
}
