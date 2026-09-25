package co.sirdab.driver

import android.app.Application
import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.di.createDemoModules
import co.sirdab.driver.shared.di.createTmsModules
import io.ktor.client.plugins.logging.LogLevel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DriverApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val modules = if (BuildConfig.DRIVER_BACKEND == "tms") {
            createTmsModules(
                environment = TmsEnvironment(
                    apiBaseUrl = BuildConfig.TMS_API_BASE_URL,
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
                ),
                // Bodies too, not just headers: a 400 from the API says which field it
                // rejected, and that is in the body. Credentials are sanitized out and
                // photo uploads are filtered, so what lands in logcat is readable.
                // Filter it with: adb logcat -s TmsApi:D
                //
                // Driven by `driver.httpLog`, not by the build type: the builds being tested
                // against the real stack are not always debuggable ones, and a release build that
                // cannot say what it sent is a release build nobody can help with.
                logLevel = if (BuildConfig.HTTP_LOG) LogLevel.ALL else LogLevel.NONE,
            )
        } else {
            createDemoModules()
        }

        startKoin {
            androidContext(this@DriverApp)
            modules(modules)
        }
    }
}
