package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileStatus
import co.sirdab.driver.shared.feature.onboarding.api.domain.OnboardingGap
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileDocument
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileTruck
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileWorkspace
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
    val status: String = "pending_verification",
    val onboardingCompletedAt: String? = null,
    /** What onboarding still wants, in the server's own words. */
    val missing: List<String> = emptyList(),
    val trucks: List<ProfileTruckDto> = emptyList(),
    val documents: List<ProfileDocumentDto> = emptyList(),
    val workspaces: List<ProfileWorkspaceDto> = emptyList(),
)

@Serializable
internal data class ProfileTruckDto(
    val id: String,
    val licencePlate: String,
    val truckType: String,
    val truckSize: String,
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

/** `PATCH /api/driver/profile`. Every field optional: only what the driver just filled in is sent. */
@Serializable
internal data class UpdateProfileDto(
    val name: String? = null,
    val nationality: String? = null,
    val licenceNumber: String? = null,
    val licenceExpiresAt: String? = null,
)

/** `POST /api/driver/profile/trucks`. */
@Serializable
internal data class AddTruckDto(
    val licencePlate: String,
    val truckType: String,
    val truckSize: String,
    val capacityKg: Int? = null,
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

/** `POST /api/v1/me/active-workspace`. */
@Serializable
internal data class SwitchWorkspaceDto(val workspaceId: String)

/**
 * The switch answers with the whole `Me`, of which the driver app needs one field — and not even
 * that, really: the token it refreshes afterwards is the authority on where it now is.
 */
@Serializable
internal data class SwitchWorkspaceResultDto(val activeWorkspaceId: String? = null)

internal fun DriverProfileDto.toDomain(): DriverProfile = DriverProfile(
    userId = userId,
    name = name,
    phone = phone,
    nationality = Nationality.fromWire(nationality),
    licenceNumber = licenceNumber,
    licenceExpiresAt = licenceExpiresAt,
    status = DriverProfileStatus.fromWire(status),
    onboardingCompletedAt = onboardingCompletedAt,
    missing = missing.map(OnboardingGap::fromWire),
    trucks = trucks.map { truck ->
        ProfileTruck(
            id = truck.id,
            licencePlate = truck.licencePlate,
            // An unknown truck type or size is a contract that moved on without the app. Falling
            // back keeps the profile readable; the picker is built off the enum, so the swap when
            // the vocabulary widens is one enum and its labels.
            truckType = TruckType.entries.firstOrNull { it.wire == truck.truckType } ?: TruckType.DRY,
            truckSize = TruckSize.entries.firstOrNull { it.wire == truck.truckSize }
                ?: TruckSize.CLOSED_LORRY,
            capacityKg = truck.capacityKg,
        )
    },
    documents = documents.mapNotNull { doc ->
        // A kind this build cannot name is a document it cannot render or replace, and inventing a
        // placeholder for it would put a row on the checklist that does nothing.
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
