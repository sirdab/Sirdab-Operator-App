package co.sirdab.driver.shared.feature.notifications.impl

import co.sirdab.driver.shared.feature.notifications.api.DeviceRegistry
import co.sirdab.driver.shared.feature.notifications.impl.data.DeviceRegistryHttp
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

val notificationsModule: Module = module {
    single { DeviceRegistryHttp(get()) } bind DeviceRegistry::class
}
