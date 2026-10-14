package co.sirdab.driver.shared.core.analytics.testing

import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent

/**
 * Keeps what it is told, for tests to read back. In main rather than a test source set so every
 * module's tests can use the one copy.
 */
class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()

    /** Every identity call in order: `identify:<id>` and `reset`. */
    val identity = mutableListOf<String>()

    val superProperties = mutableMapOf<String, Any>()
    val profile = mutableMapOf<String, Any>()

    val eventNames: List<String> get() = events.map { it.name }

    override fun track(event: String, properties: Map<String, Any>) {
        events += AnalyticsEvent(event, properties)
    }

    override fun identify(userId: String) {
        identity += "identify:$userId"
    }

    override fun reset() {
        identity += "reset"
        superProperties.clear()
    }

    override fun register(properties: Map<String, Any>) {
        superProperties += properties
    }

    override fun unregister(property: String) {
        superProperties -= property
    }

    override fun setProfile(properties: Map<String, Any>) {
        profile += properties
    }
}
