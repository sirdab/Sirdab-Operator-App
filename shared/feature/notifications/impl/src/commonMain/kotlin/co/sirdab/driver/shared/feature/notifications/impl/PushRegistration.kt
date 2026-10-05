package co.sirdab.driver.shared.feature.notifications.impl

import co.sirdab.driver.shared.core.auth.AuthState
import co.sirdab.driver.shared.core.auth.SessionManager
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.preferences.locale.LanguageStore
import co.sirdab.driver.shared.feature.notifications.api.DeviceRegistry
import co.sirdab.driver.shared.feature.notifications.api.PushDevice
import co.sirdab.driver.shared.feature.notifications.api.PushTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Tells the TMS which phone this person is on, whenever that could have changed: a sign-in, a new
 * token, or the phone passing to someone else. A fleet switch is the same person on the same phone,
 * so it registers nothing.
 *
 * A failed call is retried with backoff, since the first sign-in of the day is often where the
 * signal is worst. A newer sign-in or token cancels the retries for the old one.
 */
class PushRegistration(
    private val session: SessionManager,
    private val tokens: PushTokens,
    private val registry: DeviceRegistry,
    private val device: PushDevice,
    private val languageStore: LanguageStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    init {
        scope.launch {
            combine(session.state, tokens.token) { state, token ->
                val person = state.personId()
                if (person != null && token != null) person to token else null
            }
                .distinctUntilChanged()
                .collectLatest { registration ->
                    val (_, token) = registration ?: return@collectLatest
                    registerWithRetry(token)
                }
        }
    }

    private suspend fun registerWithRetry(token: String) {
        var wait = FIRST_RETRY
        repeat(MAX_ATTEMPTS) {
            val result = registry.register(
                platform = device.platform,
                pushToken = token,
                appVersion = device.appVersion,
                locale = languageStore.language.value.code,
            )
            if (result is AppResult.Success) return
            delay(wait)
            wait *= 2
        }
    }

    private fun AuthState.personId(): String? = when (this) {
        AuthState.SignedOut -> null
        is AuthState.Ready -> claims.subject
        is AuthState.AwaitingLink -> claims.subject
        is AuthState.NotADriver -> claims.subject
    }

    private companion object {
        const val MAX_ATTEMPTS = 6
        val FIRST_RETRY = 15.seconds
    }
}
