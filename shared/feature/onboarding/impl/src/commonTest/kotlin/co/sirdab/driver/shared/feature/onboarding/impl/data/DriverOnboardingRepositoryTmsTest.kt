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
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.feature.onboarding.api.domain.City
import co.sirdab.driver.shared.feature.onboarding.api.domain.DetailsDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import co.sirdab.driver.shared.feature.onboarding.api.domain.OnboardingGap
import co.sirdab.driver.shared.feature.onboarding.api.domain.PreferredRoute
import co.sirdab.driver.shared.feature.onboarding.api.domain.RoutesDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckType
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
    fun `the home city and routes are named from the profile's own list of cities`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(
                    profileJson(
                        homeCityId = RIYADH,
                        preferredRoutes = listOf(routeJson(RIYADH, JEDDAH), routeJson(JEDDAH, UNNAMED)),
                        cities = listOf(cityJson(RIYADH, "Riyadh", "الرياض"), cityJson(JEDDAH, "Jeddah")),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val profile = (harness.onboarding.refresh() as AppResult.Success).data

        assertThat(profile.homeCity?.displayName("ar")).isEqualTo("الرياض")
        assertThat(profile.homeCity?.displayName("en")).isEqualTo("Riyadh")
        assertThat(profile.preferredRoutes.map { it.origin.name to it.destination.name })
            .containsExactly("Riyadh" to "Jeddah", "Jeddah" to "")
        // A city the server did not name is kept by its id: the PATCH replaces the whole list, and
        // dropping the route here would delete it on the driver's next save.
        assertThat(profile.preferredRoutes.last().destination.id).isEqualTo(UNNAMED)
    }

    @Test
    fun `the home city and routes are sent as ids, the whole list at once`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(profileJson(homeCityId = RIYADH))
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }
        val riyadh = City(RIYADH, "Riyadh", "الرياض")
        val jeddah = City(JEDDAH, "Jeddah", null)

        val result = harness.onboarding.saveRoutes(
            RoutesDraft(homeCity = riyadh, routes = listOf(PreferredRoute(riyadh, jeddah))),
        )

        assertThat(result).isInstanceOf(AppResult.Success::class)
        val call = harness.calls.single { it.path == Api.PROFILE }
        assertThat(call.method).isEqualTo("PATCH")
        val body = call.body.orEmpty()
        assertThat(body).contains(""""homeCityId":"$RIYADH"""")
        assertThat(body).contains(
            """"preferredRoutes":[{"originCityId":"$RIYADH","destinationCityId":"$JEDDAH"}]""",
        )
        // Only what this screen edits: a name left out is a name left alone.
        assertThat(body).doesNotContain(""""name"""")
    }

    @Test
    fun `removing the last route sends an empty list rather than leaving the field out`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(profileJson(homeCityId = RIYADH))
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        harness.onboarding.saveRoutes(RoutesDraft(homeCity = City(RIYADH, "Riyadh", null)))

        // Left out, the server would keep the routes the driver just removed.
        assertThat(harness.bodyOf(Api.PROFILE).orEmpty()).contains(""""preferredRoutes":[]""")
    }

    @Test
    fun `a city search sends what was typed, and a blank one asks for the biggest`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.CITIES -> respondJson(
                    """{"items":[{"id":"$JEDDAH","name":"Jeddah","nameAr":"جدة","countryCode":"SA",
                       "regionId":null,"regionName":"Makkah","governorateId":null,"governorateName":null,
                       "tier":1,"lat":21.5,"lng":39.2}],"nextCursor":null}""",
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val found = (harness.onboarding.searchCities("  jed ") as AppResult.Success).data

        assertThat(found).containsExactly(City(JEDDAH, "Jeddah", "جدة"))
        assertThat(harness.calls.last().query).isEqualTo("q=jed")

        harness.onboarding.searchCities(" ")

        // The contract refuses a blank `q`, so there is none.
        assertThat(harness.calls.last().query).isEqualTo("")
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
                type = TruckType(
                    name = "Chilled 4 t box",
                    nameAr = null,
                    bodyType = EquipmentBody.BOX,
                    sizeClass = EquipmentSize.LIGHT,
                    temperature = EquipmentTemperature.CHILLED,
                ),
                capacityTons = "4",
            ),
        )

        val added = (profile as AppResult.Success).data
        // The truck's own registration is now outstanding, and the checklist has to show it.
        assertThat(added.missing).containsExactly(OnboardingGap.VehicleRegistration("t1"))
        assertThat(profileReads).isEqualTo(1)
        // The app talks in tonnes and the contract in kilograms.
        assertThat(harness.bodyOf(Api.TRUCKS).orEmpty()).contains(""""capacityKg":4000""")
        // The picked type goes back as its three axes; the type's name is the app's alone.
        assertThat(harness.bodyOf(Api.TRUCKS).orEmpty())
            .contains(""""bodyType":"box","sizeClass":"light","temperature":"chilled"""")
        assertThat(harness.bodyOf(Api.TRUCKS).orEmpty()).doesNotContain("Chilled 4 t box")
        assertThat(added.trucks.single().temperature).isEqualTo(EquipmentTemperature.CHILLED)
    }

    @Test
    fun `the truck types are the workspace's own, named in both languages`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.TRUCK_TYPES -> respondJson(
                    """{"items":[
                       {"name":"Reefer trailer","nameAr":"تريلا مبردة","bodyType":"box",
                        "sizeClass":"trailer","temperature":"chilled"},
                       {"name":"Dyna","nameAr":null,"bodyType":"open",
                        "sizeClass":"light","temperature":"ambient"},
                       {"name":"Hover truck","nameAr":null,"bodyType":"hover",
                        "sizeClass":"light","temperature":"ambient"}]}""",
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val types = (harness.onboarding.truckTypes() as AppResult.Success).data

        // A body this build has no enum for could not be sent back, so it is not offered.
        assertThat(types.map { it.name }).containsExactly("Reefer trailer", "Dyna")
        assertThat(types.first().sizeClass).isEqualTo(EquipmentSize.TRAILER)
        assertThat(types.first().displayName("ar")).isEqualTo("تريلا مبردة")
        assertThat(types.first().displayName("en")).isEqualTo("Reefer trailer")
        // No Arabic name: the workspace's own name, in every language.
        assertThat(types.last().displayName("ar")).isEqualTo("Dyna")
    }

    @Test
    fun `a truck type the workspace no longer runs is named as such`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.TRUCKS -> respondError("equipment_not_offered", HttpStatusCode.Conflict)
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val result = harness.onboarding.addTruck(
            TruckDraft(
                licencePlate = "RUH 9002",
                type = TruckType("Dyna", null, EquipmentBody.OPEN, EquipmentSize.LIGHT, EquipmentTemperature.AMBIENT),
            ),
        )

        assertThat((result as AppResult.Failure).error.reason).isEqualTo(AppErrorReason.EQUIPMENT_NOT_OFFERED)
        // The refusal is the answer; nothing is re-read on the way out.
        assertThat(harness.paths()).containsExactly(Api.TRUCKS)
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
    fun `a truck's pending type change and the last rejection are read from the profile`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.PROFILE -> respondJson(
                    profileJson(
                        status = "active",
                        missing = emptyList(),
                        trucks = listOf(
                            truckJson(id = "t1", pendingChange = truckChangeJson("box", "heavy", "frozen")),
                            truckJson(id = "t2", plate = "JED 1", rejectionReason = "The istimara says open."),
                            truckJson(id = "t3", plate = "DMM 2", pendingChange = truckChangeJson("hover", "light", "ambient")),
                        ),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val profile = (harness.onboarding.refresh() as AppResult.Success).data
        val (requested, rejected, unknown) = profile.trucks

        assertThat(profile.truckChangesReviewed).isEqualTo(true)
        // The truck is still what it was; the request sits beside it.
        assertThat(requested.sizeClass).isEqualTo(EquipmentSize.LIGHT)
        assertThat(requested.pendingChange?.sizeClass).isEqualTo(EquipmentSize.HEAVY)
        assertThat(requested.pendingChange?.temperature).isEqualTo(EquipmentTemperature.FROZEN)
        assertThat(rejected.pendingChange).isNull()
        assertThat(rejected.changeRejection?.reason).isEqualTo("The istimara says open.")
        // A body this build cannot name is not guessed at: the driver did not ask for a box.
        assertThat(unknown.pendingChange).isNull()
    }

    @Test
    fun `changing a truck's type sends its own plate and capacity with the new axes`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                Api.TRUCKS -> respondJson(
                    truckJson(pendingChange = truckChangeJson("open", "medium", "ambient")),
                    HttpStatusCode.Created,
                )
                Api.PROFILE -> respondJson(
                    profileJson(
                        status = "active",
                        missing = emptyList(),
                        trucks = listOf(truckJson(pendingChange = truckChangeJson("open", "medium", "ambient"))),
                    ),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }
        harness.onboarding.refresh()
        val truck = harness.onboarding.profile.value!!.trucks.single()

        val result = harness.onboarding.changeTruckType(
            truck,
            TruckType("Dyna 6 m", null, EquipmentBody.OPEN, EquipmentSize.MEDIUM, EquipmentTemperature.AMBIENT),
        )

        val body = harness.bodyOf(Api.TRUCKS).orEmpty()
        // The plate is how the server knows which truck; a different one would be a second truck.
        assertThat(body).contains(""""licencePlate":"RUH 9002"""")
        assertThat(body).contains(""""bodyType":"open","sizeClass":"medium","temperature":"ambient"""")
        // Left out, the capacity would be cleared by a change that was only about the type.
        assertThat(body).contains(""""capacityKg":4000""")
        // Read back, because the truck alone would not move the profile the screens are showing.
        assertThat(harness.paths()).containsExactly(Api.PROFILE, Api.TRUCKS, Api.PROFILE)
        val changed = (result as AppResult.Success).data.trucks.single()
        assertThat(changed.bodyType).isEqualTo(EquipmentBody.BOX)
        assertThat(changed.pendingChange?.bodyType).isEqualTo(EquipmentBody.OPEN)
    }

    @Test
    fun `withdrawing a change deletes the truck's request and answers with the profile`() = runTest {
        val harness = harness { request ->
            when (request.url.encodedPath) {
                "${Api.TRUCKS}/t1/change" -> respondJson(
                    profileJson(status = "active", missing = emptyList(), trucks = listOf(truckJson())),
                )
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }

        val result = harness.onboarding.withdrawTruckChange("t1")

        assertThat(harness.calls.single().method).isEqualTo("DELETE")
        assertThat((result as AppResult.Success).data.trucks.single().pendingChange).isNull()
        assertThat(harness.onboarding.profile.value).isEqualTo(result.data)
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

private const val RIYADH = "c1000000-0000-4000-8000-000000000001"
private const val JEDDAH = "c1000000-0000-4000-8000-000000000002"
private const val UNNAMED = "c1000000-0000-4000-8000-000000000099"
