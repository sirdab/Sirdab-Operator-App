package co.sirdab.driver.shared.feature.onboarding.impl.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverDestination
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Where a verified phone lands, which is the only decision sign-in makes.
 *
 * Reading the driver's profile is what creates it, so there is no "not signed up" failure any
 * more: every verified number has a profile, and the profile says which of the app's worlds they
 * are in.
 */
class SignInDestinationTest {

    @Test
    fun `a phone with nothing on file goes to the checklist`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(UNLINKED_TOKEN))
                Api.PROFILE -> respondJson(profileJson())
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000009")

        assertThat(harness.auth.verifyOtp("123456").destination()).isEqualTo(DriverDestination.ONBOARDING)
        // Sign-up is the profile read; nothing else is called to register anybody.
        assertThat(harness.paths()).isEqualTo(listOf(Api.VERIFY, Api.PROFILE))
    }

    @Test
    fun `waiting on ops opens the app rather than holding the driver out of it`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(UNLINKED_TOKEN))
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Nayef Al Rashidi",
                        licenceNumber = "DL-2020202",
                        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
                        missing = emptyList(),
                        trucks = listOf(truckJson()),
                        documents = listOf(documentJson()),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000003")

        // Review is something the shell says on a banner, not a room the driver is locked in: what
        // they may actually do is the fleet's call, through `canAcceptLoads`.
        assertThat(harness.auth.verifyOtp("123456").destination()).isEqualTo(DriverDestination.MAIN)
        assertThat(harness.onboarding.profile.value?.isUnderReview).isEqualTo(true)
    }

    @Test
    fun `an approved driver goes straight to the tabs`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Nayef Al Rashidi",
                        licenceNumber = "DL-2020202",
                        status = "active",
                        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
                        missing = emptyList(),
                        trucks = listOf(truckJson()),
                        workspaces = listOf(workspaceJson()),
                    ),
                )
                Api.ME -> respondJson(ME_JSON)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")

        assertThat(harness.auth.verifyOtp("123456").destination()).isEqualTo(DriverDestination.MAIN)
        // The fleet's own view of the driver is read too, for the name on the profile screen.
        assertThat(harness.paths()).contains(Api.ME)
    }

    @Test
    fun `a suspended driver is blocked rather than shown an error`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(UNLINKED_TOKEN))
                // The server refuses the read outright rather than reporting the status on it.
                Api.PROFILE -> respondError("driver_suspended", HttpStatusCode.Forbidden)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000003")

        assertThat(harness.auth.verifyOtp("123456").destination()).isEqualTo(DriverDestination.BLOCKED)
    }

    @Test
    fun `a profile that links a fleet trades the token in for one that carries it`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                // No workspace in the claims: the roster row was linked by the profile read itself.
                Api.VERIFY -> respondJson(session(UNLINKED_TOKEN))
                Api.REFRESH -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Nayef Al Rashidi",
                        licenceNumber = "DL-2020202",
                        status = "active",
                        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
                        missing = emptyList(),
                        trucks = listOf(truckJson()),
                        workspaces = listOf(workspaceJson()),
                    ),
                )
                Api.ME -> respondJson(ME_JSON)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000003")
        harness.auth.verifyOtp("123456")

        // Without this the trip surface would answer 403 to a driver the roster already has.
        assertThat(harness.paths()).contains(Api.REFRESH)
        assertThat(harness.auth.hasWorkspace()).isTrue()
    }

    @Test
    fun `a cold start with no session opens sign-in without asking the server`() = runTest {
        val harness = harness { request -> error("unexpected ${request.url.encodedPath}") }

        assertThat(harness.auth.resolveDestination()).isEqualTo(DriverDestination.SIGN_IN)
        assertThat(harness.paths()).isEmpty()
    }

    @Test
    fun `signing out sends what it can then leaves nothing of this driver behind`() = runTest {
        val sent = mutableListOf<String>()
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Nayef",
                        licenceNumber = "DL-1",
                        status = "active",
                        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
                        missing = emptyList(),
                        trucks = listOf(truckJson()),
                        workspaces = listOf(workspaceJson()),
                    ),
                )
                Api.ME -> respondJson(ME_JSON)
                Api.EVENTS -> {
                    sent += request.url.encodedPath
                    respondJson("{}", HttpStatusCode.Created)
                }
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")
        harness.queue.enqueue(Api.EVENTS.trimStart('/'), """{"kind":"arrived"}""", occurredAtMillis = 1)

        harness.auth.signOut()

        assertThat(sent).isEqualTo(listOf(Api.EVENTS))
        assertThat(harness.dao.rows.value).isEmpty()
        assertThat(harness.store.get("access_token")).isNull()
        assertThat(harness.auth.hasWorkspace()).isEqualTo(false)
    }

    private fun AppResult<DriverDestination>.destination(): DriverDestination =
        (this as AppResult.Success).data

    @Test
    fun `an onboarded driver opening the app offline lands on the tabs`() = runTest {
        val phone = InMemoryStore()
        val online = harness(phone) { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Nayef",
                        licenceNumber = "DL-1",
                        status = "active",
                        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
                        missing = emptyList(),
                        trucks = listOf(truckJson()),
                        workspaces = listOf(workspaceJson()),
                    ),
                )
                Api.ME -> respondJson(ME_JSON)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }
        online.auth.requestOtp("+966500000001")
        online.auth.verifyOtp("123456")

        // A new process on the same phone, in a dead zone: nothing held in memory survives, and
        // every read fails. The checklist would take the driver away from their trips.
        val coldStart = harness(phone) { throw IllegalStateException("no signal") }

        assertThat(coldStart.auth.resolveDestination()).isEqualTo(DriverDestination.MAIN)
    }

    @Test
    fun `signing out forgets where the last driver belonged`() = runTest {
        val phone = InMemoryStore()
        val harness = harness(phone) { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(
                    profileJson(status = "active", missing = emptyList(), workspaces = listOf(workspaceJson())),
                )
                Api.ME -> respondJson(ME_JSON)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }
        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")

        harness.auth.signOut()

        assertThat(phone.get("driver.last_destination")).isNull()
    }

    @Test
    fun `an account that is not a driver's is not kept for the next launch`() = runTest {
        val staff = token(
            """{"sub":"p9","app_metadata":{"workspace_id":"ws-1"},"tms_access":"staff"}""",
        )
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(staff))
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000009")
        harness.auth.verifyOtp("123456")

        // Kept, a relaunch would restore it and walk straight past the gate.
        assertThat(harness.store.get("access_token")).isNull()
    }

    @Test
    fun `a rate-limited refresh does not sign the driver out`() = runTest {
        var refreshes = 0
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.REFRESH -> {
                    refreshes++
                    respondError("over_request_rate_limit", HttpStatusCode.TooManyRequests)
                }
                else -> respondJson(profileJson(status = "active", missing = emptyList()))
            }
        }
        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")

        harness.session.refresh()

        assertThat(refreshes).isEqualTo(1)
        assertThat(harness.auth.hasWorkspace()).isTrue()
        assertThat(harness.store.get("refresh_token")).isNotNull()
    }
}
