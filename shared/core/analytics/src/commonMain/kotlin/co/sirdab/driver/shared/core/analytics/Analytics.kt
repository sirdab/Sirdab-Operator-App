package co.sirdab.driver.shared.core.analytics

/**
 * Product analytics, sent to Mixpanel through each platform's own SDK.
 *
 * Mixpanel ships an Android library and a Swift package, neither of which common code can call, so
 * each app implements this and hands it to the shared graph: `MixpanelAnalytics` in androidApp and
 * in iosApp. Shared code only ever sees this interface, and says what happened through
 * [AnalyticsEvent].
 *
 * Every call is fire-and-forget. The SDKs persist, batch and send on their own, so an event recorded
 * at the back of a warehouse is kept until there is signal, and nothing here can fail a screen.
 *
 * Property values are strings, numbers and booleans only; [AnalyticsEvent] drops nulls before they
 * get here.
 */
interface Analytics {
    fun track(event: String, properties: Map<String, Any>)

    /** From here on everything is [userId]'s, and so is what this install sent before signing in. */
    fun identify(userId: String)

    /**
     * Forget who was signed in, and carry on as a new anonymous install.
     *
     * Phones are handed between drivers. Without this the next driver's sign-in is merged into
     * the last one's history.
     */
    fun reset()

    /** Sent with every event from now on, until [reset] or [unregister]. */
    fun register(properties: Map<String, Any>)

    fun unregister(property: String)

    /** Set on the signed-in person's Mixpanel profile, rather than on any one event. */
    fun setProfile(properties: Map<String, Any>)

    /** Goes nowhere: for tests, and for a build with no Mixpanel token. */
    object None : Analytics {
        override fun track(event: String, properties: Map<String, Any>) = Unit
        override fun identify(userId: String) = Unit
        override fun reset() = Unit
        override fun register(properties: Map<String, Any>) = Unit
        override fun unregister(property: String) = Unit
        override fun setProfile(properties: Map<String, Any>) = Unit
    }
}

fun Analytics.track(event: AnalyticsEvent) = track(event.name, event.properties)
