package co.sirdab.driver.shared.feature.onboarding.impl.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import co.sirdab.driver.shared.feature.onboarding.api.domain.DetailsDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import co.sirdab.driver.shared.feature.onboarding.api.domain.OnboardingGap
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckDraft
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Onboarding as a conversation with one endpoint.
 *
 * The server keeps score in `missing`; the app's job is to send what the driver typed, put the
 * photographs where it is told, and render what comes back. These pin all three.
 */
class DriverOnboardingRepositoryTmsTest {

    @Test
    fun `what is still missing is read from the server per truck`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Nayef",
                        licenceNumber = "DL-1",
                        missing = listOf("identity", "vehicle_registration:t1", "vehicle_registration:t2"),
                        trucks = listOf(truckJson(id = "t1"), truckJson(id = "t2", plate = "RUH 1")),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val profile = (harness.onboarding.refresh() as AppResult.Success).data

        // A registration belongs to a truck, so the gap carries the id rather than a bare name: a
        // driver with two trucks can be missing one and not the other.
        assertThat(profile.missing).containsExactly(
            OnboardingGap.Identity,
            OnboardingGap.VehicleRegistration("t1"),
            OnboardingGap.VehicleRegistration("t2"),
        )
        assertThat(profile.onboardingComplete).isEqualTo(false)
    }

    @Test
    fun `a gap this build does not know is kept rather than dropped`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(profileJson(missing = listOf("insurance_certificate")))
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val profile = (harness.onboarding.refresh() as AppResult.Success).data

        // Quietly ignoring it would report onboarding finished when the server says it is not.
        assertThat(profile.missing).containsExactly(OnboardingGap.Unsupported("insurance_certificate"))
    }

    @Test
    fun `details are sent as the contract's four fields`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(
                    profileJson(name = "Nayef Al Rashidi", licenceNumber = "DL-2020202", missing = listOf("truck")),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val result = harness.onboarding.saveDetails(
            DetailsDraft(
                name = "Nayef Al Rashidi",
                nationality = Nationality.SAUDI,
                licenceNumber = "DL-2020202",
                licenceExpiresAt = "2027-09-21",
            ),
        )

        assertThat(result).isInstanceOf(AppResult.Success::class)
        val call = harness.calls.single { it.path == Api.PROFILE }
        assertThat(call.method).isEqualTo("PATCH")
        val body = call.body.orEmpty()
        assertThat(body).contains(""""name":"Nayef Al Rashidi"""")
        // The dropdown's choice travels as the ISO code the TMS stores, not as its label.
        assertThat(body).contains(""""nationality":"SA"""")
        assertThat(body).contains(""""licenceNumber":"DL-2020202"""")
        assertThat(body).contains(""""licenceExpiresAt":"2027-09-21"""")
    }

    @Test
    fun `adding a truck re-reads the profile because it opens a new gap`() = runTest {
        var profileReads = 0
        val harness = harness { request ->
            when {
                request.url.encodedPath == Api.TRUCKS -> respondJson(truckJson(), HttpStatusCode.Created)
                request.url.encodedPath == Api.PROFILE -> {
                    profileReads++
                    respondJson(
                        profileJson(
                            name = "Nayef",
                            licenceNumber = "DL-1",
                            missing = listOf("vehicle_registration:t1"),
                            trucks = listOf(truckJson()),
                        ),
                    )
                }
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val profile = harness.onboarding.addTruck(
            TruckDraft(
                licencePlate = "RUH 9002",
                truckType = TruckType.CHILLED,
                truckSize = TruckSize.CLOSED_DYNA,
                capacityTons = "4",
            ),
        )

        val added = (profile as AppResult.Success).data
        // The truck's own registration is now outstanding, and the checklist has to show it.
        assertThat(added.missing).containsExactly(OnboardingGap.VehicleRegistration("t1"))
        assertThat(profileReads).isEqualTo(1)
        // The app talks in tonnes and the contract in kilograms.
        assertThat(harness.bodyOf(Api.TRUCKS).orEmpty()).contains(""""capacityKg":4000""")
    }

    @Test
    fun `the server's refusal to remove the last truck is passed on as it is`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                "${Api.TRUCKS}/t1" -> respondError("last_truck", HttpStatusCode.Conflict)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val result = harness.onboarding.removeTruck("t1")

        assertThat((result as AppResult.Failure).error.reason).isEqualTo(AppErrorReason.LAST_TRUCK)
    }

    @Test
    fun `a document is minted then uploaded and confirmed`() = runTest {
        var uploadedBytes = 0
        val harness = harness { request ->
            when {
                request.isUpload() -> {
                    uploadedBytes = request.body.contentLength?.toInt() ?: 0
                    respondJson("{}")
                }
                request.url.encodedPath == Api.DOCUMENTS -> respondJson(
                    """{"documentId":"d1","uploadUrl":"${Api.STORAGE_URL}","method":"PUT",
                       "headers":{"Content-Type":"image/jpeg"},"expiresAt":null,"path":"doc.jpg"}""",
                    HttpStatusCode.Created,
                )
                request.url.encodedPath == "${Api.DOCUMENTS}/d1/confirm" -> respondJson(
                    profileJson(
                        name = "Nayef",
                        licenceNumber = "DL-1",
                        missing = listOf("driving_licence"),
                        documents = listOf(documentJson()),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val result = harness.onboarding.uploadDocument(
            kind = DriverDocumentKind.NATIONAL_ID,
            truckId = null,
            bytes = ByteArray(12) { 7 },
            contentType = "image/jpeg",
        )

        assertThat(result).isInstanceOf(AppResult.Success::class)
        // Mint, then the bytes straight to storage, then confirm: the document only counts after
        // the object has landed.
        assertThat(harness.paths()).isEqualTo(
            listOf(Api.DOCUMENTS, Api.STORAGE, "${Api.DOCUMENTS}/d1/confirm"),
        )
        assertThat(uploadedBytes).isEqualTo(12)
        // A driver document naming a truck is refused by the contract, so it names none.
        assertThat(harness.bodyOf(Api.DOCUMENTS).orEmpty()).doesNotContain("truckId")
        // Confirming answers with the profile, so the checklist updates without a second read.
        val profile = (result as AppResult.Success).data
        assertThat(profile.documentFor(DriverDocumentKind.NATIONAL_ID)).isNotNull()
    }

    @Test
    fun `a vehicle registration names its truck`() = runTest {
        val harness = harness { request ->
            when {
                request.isUpload() -> respondJson("{}")
                request.url.encodedPath == Api.DOCUMENTS -> respondJson(
                    """{"documentId":"d2","uploadUrl":"${Api.STORAGE_URL}","method":"PUT",
                       "headers":{},"expiresAt":null,"path":"doc.jpg"}""",
                    HttpStatusCode.Created,
                )
                request.url.encodedPath == "${Api.DOCUMENTS}/d2/confirm" -> respondJson(
                    profileJson(
                        name = "Nayef",
                        licenceNumber = "DL-1",
                        onboardingCompletedAt = "2026-09-22T08:00:00+03:00",
                        missing = emptyList(),
                        trucks = listOf(truckJson()),
                        documents = listOf(documentJson(id = "d2", kind = "vehicle_registration", truckId = "t1")),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val result = harness.onboarding.uploadDocument(
            kind = DriverDocumentKind.VEHICLE_REGISTRATION,
            truckId = "t1",
            bytes = ByteArray(4),
            contentType = "image/jpeg",
        )

        assertThat(harness.bodyOf(Api.DOCUMENTS).orEmpty()).contains(""""truckId":"t1"""")
        // The last gap closing is what sets onboardingCompletedAt, and the app hears it here.
        assertThat((result as AppResult.Success).data.onboardingComplete).isEqualTo(true)
    }

    @Test
    fun `a photo the contract would refuse is stopped before it is sent`() = runTest {
        val harness = harness { request -> error("unexpected ${request.url.encodedPath}") }

        val tooBig = harness.onboarding.uploadDocument(
            kind = DriverDocumentKind.NATIONAL_ID,
            truckId = null,
            bytes = ByteArray(11 * 1024 * 1024),
            contentType = "image/jpeg",
        )
        val wrongType = harness.onboarding.uploadDocument(
            kind = DriverDocumentKind.NATIONAL_ID,
            truckId = null,
            bytes = ByteArray(8),
            contentType = "image/gif",
        )

        assertThat((tooBig as AppResult.Failure).error.reason).isEqualTo(AppErrorReason.DOCUMENT_TOO_LARGE)
        assertThat((wrongType as AppResult.Failure).error.reason).isEqualTo(AppErrorReason.DOCUMENT_TYPE)
        // Nothing left the phone: a 10 MB round trip to be told no is a round trip wasted.
        assertThat(harness.paths()).isEqualTo(emptyList())
    }

    @Test
    fun `a rejection is carried with the reason ops gave`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(
                    profileJson(
                        name = "Nayef",
                        licenceNumber = "DL-1",
                        missing = listOf("identity"),
                        documents = listOf(
                            documentJson(status = "rejected", rejectionReason = "The photo is blurred"),
                        ),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val profile: DriverProfile = (harness.onboarding.refresh() as AppResult.Success).data
        val document = profile.documentFor(DriverDocumentKind.NATIONAL_ID)

        assertThat(document?.status).isEqualTo(DriverDocumentStatus.REJECTED)
        assertThat(document?.rejectionReason).isEqualTo("The photo is blurred")
        // And it is back on the checklist, because the server put it back in `missing`.
        assertThat(profile.isMissing(OnboardingGap.Identity)).isEqualTo(true)
    }

    @Test
    fun `the cached profile is what screens read between calls`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(profileJson(name = "Nayef"))
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        assertThat(harness.onboarding.profile.value).isNull()
        harness.onboarding.refresh()
        assertThat(harness.onboarding.profile.value?.name).isEqualTo("Nayef")
    }
}
