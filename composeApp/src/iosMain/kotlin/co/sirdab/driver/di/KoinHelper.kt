package co.sirdab.driver.di

import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.di.createDemoModules
import co.sirdab.driver.shared.di.createTmsModules
import io.ktor.client.plugins.logging.LogLevel
import org.koin.core.context.startKoin
import kotlin.experimental.ExperimentalNativeApi

fun doInitKoin() {
    startKoin {
        modules(createDemoModules())
    }
}

/**
 * The Swift side passes these because iOS has no equivalent of Android's generated BuildConfig
 * here; they come from the Xcode configuration.
 */
@OptIn(ExperimentalNativeApi::class)
fun doInitKoinWithTms(
    apiBaseUrl: String,
    supabaseUrl: String,
    supabaseAnonKey: String,
) {
    startKoin {
        modules(
            createTmsModules(
                TmsEnvironment(
                    apiBaseUrl = apiBaseUrl,
                    supabaseUrl = supabaseUrl,
                    supabaseAnonKey = supabaseAnonKey,
                ),
                // NSLog, so the same conversation is readable from Xcode and Console.app. Debug
                // binaries only: bodies carry the driver's national ID, licence and phone, and a
                // release build's NSLog is readable by anyone who plugs the phone in.
                logLevel = if (Platform.isDebugBinary) LogLevel.ALL else LogLevel.NONE,
            ),
        )
    }
}
