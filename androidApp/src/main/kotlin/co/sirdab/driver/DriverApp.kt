package co.sirdab.driver

import android.app.Application
import co.sirdab.driver.shared.di.createDemoModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DriverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DriverApp)
            modules(createDemoModules())
        }
    }
}
