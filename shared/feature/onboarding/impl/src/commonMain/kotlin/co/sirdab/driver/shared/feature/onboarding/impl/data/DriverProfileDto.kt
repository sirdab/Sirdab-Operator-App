package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.feature.onboarding.api.domain.City
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileStatus
import co.sirdab.driver.shared.feature.onboarding.api.domain.OnboardingGap
import co.sirdab.driver.shared.feature.onboarding.api.domain.PreferredRoute
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileDocument
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileTruck
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileWorkspace
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckType
import kotlinx.serialization.Serializable

/** `GET`, `PATCH`, document confirm and truck delete all answer with this. */
@Serializable
internal data class DriverProfileDto(
    val userId: String,
    val name: String = "",
    val phone: String? = null,
    val nationality: String? = null,
    val licenceNumber: String? = null,
    val licenceExpiresAt: String? = null,
    val homeCityId: String? = null,
    val preferredRoutes: List<PreferredRouteDto> = emptyList(),
    /** The name of every city [homeCityId] and [preferredRoutes] mention, and no others. */
    val cities: List<CityDto> = emptyList(),
    val status: String = "pending_verification",
    val onboardingCompletedAt: String? = null,
    /** What onboarding still wants, in the server's own words. */
    val missing: List<String> = emptyList(),
    val trucks: List<ProfileTruckDto> = emptyList(),
    val documents: List<ProfileDocumentDto> = emptyList(),
    val workspaces: List<ProfileWorkspaceDto> = emptyList(),
)

/** Ids only, here and in the PATCH: the names come once, in the profile's `cities`. */
@Serializable
internal data class PreferredRouteDto(
    val originCityId: String,
    val destinationCityId: String,
)

/** A city in the profile's `cities`, and in `GET /api/v1/cities`, which sends more than this. */
@Serializable
internal data class CityDto(
    val id: String,
    val name: String,
    val nameAr: String? = null,
)

/** `GET /api/v1/cities` (contracts 10.43.0). One page is all the app reads: the search narrows it. */
@Serializable
internal data class CitiesDto(val items: List<CityDto> = emptyList())

@Serializable
internal data class ProfileTruckDto(
    val id: String,
    val licencePlate: String,
    val bodyType: String,
    val sizeClass: String,
    val temperature: String,
    val capacityKg: Int? = null,
)

@Serializable
internal data class ProfileDocumentDto(
    val id: String,
    val kind: String,
    val truckId: String? = null,
    val contentType: String = "",
    val status: String = "uploaded",
    val rejectionReason: String? = null,
    val expiresAt: String? = null,
    val uploadedAt: String? = null,
    /** Signed for ten minutes, and null until the upload is confirmed. */
    val downloadUrl: String? = null,
)

@Serializable
internal data class ProfileWorkspaceDto(
    val workspaceId: String,
    val workspaceName: String = "",
    val driverId: String,
    val carrierId: String,
)

/**
 * `PATCH /api/driver/profile`. Every field optional: only what the driver just filled in is sent.
 *
 * A null is left out of the body rather than sent, which for [preferredRoutes] is the difference
 * between keeping the list and clearing it.
 */
@Serializable
internal data class UpdateProfileDto(
    val name: String? = null,
    val nationality: String? = null,
    val licenceNumber: String? = null,
    val licenceExpiresAt: String? = null,
    val homeCityId: String? = null,
    val preferredRoutes: List<PreferredRouteDto>? = null,
)

/** `POST /api/driver/profile/trucks`. All three axes are required: a truck missing one never matches. */
@Serializable
internal data class AddTruckDto(
    val licencePlate: String,
    val bodyType: String,
    val sizeClass: String,
    val temperature: String,
    val capacityKg: Int? = null,
)

/** `GET /api/driver/equipment-types` (contracts 10.38.0). */
@Serializable
internal data class TruckTypesDto(val items: List<TruckTypeDto> = emptyList())

/** One combination of the three axes, named the way a posting's equipment is. */
@Serializable
internal data class TruckTypeDto(
    val name: String,
    val nameAr: String? = null,
    val bodyType: String,
    val sizeClass: String,
    val temperature: String,
)

/** `POST /api/driver/profile/documents`: declare the file, get somewhere to put it. */
@Serializable
internal data class CreateDocumentDto(
    val kind: String,
    val truckId: String? = null,
    val contentType: String,
    val sizeBytes: Int,
)

