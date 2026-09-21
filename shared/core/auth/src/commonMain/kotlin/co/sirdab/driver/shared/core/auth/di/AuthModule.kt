package co.sirdab.driver.shared.core.auth.di

import co.sirdab.driver.shared.core.auth.SecureStore
import co.sirdab.driver.shared.core.auth.SessionManager
import co.sirdab.driver.shared.core.auth.SupabaseAuthApi
import co.sirdab.driver.shared.core.network.TokenProvider
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Platform binding that provides the [SecureStore] implementation. */
expect fun authPlatformModule(): Module

val authModule: Module = module {
    single { SupabaseAuthApi(http = get(), environment = get()) }
    single { SessionManager(api = get(), store = get()) } bind TokenProvider::class
}
