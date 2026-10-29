package co.sirdab.driver.shared.feature.guide.impl

import co.sirdab.driver.shared.core.auth.AuthState
import co.sirdab.driver.shared.core.preferences.KeyValueStore
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the guide has already opened for the driver holding the phone.
 *
 * Remembered per person rather than per phone: phones are handed between drivers, and the next one
 * to sign in has never seen it. The person is the Supabase user, which stays the same across every
 * fleet a driver works for, so switching fleet does not bring it back.
 */
class GuideStore(
    private val kv: KeyValueStore,
    private val session: StateFlow<AuthState>,
) {
    /**
     * True the first time it is asked about whoever is signed in now, and false ever after.
     *
     * Recorded at the asking rather than at the end of the guide. There are several ways out of it,
     * the system back among them, and a guide that came back on every launch because the driver
     * left it the wrong way would be worse than one they see once. Profile opens it again for
     * anyone who wants it.
     */
    suspend fun claimFirstShow(): Boolean {
        val person = session.value.person() ?: return false
        val key = SEEN_PREFIX + person
        if (kv.getString(key) != null) return false
        kv.putString(key, SEEN)
        return true
    }

    private fun AuthState.person(): String? = when (this) {
        AuthState.SignedOut -> null
        is AuthState.Ready -> claims.subject
        // A driver no fleet has taken on yet still uses the app, and still needs showing round it.
        is AuthState.AwaitingLink -> claims.subject
        // The wrong app altogether: they never reach the shell this opens over.
        is AuthState.NotADriver -> null
    }?.takeIf { it.isNotBlank() }

    private companion object {
        const val SEEN_PREFIX = "guide_seen:"
        const val SEEN = "1"
    }
}
