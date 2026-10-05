package co.sirdab.driver.di

import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.di.createTmsModules
import co.sirdab.driver.shared.feature.notifications.api.DevicePlatform
import co.sirdab.driver.shared.feature.notifications.api.PushDevice
import co.sirdab.driver.shared.feature.notifications.api.PushTokens
import io.ktor.client.plugins.logging.LogLevel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi

/** Where the app points comes from gradle.properties, through [IosBackendConfig]. */
@OptIn(ExperimentalNativeApi::class)
fun doInitKoin() {
    val platform = module {
        single {
            PushDevice(
                platform = DevicePlatform.IOS,
                appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String,
            )
        }
    }
    startKoin {
        modules(
            createTmsModules(
                TmsEnvironment(
                    apiBaseUrl = IosBackendConfig.API_BASE_URL,
                    supabaseUrl = IosBackendConfig.SUPABASE_URL,
                    supabaseAnonKey = IosBackendConfig.SUPABASE_ANON_KEY,
                ),
                // NSLog, so the same conversation is readable from Xcode and Console.app. Debug
                // binaries only: bodies carry the driver's national ID, licence and phone, and a
                // release build's NSLog is readable by anyone who plugs the phone in.
                logLevel = if (Platform.isDebugBinary) LogLevel.ALL else LogLevel.NONE,
            ) + platform,
        )
    }
}

/** Called from Swift with the FCM token, at launch and whenever Firebase rotates it. */
fun onPushToken(token: String) {
    KoinPlatform.getKoin().get<PushTokens>().update(token)
}
