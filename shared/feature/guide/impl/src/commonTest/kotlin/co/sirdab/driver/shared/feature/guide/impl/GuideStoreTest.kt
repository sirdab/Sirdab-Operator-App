package co.sirdab.driver.shared.feature.guide.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import co.sirdab.driver.shared.core.auth.AuthState
import co.sirdab.driver.shared.core.auth.DriverClaims
import co.sirdab.driver.shared.core.preferences.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** What the phone keeps between launches, without a file under it. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private fun signedIn(subject: String, workspace: String? = "w1"): AuthState {
    val claims = DriverClaims(
        subject = subject,
        phone = null,
        workspaceId = workspace,
        access = "driver",
        driverId = "d-$subject",
        expiresAtEpochSeconds = null,
    )
    return if (workspace != null) AuthState.Ready(claims) else AuthState.AwaitingLink(claims)
}

class GuideStoreTest {

    private val kv = KeyValueStore(InMemoryPreferences())
    private val session = MutableStateFlow(signedIn("u1"))
    private val store = GuideStore(kv, session)

    @Test
    fun `the first ask for a driver is yes, and every one after is no`() = runTest {
        assertThat(store.claimFirstShow()).isTrue()
        assertThat(store.claimFirstShow()).isFalse()
        assertThat(store.claimFirstShow()).isFalse()
    }

    @Test
    fun `it is remembered across a restart`() = runTest {
        store.claimFirstShow()

        assertThat(GuideStore(kv, session).claimFirstShow()).isFalse()
    }

    @Test
    fun `the next driver handed the phone is shown it too`() = runTest {
        store.claimFirstShow()

        session.value = signedIn("u2")
        assertThat(store.claimFirstShow()).isTrue()

        // And the first driver, back on their own phone, is not shown it again.
        session.value = signedIn("u1")
        assertThat(store.claimFirstShow()).isFalse()
    }

    @Test
    fun `switching fleet does not bring it back`() = runTest {
        store.claimFirstShow()

        session.value = signedIn("u1", workspace = "w2")
        assertThat(store.claimFirstShow()).isFalse()
    }

    @Test
    fun `a driver no fleet has taken on yet is shown it`() = runTest {
        session.value = signedIn("u3", workspace = null)

        assertThat(store.claimFirstShow()).isTrue()
    }

    @Test
    fun `nobody signed in is never shown it, and it is not spent on them`() = runTest {
        session.value = AuthState.SignedOut
        assertThat(store.claimFirstShow()).isFalse()

        session.value = signedIn("u1")
        assertThat(store.claimFirstShow()).isTrue()
    }
}
