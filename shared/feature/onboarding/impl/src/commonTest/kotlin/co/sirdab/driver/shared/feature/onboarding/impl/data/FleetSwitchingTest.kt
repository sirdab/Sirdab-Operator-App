package co.sirdab.driver.shared.feature.onboarding.impl.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverDestination
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * A driver on more than one fleet's roster.
 *
 * The token is what scopes every driver route, so moving between fleets is a server write followed
 * by a new token — not a preference the app keeps. These pin that the app does both halves, and
 * that it re-reads everything it was holding from the fleet it just left.
 */
class FleetSwitchingTest {

    @Test
    fun `switching posts the workspace then takes a new token and re-reads the fleet`() = runTest {
        var profileReads = 0
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.REFRESH -> respondJson(session(OTHER_FLEET_TOKEN))
                Api.PROFILE -> {
                    profileReads++
                    respondJson(twoFleets)
                }
                Api.ME -> respondJson(ME_JSON)
                Api.ACTIVE_WORKSPACE -> respondJson("""{"activeWorkspaceId":"ws-2"}""")
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")
        val readsAfterSignIn = profileReads

        val result = harness.auth.switchWorkspace("ws-2")

        assertThat(result).isInstanceOf(AppResult.Success::class)
        assertThat(harness.bodyOf(Api.ACTIVE_WORKSPACE).orEmpty()).contains(""""workspaceId":"ws-2"""")
        // The server rotates its own cookies, which is nothing to a phone: without this refresh the
        // app would keep calling the old fleet's API with the old claims.
        assertThat(harness.paths()).contains(Api.REFRESH)
        assertThat(harness.auth.observeActiveWorkspace().first()).isEqualTo("ws-2")
        // The profile and the fleet's verdict both belong to the fleet that was just left.
        assertThat(profileReads).isEqualTo(readsAfterSignIn + 1)
    }

    @Test
    fun `a refused switch leaves the session where it was`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(twoFleets)
                Api.ME -> respondJson(ME_JSON)
                Api.ACTIVE_WORKSPACE -> respondError("forbidden", io.ktor.http.HttpStatusCode.Forbidden)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")

        val result = harness.auth.switchWorkspace("ws-9")

        assertThat(result).isInstanceOf(AppResult.Failure::class)
        // No refresh, no re-read: the token still names the fleet it named before.
        assertThat(harness.auth.observeActiveWorkspace().first()).isEqualTo("ws-1")
    }

    @Test
    fun `a document ops sent back puts the driver on the one screen that can replace it`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                // Approved, working, and then ops rejected the licence: contract 9.0.0 drops the
                // document out of its slot and puts `identity`/`driving_licence` back in `missing`,
                // without moving `onboardingCompletedAt`.
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Nayef",
                        licenceNumber = "DL-1",
                        status = "active",
                        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
                        missing = listOf("driving_licence"),
                        trucks = listOf(truckJson()),
                        documents = listOf(
                            documentJson(
                                kind = "driving_licence",
                                status = "rejected",
                                rejectionReason = "The photo is blurred",
                            ),
                        ),
                        workspaces = listOf(workspaceJson()),
                    ),
                )
                Api.ME -> respondJson(ME_JSON)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")
        val destination = harness.auth.verifyOtp("123456")

        // `missing` outranks an active status, in the contract's own order: a driver with something
        // to replace belongs on the checklist, not on a review screen with nothing to do.
        assertThat((destination as AppResult.Success).data).isEqualTo(DriverDestination.ONBOARDING)
        assertThat(harness.onboarding.profile.value?.missing?.size).isEqualTo(1)
    }

    @Test
    fun `a driver with unsent work is not moved out from under it`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(twoFleets)
                Api.ME -> respondJson(ME_JSON)
                // A dead zone: the arrival the driver tapped cannot go yet.
                Api.EVENTS -> respondError("internal", HttpStatusCode.InternalServerError)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")
        harness.queue.enqueue(Api.EVENTS.trimStart('/'), ARRIVED, occurredAtMillis = 1)

        val result = harness.auth.switchWorkspace("ws-2")

        // Sent under the next fleet's token it would be a trip that fleet has never heard of.
        assertThat((result as AppResult.Failure).error.reason).isEqualTo(AppErrorReason.UNSENT_WORK)
        assertThat(harness.paths()).doesNotContain(Api.ACTIVE_WORKSPACE)
        assertThat(harness.dao.rows.value.size).isEqualTo(1)
    }

    private val twoFleets = profileJson(
        name = "Nayef",
        licenceNumber = "DL-1",
        status = "active",
        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
        missing = emptyList(),
        trucks = listOf(truckJson()),
        workspaces = listOf(workspaceJson(), workspaceJson(id = "ws-2")),
    )

    private companion object {
        /** The same driver, stamped into the fleet they just switched to. */
        val OTHER_FLEET_TOKEN = token(
            """{"sub":"p1","app_metadata":{"workspace_id":"ws-2","organization_id":"org-1"},""" +
                """"tms_access":"driver","tms_driver_id":"drv-9"}""",
        )
    }
}
