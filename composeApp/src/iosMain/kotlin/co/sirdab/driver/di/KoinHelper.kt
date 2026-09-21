package co.sirdab.driver.di

import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.di.createDemoModules
import co.sirdab.driver.shared.di.createTmsModules
import org.koin.core.context.startKoin

fun doInitKoin() {
    startKoin {
        modules(createDemoModules())
    }
}

/**
 * The Swift side passes these because iOS has no equivalent of Android's generated BuildConfig
 * here; they come from the Xcode configuration.
 */
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
            ),
        )
    }
}
