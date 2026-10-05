package co.sirdab.driver

import android.app.Application
import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.di.createTmsModules
import io.ktor.client.plugins.logging.LogLevel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DriverApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val modules = createTmsModules(
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
            // Driven by `driver.httpLog` (debug) and `driver.httpLog.release` (release, off
            // unless set) rather than by the build type alone: a release build handed to a
            // tester can be made to say what it sent, and a store build never does.
            logLevel = if (BuildConfig.HTTP_LOG) LogLevel.ALL else LogLevel.NONE,
        )

        startKoin {
            androidContext(this@DriverApp)
            modules(modules)
        }
    }
}
