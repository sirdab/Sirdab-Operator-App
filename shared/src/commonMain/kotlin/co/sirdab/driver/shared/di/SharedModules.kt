package co.sirdab.driver.shared.di

import co.sirdab.driver.shared.core.auth.di.authModule
import co.sirdab.driver.shared.core.auth.di.authPlatformModule
import co.sirdab.driver.shared.core.demo.di.demoModule
import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.core.network.di.anonymousTokenModule
import co.sirdab.driver.shared.core.network.di.networkModule
import co.sirdab.driver.shared.core.platform.browser.platformUrlOpenerModule
import co.sirdab.driver.shared.core.platform.dialer.platformDialerModule
import co.sirdab.driver.shared.core.platform.maps.platformMapLauncherModule
import co.sirdab.driver.shared.core.platform.files.platformFileStoreModule
import co.sirdab.driver.shared.core.platform.notification.platformNotifierModule
import co.sirdab.driver.shared.core.preferences.di.preferencesModule
import co.sirdab.driver.shared.core.queue.di.queueModule
import co.sirdab.driver.shared.core.queue.di.queuePlatformModule
import co.sirdab.driver.shared.core.preferences.di.preferencesPlatformModule
import co.sirdab.driver.shared.feature.bidding.impl.tmsBiddingModule
import co.sirdab.driver.shared.feature.notifications.impl.notificationsModule
import co.sirdab.driver.shared.feature.notifications.impl.tmsNotificationsModule
import co.sirdab.driver.shared.feature.onboarding.impl.di.onboardingModule
import co.sirdab.driver.shared.feature.onboarding.impl.di.tmsOnboardingModule
import co.sirdab.driver.shared.feature.profile.impl.profileModule
import co.sirdab.driver.shared.feature.trip.impl.tmsTripModule
import io.ktor.client.plugins.logging.LogLevel
import org.koin.core.module.Module

/** Single aggregation point for Koin, mirroring the reference `createSharedModules`. */
fun createDemoModules(): List<Module> = listOf(
    // Core / platform seams
    preferencesModule,
    preferencesPlatformModule(),
    platformNotifierModule(),
    platformDialerModule(),
    platformMapLauncherModule(),
    platformUrlOpenerModule(),
    demoModule,
    anonymousTokenModule,
    // Features
    onboardingModule,
    profileModule,
    notificationsModule,
)

/**
 * The same graph pointed at a real TMS.
 *
 * Whatever the backend does not serve yet still comes from the demo world, so this is additive:
 * the modules listed last replace only the bindings that have a live endpoint behind them. Today
 * that is sign-in, the driver profile, trips, trip events, exceptions and device registration.
 *
 * The trip simulation is absent rather than overridden: it is a different model of a trip, and
 * leaving it in the graph would mean two answers to "what am I driving". Postings and bids still
 * come from the demo world, pending the bidding decision.
 */
fun createTmsModules(
    environment: TmsEnvironment,
    logLevel: LogLevel = LogLevel.NONE,
): List<Module> = listOf(
    preferencesModule,
    preferencesPlatformModule(),
    platformNotifierModule(),
    platformDialerModule(),
    platformMapLauncherModule(),
    platformUrlOpenerModule(),
    demoModule,
    // Network and session
    networkModule(environment, logLevel),
    authPlatformModule(),
    authModule,
    platformFileStoreModule(),
    queuePlatformModule(),
    queueModule,
    // Features
    onboardingModule,
    profileModule,
    notificationsModule,
    // Real bindings last: Koin lets a later module override an earlier one, so each of these
    // retires exactly one demo binding as its endpoint goes live.
    tmsOnboardingModule,
    tmsTripModule,
    tmsBiddingModule,
    tmsNotificationsModule,
)
