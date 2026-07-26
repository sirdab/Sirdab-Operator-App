package co.sirdab.driver.shared.di

import co.sirdab.driver.shared.core.demo.di.demoModule
import co.sirdab.driver.shared.core.platform.notification.platformNotifierModule
import co.sirdab.driver.shared.core.preferences.di.preferencesModule
import co.sirdab.driver.shared.core.preferences.di.preferencesPlatformModule
import co.sirdab.driver.shared.feature.bidding.impl.biddingModule
import co.sirdab.driver.shared.feature.loadboard.impl.loadboardModule
import co.sirdab.driver.shared.feature.notifications.impl.notificationsModule
import co.sirdab.driver.shared.feature.onboarding.impl.di.onboardingModule
import co.sirdab.driver.shared.feature.profile.impl.profileModule
import co.sirdab.driver.shared.feature.trip.impl.tripModule
import co.sirdab.driver.shared.feature.wallet.impl.walletModule
import org.koin.core.module.Module

/** Single aggregation point for Koin, mirroring the reference `createSharedModules`. */
fun createDemoModules(): List<Module> = listOf(
    // Core / platform seams
    preferencesModule,
    preferencesPlatformModule(),
    platformNotifierModule(),
    demoModule,
    // Features
    onboardingModule,
    loadboardModule,
    biddingModule,
    tripModule,
    walletModule,
    profileModule,
    notificationsModule,
)
