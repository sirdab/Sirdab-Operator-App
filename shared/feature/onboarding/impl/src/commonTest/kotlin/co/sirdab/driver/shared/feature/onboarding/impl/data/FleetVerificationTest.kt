package co.sirdab.driver.shared.feature.onboarding.impl.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import co.sirdab.driver.shared.core.model.VerificationDocumentKind
import co.sirdab.driver.shared.core.model.VerificationDocumentStatus
import co.sirdab.driver.shared.core.model.VerificationSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * What the fleet says about a driver, which is what the board is gated on.
 *
 * `canAcceptLoads` is the server's answer and not a summary of the documents: it accounts for
 * expiry, and for a driver row or a truck the fleet has deactivated. The app reads it, renders the
 * documents beside it, and works none of it out for itself.
 */
class FleetVerificationTest {

    @Test
    fun `an approved driver may work and their documents come with them`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(activeProfile)
                Api.ME -> respondJson(meJson(canAcceptLoads = true, status = "approved"))
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")

        val verification = harness.auth.observeVerification().first()
        assertThat(verification?.canAcceptLoads).isEqualTo(true)
        assertThat(verification?.status).isEqualTo(VerificationSummary.APPROVED)
        assertThat(verification?.documents?.map { it.kind }).isEqualTo(
            listOf(VerificationDocumentKind.NATIONAL_ID, VerificationDocumentKind.ISTIMARA),
        )
    }

    @Test
    fun `a rejection is carried with the reason and the driver may not work`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(activeProfile)
                Api.ME -> respondJson(
                    meJson(
                        canAcceptLoads = false,
                        status = "rejected",
                        documents = listOf(
                            meDocument(
                                kind = "driving_licence",
                                status = "rejected",
                                rejectionReason = "The photo is blurred",
                            ),
                        ),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")

        val verification = harness.auth.observeVerification().first()
        assertThat(verification?.canAcceptLoads).isEqualTo(false)
        assertThat(verification?.hasRejection).isEqualTo(true)
        val document = verification?.documents?.single()
        assertThat(document?.status).isEqualTo(VerificationDocumentStatus.REJECTED)
        assertThat(document?.rejectionReason).isEqualTo("The photo is blurred")
    }

    @Test
    fun `an approved document that has expired still closes the board`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(activeProfile)
                // The server's own answer: approved, and expired, and therefore not allowed to
                // work. Nothing in the statuses alone would have told the app that.
                Api.ME -> respondJson(
                    meJson(
                        canAcceptLoads = false,
                        status = "approved",
                        documents = listOf(
                            meDocument(kind = "driving_licence", status = "approved", expired = true),
                        ),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000001")
        harness.auth.verifyOtp("123456")

        val verification = harness.auth.observeVerification().first()
        assertThat(verification?.canAcceptLoads).isEqualTo(false)
        assertThat(verification?.documents?.single()?.expired).isEqualTo(true)
        assertThat(verification?.hasRejection).isEqualTo(false)
    }

    @Test
    fun `a driver with no fleet has no verdict to show`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.VERIFY -> respondJson(session(UNLINKED_TOKEN))
                Api.PROFILE -> respondJson(profileJson())
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000003")
        harness.auth.verifyOtp("123456")

        // No workspace, so `/me` is never asked and there is nothing to gate on. The shell treats
        // that as "not asked", not as "refused".
        assertThat(harness.auth.observeVerification().first()).isNull()
        assertThat(harness.paths()).isEqualTo(listOf(Api.VERIFY, Api.PROFILE))
    }

    @Test
    fun `an approved driver with no fleet is not asked about one`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                // The token still names a workspace — a fleet that has since dropped them, or a
                // claim minted before it did. The profile, read a moment ago, says otherwise.
                Api.VERIFY -> respondJson(session(DRIVER_TOKEN))
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Azab",
                        licenceNumber = "DL-1",
                        status = "active",
                        onboardingCompletedAt = "2026-09-22T10:12:42Z",
                        missing = emptyList(),
                        trucks = listOf(truckJson()),
                        workspaces = emptyList(),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.auth.requestOtp("+966500000009")
        harness.auth.verifyOtp("123456")

        // `/driver/me` is workspace-scoped and would answer 403 no_active_workspace. Asking anyway
        // spends a round trip to be told what the profile already said.
        assertThat(harness.paths()).isEqualTo(listOf(Api.VERIFY, Api.PROFILE))
        assertThat(harness.auth.observeVerification().first()).isNull()
    }

    private val activeProfile = profileJson(
        name = "Nayef",
        licenceNumber = "DL-1",
        status = "active",
        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
        missing = emptyList(),
        trucks = listOf(truckJson()),
        workspaces = listOf(workspaceJson()),
    )

    private fun meDocument(
        kind: String,
        status: String = "pending",
        rejectionReason: String? = null,
        expired: Boolean = false,
    ): String = """
    {"id":"vd-$kind","subject":"driver","subjectId":"drv-7","kind":"$kind","fileId":"f1",
     "expiresAt":null,"status":"$status",
     "rejectionReason":${rejectionReason?.let { "\"$it\"" } ?: "null"},
     "reviewedAt":null,"submittedAt":"2026-09-22T08:00:00+03:00","expired":$expired}
    """

    private fun meJson(
        canAcceptLoads: Boolean,
        status: String,
        documents: List<String> = listOf(
            meDocument(kind = "national_id", status = "approved"),
            meDocument(kind = "istimara", status = "approved"),
        ),
    ): String = """
    {"profile":{"id":"p1","name":"Nayef Al Rashidi","phone":"+966500000001"},
     "workspaceId":"ws-1",
     "driver":{"id":"drv-7","name":"Nayef Al Rashidi","status":"active",
               "licenceNumber":"DL-2020202","licenceExpiresAt":"2027-09-21"},
     "carrier":{"id":"c-1","name":"Nayef","kind":"independent"},
     "trucks":[],
     "documents":${documents.joinToString(prefix = "[", postfix = "]")},
     "verification":{"status":"$status","canAcceptLoads":$canAcceptLoads}}
    """
}
