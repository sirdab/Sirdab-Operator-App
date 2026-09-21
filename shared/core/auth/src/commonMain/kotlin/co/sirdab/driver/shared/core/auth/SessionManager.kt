package co.sirdab.driver.shared.core.auth

import co.sirdab.driver.shared.core.network.ApiFailure
import co.sirdab.driver.shared.core.network.TokenProvider
import co.sirdab.driver.shared.core.network.apiFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the app knows about who is signed in.
 *
 * The three failure shapes are deliberately distinct. A driver whose phone is not yet linked to a
 * workspace signs in perfectly well at Supabase and is then refused by the TMS; telling them
 * "login failed" would send them round the OTP loop forever.
 */
sealed interface AuthState {
    data object SignedOut : AuthState

    /** Signed in, and the token carries a driver membership in an account. */
    data class Ready(val claims: DriverClaims) : AuthState

    /**
     * Signed in, but the token carries no workspace.
     *
     * Usually one call away from [Ready]: a dispatcher creates the driver row against a phone
     * number, and the first time that phone verifies, the app asks the server to link them. It is
     * only a dead end when no dispatcher has ever added the number.
     */
    data class AwaitingLink(val claims: DriverClaims) : AuthState

    /** Signed in against an account, but not as a driver. The driver app is the wrong app. */
    data class NotADriver(val claims: DriverClaims) : AuthState
}

/**
 * Owns the session and answers [TokenProvider] for the API client.
 *
 * The refresh mutex matters more than it looks: the write queue drains concurrently with whatever
 * screen is open, so an expired token can produce several simultaneous 401s. Without the lock each
 * one would spend the refresh token, and Supabase rotates refresh tokens, so the losers would
 * invalidate the winner's session and sign the driver out mid-shift.
 */
class SessionManager(
    private val api: SupabaseAuthApi,
    private val store: SecureStore,
) : TokenProvider {

    private val refreshLock = Mutex()
    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var session: SupabaseSession? = null

    /** Reads the persisted session on cold start. Safe to call more than once. */
    suspend fun restore() {
        if (session != null) return
        val accessToken = store.get(KEY_ACCESS) ?: return
        val refreshToken = store.get(KEY_REFRESH) ?: return
        adopt(SupabaseSession(accessToken = accessToken, refreshToken = refreshToken))

        // A stored access token is usually stale by the time the app reopens, and the claims decide
        // which screen to show, so trade it in now rather than letting the first request 401.
        refresh()
    }

    suspend fun requestOtp(phone: String): Result<Unit> = api.requestOtp(phone)

    suspend fun verifyOtp(phone: String, code: String): Result<AuthState> =
        api.verifyOtp(phone, code).map { adopt(it) }

    suspend fun signOut() {
        session = null
        store.remove(KEY_ACCESS)
        store.remove(KEY_REFRESH)
        _state.value = AuthState.SignedOut
    }

    override suspend fun accessToken(): String? = session?.accessToken

    override suspend fun refresh(): Boolean {
        val stale = session?.accessToken ?: return false

        return refreshLock.withLock {
            // Another caller refreshed while this one waited for the lock, so its result stands.
            if (session?.accessToken != stale) return@withLock true

            val refreshToken = session?.refreshToken ?: return@withLock false
            api.refresh(refreshToken).fold(
                onSuccess = { adopt(it); true },
                onFailure = { error ->
                    // A refused refresh token is terminal: the session is gone and the driver signs
                    // in again. A network failure is not, so the session survives for a later try.
                    if (error.apiFailure.isTerminal()) signOut()
                    false
                },
            )
        }
    }

    private suspend fun adopt(fresh: SupabaseSession): AuthState {
        session = fresh
        store.put(KEY_ACCESS, fresh.accessToken)
        store.put(KEY_REFRESH, fresh.refreshToken)

        val claims = JwtDecoder.decode(fresh.accessToken)
        val next = when {
            claims == null -> AuthState.SignedOut
            !claims.hasWorkspace -> AuthState.AwaitingLink(claims)
            !claims.isDriver -> AuthState.NotADriver(claims)
            else -> AuthState.Ready(claims)
        }
        _state.value = next
        return next
    }

    private fun ApiFailure?.isTerminal(): Boolean = this is ApiFailure.Http && status in 400..499

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