/**
 * Where the bytes go.
 *
 * [headers] are the ones the signature was computed over; they are replayed verbatim and nothing
 * is added to them, because storage recomputes the signature from what it receives.
 */
@Serializable
internal data class CreateDocumentResultDto(
    val documentId: String,
    val uploadUrl: String,
    val method: String = "PUT",
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: String? = null,
    val path: String = "",
)

/** `DELETE /api/driver/account`. Nothing beyond the fact: the session it answered is gone. */
@Serializable
internal data class DeletedAccountDto(val deleted: Boolean)

/** `POST /api/v1/me/active-workspace`. */
@Serializable
internal data class SwitchWorkspaceDto(val workspaceId: String)

/**
 * The switch answers with the whole `Me`, of which the driver app needs one field — and not even
 * that, really: the token it refreshes afterwards is the authority on where it now is.
 */
@Serializable
internal data class SwitchWorkspaceResultDto(val activeWorkspaceId: String? = null)

/**
 * A type with an axis this build cannot name is left out rather than offered: it could not be sent
 * back as the enum the request is built from, and the server's vocabulary only widens with a
 * contract release the app would follow.
 */
internal fun TruckTypeDto.toDomain(): TruckType? = TruckType(
    name = name,
    nameAr = nameAr,
    bodyType = EquipmentBody.fromWire(bodyType) ?: return null,
    sizeClass = EquipmentSize.fromWire(sizeClass) ?: return null,
    temperature = EquipmentTemperature.fromWire(temperature) ?: return null,
)

internal fun CityDto.toDomain(): City = City(id = id, name = name, nameAr = nameAr)

internal fun DriverProfileDto.toDomain(): DriverProfile {
    // The server names every city it refers to, so a miss is a city it could not find. Kept with a
    // blank name rather than dropped: the PATCH replaces the whole list, and saving a list with a
    // route quietly missing would delete it.
    val citiesById = cities.associate { it.id to it.toDomain() }
    fun city(id: String) = citiesById[id] ?: City(id = id, name = "", nameAr = null)

    return DriverProfile(
        userId = userId,
        name = name,
        phone = phone,
        nationality = Nationality.fromWire(nationality),
        licenceNumber = licenceNumber,
        licenceExpiresAt = licenceExpiresAt,
        homeCity = homeCityId?.let(::city),
        preferredRoutes = preferredRoutes.map {
            PreferredRoute(origin = city(it.originCityId), destination = city(it.destinationCityId))
        },
        status = DriverProfileStatus.fromWire(status),
        onboardingCompletedAt = onboardingCompletedAt,
        missing = missing.map(OnboardingGap::fromWire),
        trucks = trucks.map { truck ->
            ProfileTruck(
                id = truck.id,
                licencePlate = truck.licencePlate,
                // An unknown axis value is a contract that moved on without the app. Falling back
                // keeps the profile readable; the pickers are built off the enums, so the swap when
                // the vocabulary widens is an enum and its labels.
                bodyType = EquipmentBody.fromWire(truck.bodyType) ?: EquipmentBody.BOX,
                sizeClass = EquipmentSize.fromWire(truck.sizeClass) ?: EquipmentSize.MEDIUM,
                temperature = EquipmentTemperature.fromWire(truck.temperature) ?: EquipmentTemperature.AMBIENT,
                capacityKg = truck.capacityKg,
            )
        },
        documents = documents.mapNotNull { doc ->
            // A kind this build cannot name is a document it cannot render or replace, and inventing
            // a placeholder for it would put a row on the checklist that does nothing.
            val kind = DriverDocumentKind.fromWire(doc.kind) ?: return@mapNotNull null
            ProfileDocument(
                id = doc.id,
                kind = kind,
                truckId = doc.truckId,
                status = DriverDocumentStatus.fromWire(doc.status),
                rejectionReason = doc.rejectionReason,
                uploadedAt = doc.uploadedAt,
                downloadUrl = doc.downloadUrl,
            )
        },
        workspaces = workspaces.map {
            ProfileWorkspace(
                workspaceId = it.workspaceId,
                workspaceName = it.workspaceName,
                driverId = it.driverId,
                carrierId = it.carrierId,
            )
        },
    )
}
