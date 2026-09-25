package co.sirdab.driver.shared.core.network.di

import co.sirdab.driver.shared.core.network.BackendMode
import co.sirdab.driver.shared.core.network.FileUploader
import co.sirdab.driver.shared.core.network.ServerClock
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.core.network.TokenProvider
import co.sirdab.driver.shared.core.network.platformHttpLogger
import co.sirdab.driver.shared.core.network.tmsHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.EMPTY
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * [environment] is passed in rather than read from a build config here, so the app module decides
 * where a build points and this module stays testable.
 */
fun networkModule(
    environment: TmsEnvironment,
    logLevel: LogLevel = LogLevel.NONE,
): Module = module {
    single { BackendMode.TMS }
    single { environment }
    single { ServerClock() }
    single<HttpClient> { tmsHttpClient(environment, logLevel) }
    single {
        TmsApiClient(
            http = get(),
            tokens = get(),
            serverClock = get(),
            // The upload lines follow the same switch as the request log, so a build with logging
            // off says nothing at all rather than still printing every signed upload URL.
            logger = if (logLevel == LogLevel.NONE) Logger.EMPTY else platformHttpLogger(),
        )
    }
    single { FileUploader(get()) }
}

/** Demo mode still needs the graph to resolve, but nothing should reach the network. */
val anonymousTokenModule: Module = module {
    single { BackendMode.DEMO }
    single<TokenProvider> { TokenProvider.Anonymous }
}
