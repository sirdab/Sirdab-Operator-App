package co.sirdab.driver.shared.analytics

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import co.sirdab.driver.shared.core.analytics.testing.RecordingAnalytics
import co.sirdab.driver.shared.core.auth.AuthState
import co.sirdab.driver.shared.core.auth.DriverClaims
import co.sirdab.driver.shared.core.preferences.locale.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private fun claims(
    subject: String,
    workspace: String? = "w1",
    driver: String? = "d-$subject",
    phone: String? = "+96650000000${subject.removePrefix("u")}",
) = DriverClaims(
    subject = subject,
    phone = phone,
    workspaceId = workspace,
    access = "driver",
    driverId = driver,
    expiresAtEpochSeconds = null,
)

class AnalyticsSessionTest {

    private val analytics = RecordingAnalytics()
    private val session = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val language = MutableStateFlow(AppLanguage.ARABIC)

    private fun TestScope.start() {
        AnalyticsSession(analytics, session, language, backgroundScope)
    }

    @Test
    fun `launching signed out identifies nobody and resets nothing`() = runTest(UnconfinedTestDispatcher()) {
        start()

        assertThat(analytics.identity).isEmpty()
        assertThat(analytics.superProperties).containsOnly("language" to "ar")
    }

    @Test
    fun `a sign-in identifies the person by phone number, with their fleet on every event`() = runTest(UnconfinedTestDispatcher()) {
        start()

        session.value = AuthState.Ready(claims("u1"))

        assertThat(analytics.identity).isEqualTo(listOf("identify:+966500000001"))
        assertThat(analytics.superProperties).containsOnly(
            "language" to "ar",
            "workspace_id" to "w1",
            "driver_id" to "d-u1",
        )
    }

    @Test
    fun `a token without a phone number falls back to the user id`() = runTest(UnconfinedTestDispatcher()) {
        start()

        session.value = AuthState.Ready(claims("u1", phone = null))

        assertThat(analytics.identity).isEqualTo(listOf("identify:u1"))
    }

    @Test
    fun `signing out says so as the person leaving, then forgets them`() = runTest(UnconfinedTestDispatcher()) {
        start()
        session.value = AuthState.Ready(claims("u1"))

        session.value = AuthState.SignedOut

        // In this order: a reset first would put "Signed Out" on the next person's anonymous id.
        assertThat(analytics.identity).isEqualTo(listOf("identify:+966500000001", "reset"))
        assertThat(analytics.eventNames).isEqualTo(listOf("Signed Out"))
        assertThat(analytics.superProperties).containsOnly("language" to "ar")
    }

    @Test
    fun `the next driver on the same phone starts clean`() = runTest(UnconfinedTestDispatcher()) {
        start()
        session.value = AuthState.Ready(claims("u1"))

        session.value = AuthState.Ready(claims("u2"))

        assertThat(analytics.identity).isEqualTo(listOf("identify:+966500000001", "reset", "identify:+966500000002"))
    }

    @Test
    fun `a fleet switch is the same person, so only the fleet changes`() = runTest(UnconfinedTestDispatcher()) {
        start()
        session.value = AuthState.Ready(claims("u1", workspace = "w1"))

        session.value = AuthState.Ready(claims("u1", workspace = "w2"))

        assertThat(analytics.identity).isEqualTo(listOf("identify:+966500000001"))
        assertThat(analytics.superProperties["workspace_id"]).isEqualTo("w2")
    }

    @Test
    fun `a driver no fleet holds carries no fleet`() = runTest(UnconfinedTestDispatcher()) {
        start()
        session.value = AuthState.Ready(claims("u1"))

        session.value = AuthState.AwaitingLink(claims("u1", workspace = null, driver = null))

        assertThat(analytics.superProperties).containsOnly("language" to "ar")
    }

    @Test
    fun `a language change reaches the next event without a sign-in`() = runTest(UnconfinedTestDispatcher()) {
        start()
        session.value = AuthState.Ready(claims("u1"))

        language.value = AppLanguage.URDU

        assertThat(analytics.superProperties["language"]).isEqualTo("ur")
        assertThat(analytics.profile["language"]).isEqualTo("ur")
        assertThat(analytics.identity).isEqualTo(listOf("identify:+966500000001"))
    }
}
