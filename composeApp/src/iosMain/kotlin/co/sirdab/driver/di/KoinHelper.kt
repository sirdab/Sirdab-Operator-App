package co.sirdab.driver.di

import co.sirdab.driver.shared.di.createDemoModules
import org.koin.core.context.startKoin

fun doInitKoin() {
    startKoin {
        modules(createDemoModules())
    }
}
