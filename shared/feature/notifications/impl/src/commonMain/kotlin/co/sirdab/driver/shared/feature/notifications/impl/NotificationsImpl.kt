package co.sirdab.driver.shared.feature.notifications.impl

import co.sirdab.driver.shared.feature.notifications.api.DeviceRegistry
import co.sirdab.driver.shared.feature.notifications.api.PushTokens
import co.sirdab.driver.shared.feature.notifications.impl.data.DeviceRegistryHttp
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Needs a [co.sirdab.driver.shared.feature.notifications.api.PushDevice], bound by the platform. */
val notificationsModule: Module = module {
    single { DeviceRegistryHttp(get()) } bind DeviceRegistry::class
    single { PushTokens() }
    // Eager: it watches the session from launch, so a driver already signed in registers too.
    single(createdAtStart = true) { PushRegistration(get(), get(), get(), get(), get()) }
}
