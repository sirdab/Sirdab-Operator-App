package co.sirdab.driver.shared.di

import co.sirdab.driver.shared.core.auth.di.authModule
import co.sirdab.driver.shared.core.auth.di.authPlatformModule
import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.core.network.di.networkModule
import co.sirdab.driver.shared.core.platform.browser.platformUrlOpenerModule
import co.sirdab.driver.shared.core.platform.dialer.platformDialerModule
import co.sirdab.driver.shared.core.platform.files.platformFileStoreModule
import co.sirdab.driver.shared.core.platform.maps.platformMapLauncherModule
import co.sirdab.driver.shared.core.preferences.di.preferencesModule
import co.sirdab.driver.shared.core.preferences.di.preferencesPlatformModule
import co.sirdab.driver.shared.core.queue.di.queueModule
import co.sirdab.driver.shared.core.queue.di.queuePlatformModule
import co.sirdab.driver.shared.feature.bidding.impl.biddingModule
import co.sirdab.driver.shared.feature.notifications.impl.notificationsModule
import co.sirdab.driver.shared.feature.onboarding.impl.di.onboardingModule
import co.sirdab.driver.shared.feature.profile.impl.profileModule
import co.sirdab.driver.shared.feature.trip.impl.tripModule
import io.ktor.client.plugins.logging.LogLevel
import org.koin.core.module.Module

/** The whole Koin graph, pointed at the TMS [environment]. */
fun createTmsModules(
    environment: TmsEnvironment,
    logLevel: LogLevel = LogLevel.NONE,
): List<Module> = listOf(
    // Core / platform seams
    preferencesModule,
    preferencesPlatformModule(),
    platformDialerModule(),
    platformMapLauncherModule(),
    platformUrlOpenerModule(),
    platformFileStoreModule(),
    // Network and session
    networkModule(environment, logLevel),
    authPlatformModule(),
    authModule,
    queuePlatformModule(),
    queueModule,
    // Features
    onboardingModule,
    profileModule,
    tripModule,
    biddingModule,
    notificationsModule,
)
