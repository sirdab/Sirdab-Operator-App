package co.sirdab.driver.shared.analytics

import co.sirdab.driver.shared.core.analytics.Analytics
import co.sirdab.driver.shared.core.analytics.AnalyticsEvent
import co.sirdab.driver.shared.core.analytics.track
import co.sirdab.driver.shared.core.auth.AuthState
import co.sirdab.driver.shared.core.auth.DriverClaims
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps Mixpanel's idea of who is holding the phone in step with the session.
 *
 * A driver is identified by the phone number they signed in with, so Mixpanel profiles can be
 * looked up by the number support and ops already know them by. A token without one falls back to
 * the Supabase user id.
 *
 * Phones are handed between drivers, so this is more than bookkeeping. Whoever signs in is
 * identified, and whoever leaves is reset away before the next person starts, or the next driver's
 * sign-up would be merged into the last one's history. It watches the session rather than the
 * sign-out buttons so it also catches the ways out nobody taps: a refresh token the server refused,
 * a deleted account, a sign-in that turned out not to be a driver.
 *
 * Every event also carries the fleet and the app's language, since both can change without a new
 * sign-in.
 */
class AnalyticsSession(
    private val analytics: Analytics,
    session: Flow<AuthState>,
    language: Flow<AppLanguage>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** Who this install was last told it belongs to. */
    private var identified: String? = null

    init {
        scope.launch {
            combine(session, language) { state, lang -> state.claims() to lang }
                .distinctUntilChanged()
                .collect { (claims, lang) -> update(claims, lang) }
        }
    }

    private fun update(claims: DriverClaims?, language: AppLanguage) {
        val person = claims?.let { it.phone ?: it.subject }?.takeIf { it.isNotBlank() }

        if (identified != null && person != identified) {
            // Tracked first: until the reset this install is still the person leaving.
            analytics.track(AnalyticsEvent.signedOut())
            analytics.reset()
            identified = null
        }
        if (person != null && person != identified) {
            analytics.identify(person)
            identified = person
        }

        // A reset clears these, so they are set again on every change rather than once.
        val properties = buildMap {
            put(LANGUAGE, language.code)
            claims?.workspaceId?.takeIf { it.isNotBlank() }?.let { put(WORKSPACE_ID, it) }
            claims?.driverId?.let { put(DRIVER_ID, it) }
        }
        analytics.register(properties)
        // A driver whose fleet let them go keeps signing in, with no fleet to speak for them.
        (FLEET_KEYS - properties.keys).forEach(analytics::unregister)
        if (person != null) analytics.setProfile(properties)
    }

    private fun AuthState.claims(): DriverClaims? = when (this) {
        AuthState.SignedOut -> null
        is AuthState.Ready -> claims
        is AuthState.AwaitingLink -> claims
        is AuthState.NotADriver -> claims
    }

    private companion object {
        const val LANGUAGE = "language"
        const val WORKSPACE_ID = "workspace_id"
        const val DRIVER_ID = "driver_id"
        val FLEET_KEYS = setOf(WORKSPACE_ID, DRIVER_ID)
    }
}
