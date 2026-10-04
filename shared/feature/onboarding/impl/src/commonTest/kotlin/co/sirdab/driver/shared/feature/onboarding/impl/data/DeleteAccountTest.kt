package co.sirdab.driver.shared.feature.onboarding.impl.data

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * "Delete my account": the server does the deleting, and the phone forgets the driver only once
 * the server says it has.
 */
class DeleteAccountTest {

    private fun signedIn(
        account: suspend MockRequestHandleScope.() -> HttpResponseData,
    ) = harness { request ->
        when (request.url.encodedPath) {
            Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
            Api.PROFILE -> respondJson(
                profileJson(status = "active", missing = emptyList(), workspaces = listOf(workspaceJson())),
            )
            Api.ME -> respondJson(ME_JSON)
            Api.EVENTS -> respondJson("{}", HttpStatusCode.Created)
            Api.ACCOUNT -> account()
            else -> error("unexpected ${request.url.encodedPath}")
        }
    }

    @Test
    fun `a deleted account leaves nothing of the driver on the phone`() = runTest {
        val harness = signedIn { respondJson("""{"deleted":true}""") }
        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")
        harness.queue.enqueue(Api.EVENTS.trimStart('/'), ARRIVED, occurredAtMillis = 1)

        val result = harness.auth.deleteAccount()

        assertThat(result).isInstanceOf(AppResult.Success::class)
        assertThat(harness.calls.last { it.path == Api.ACCOUNT }.method).isEqualTo("DELETE")
        // The fleet's record of the trip goes out before the session that could send it dies.
        assertThat(harness.paths().indexOf(Api.EVENTS) < harness.paths().indexOf(Api.ACCOUNT)).isTrue()
        assertThat(harness.dao.rows.value).isEmpty()
        assertThat(harness.store.get("access_token")).isNull()
        assertThat(harness.store.get("driver.last_destination")).isNull()
        assertThat(harness.onboarding.profile.value).isNull()
        assertThat(harness.auth.hasWorkspace()).isEqualTo(false)
    }

    @Test
    fun `an organization admin is refused and stays signed in`() = runTest {
        val harness = signedIn {
            respondError("account_manages_organization", HttpStatusCode.Conflict)
        }
        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")

        val result = harness.auth.deleteAccount()

        assertThat((result as AppResult.Failure).error.reason)
            .isEqualTo(AppErrorReason.MANAGES_ORGANIZATION)
        assertThat(harness.store.get("access_token")).isNotNull()
        assertThat(harness.auth.hasWorkspace()).isEqualTo(true)
    }

    @Test
    fun `no signal keeps the account and the session`() = runTest {
        val harness = signedIn { throw IllegalStateException("no signal") }
        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")

        val result = harness.auth.deleteAccount()

        assertThat(result).isInstanceOf(AppResult.Failure::class)
        assertThat(harness.store.get("access_token")).isNotNull()
    }
}
