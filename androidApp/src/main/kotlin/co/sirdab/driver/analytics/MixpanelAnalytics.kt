package co.sirdab.driver.analytics

import android.content.Context
import co.sirdab.driver.BuildConfig
import co.sirdab.driver.shared.core.analytics.Analytics
import com.mixpanel.android.mpmetrics.MixpanelAPI

/**
 * [Analytics] through the Mixpanel Android SDK, which keeps events on disk and sends them in
 * batches by itself, so a driver with no signal at a loading bay loses nothing.
 *
 * Mixpanel's own automatic events stay off: "App Opened" and "Screen Viewed" come from the shared
 * code, the same on both platforms.
 *
 * The SDK's instance is not thread-safe, and events arrive from view models on the main thread and
 * from the session and the write queue on background ones, so every call holds the instance's lock,
 * as the SDK asks.
 */
class MixpanelAnalytics(context: Context, token: String) : Analytics {

    private val mixpanel = MixpanelAPI.getInstance(context, token, false)

    init {
        registerBuild()
    }

    override fun track(event: String, properties: Map<String, Any>) = synchronized(mixpanel) {
        mixpanel.trackMap(event, properties)
    }

    override fun identify(userId: String) = synchronized(mixpanel) {
        mixpanel.identify(userId)
    }

    override fun reset() = synchronized(mixpanel) {
        mixpanel.reset()
        // A reset clears every super property, this one too.
        registerBuild()
    }

    override fun register(properties: Map<String, Any>) = synchronized(mixpanel) {
        mixpanel.registerSuperPropertiesMap(properties)
    }

    override fun unregister(property: String) = synchronized(mixpanel) {
        mixpanel.unregisterSuperProperty(property)
    }

    override fun setProfile(properties: Map<String, Any>) = synchronized(mixpanel) {
        mixpanel.people.setMap(properties)
    }

    /** Debug builds send too, so a developer's taps can be told apart from a driver's. */
    private fun registerBuild() {
        mixpanel.registerSuperPropertiesMap(
            mapOf("build_type" to if (BuildConfig.DEBUG) "debug" else "release"),
        )
    }
}
