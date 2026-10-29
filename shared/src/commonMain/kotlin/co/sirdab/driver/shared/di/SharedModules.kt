package co.sirdab.driver.shared.di

import co.sirdab.driver.shared.analytics.AnalyticsSession
import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.auth.SessionManager
import co.sirdab.driver.shared.core.auth.di.authModule
import co.sirdab.driver.shared.core.auth.di.authPlatformModule
import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.core.network.di.networkModule
import co.sirdab.driver.shared.core.platform.browser.platformUrlOpenerModule
import co.sirdab.driver.shared.core.platform.dialer.platformDialerModule
import co.sirdab.driver.shared.core.platform.documents.platformDocumentViewerModule
import co.sirdab.driver.shared.core.platform.files.platformFileStoreModule
import co.sirdab.driver.shared.core.platform.maps.platformMapLauncherModule
import co.sirdab.driver.shared.core.preferences.di.preferencesModule
import co.sirdab.driver.shared.core.preferences.di.preferencesPlatformModule
import co.sirdab.driver.shared.core.preferences.locale.LanguageStore
import co.sirdab.driver.shared.core.queue.di.queueModule
import co.sirdab.driver.shared.core.queue.di.queuePlatformModule
import co.sirdab.driver.shared.feature.bidding.impl.biddingModule
import co.sirdab.driver.shared.feature.guide.impl.guideModule
import co.sirdab.driver.shared.feature.notifications.impl.notificationsModule
import co.sirdab.driver.shared.feature.onboarding.impl.di.onboardingModule
import co.sirdab.driver.shared.feature.profile.impl.profileModule
import co.sirdab.driver.shared.feature.trip.impl.tripModule
import io.ktor.client.plugins.logging.LogLevel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The whole Koin graph, pointed at the TMS [environment].
 *
 * [analytics] is the platform's Mixpanel, made by the app because the SDK is a platform library.
 */
fun createTmsModules(
    environment: TmsEnvironment,
    analytics: Analytics,
    logLevel: LogLevel = LogLevel.NONE,
): List<Module> = listOf(
    // Core / platform seams
    preferencesModule,
    preferencesPlatformModule(),
    platformDialerModule(),
    platformMapLauncherModule(),
    platformUrlOpenerModule(),
    platformDocumentViewerModule(),
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
    guideModule,
    tripModule,
    biddingModule,
    notificationsModule,
    analyticsModule(analytics),
)

private fun analyticsModule(analytics: Analytics): Module = module {
    single { analytics }
    // Eager: it watches the session from launch, so a driver restored from the last run is
    // identified before the first event goes out.
    single(createdAtStart = true) {
        AnalyticsSession(analytics, get<SessionManager>().state, get<LanguageStore>().language)
    }
}
