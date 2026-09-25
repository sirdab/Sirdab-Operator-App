package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.DriverVerification
import co.sirdab.driver.shared.core.model.VerificationDocument
import co.sirdab.driver.shared.core.model.VerificationDocumentKind
import co.sirdab.driver.shared.core.model.VerificationDocumentStatus
import co.sirdab.driver.shared.core.model.VerificationSummary
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import co.sirdab.driver.shared.core.model.Vehicle
import co.sirdab.driver.shared.core.model.VerificationState
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileRemote
import co.sirdab.driver.shared.feature.onboarding.api.domain.FleetDriver
import kotlinx.serialization.Serializable

@Serializable
internal data class DriverMeDto(
    val profile: ProfileDto,
    val workspaceId: String,
    val driver: DriverDto,
    val carrier: CarrierDto,
    val trucks: List<TruckDto> = emptyList(),
    val documents: List<VerificationDocumentDto> = emptyList(),
    /**
     * Absent only from a server older than this build; the contract always sends it.
     *
     * Defaulting to "may work" rather than "may not": the gate exists to keep a driver off a board
     * the server would refuse, and a field that did not arrive is not a refusal.
     */
    val verification: VerificationDto = VerificationDto(),
)

@Serializable
internal data class VerificationDto(
    val status: String = "approved",
    val canAcceptLoads: Boolean = true,
)

@Serializable
internal data class VerificationDocumentDto(
    val id: String,
    val kind: String,
    val subject: String = "driver",
    val subjectId: String? = null,
    val fileId: String? = null,
    val status: String = "pending",
    val rejectionReason: String? = null,
    val expiresAt: String? = null,
    val reviewedAt: String? = null,
    val submittedAt: String? = null,
    val expired: Boolean = false,
)

@Serializable
internal data class ProfileDto(
    val id: String,
    val name: String,
    val phone: String? = null,
)

@Serializable
internal data class DriverDto(
    val id: String,
    val name: String,
    val status: String = "active",
    val licenceNumber: String? = null,
    val licenceExpiresAt: String? = null,
)

@Serializable
internal data class CarrierDto(
    val id: String,
    val name: String,
    val kind: String? = null,
)

@Serializable
internal data class TruckDto(
    val id: String,
    val licencePlate: String,
    val truckType: String,
    val truckSize: String,
    val capacityKg: Int? = null,
    val status: String = "active",
)

/** `GET /api/driver/me`. */
class DriverProfileRemoteHttp(private val api: TmsApiClient) : DriverProfileRemote {

    override suspend fun fetch(): AppResult<FleetDriver> =
        api.get(PATH, DriverMeDto.serializer()).fold(
            onSuccess = { AppResult.Success(it.value.toFleetDriver()) },
            onFailure = { error ->
                AppResult.Failure(
                    error.apiFailure?.toAppError()
                        ?: AppError(error.message ?: "Could not load your profile."),
                )
            },
        )

    private companion object {
        const val PATH = "api/driver/me"
    }
}

/**
 * The driver, and the fleet's verdict on them, from the one call that carries both.
 *
 * The verdict is not derived from the documents here. `canAcceptLoads` accounts for expiry and for
 * a deactivated driver or truck, none of which a document's status shows.
 */
internal fun DriverMeDto.toFleetDriver(): FleetDriver = FleetDriver(
    driver = toDomain(),
    verification = DriverVerification(
        status = VerificationSummary.fromWire(verification.status),
        canAcceptLoads = verification.canAcceptLoads,
        documents = documents.mapNotNull { document ->
            // A kind this build cannot name is one it cannot label or act on, and a blank row in
            // the document list helps nobody.
            val kind = VerificationDocumentKind.fromWire(document.kind) ?: return@mapNotNull null
            VerificationDocument(
                id = document.id,
                kind = kind,
                status = VerificationDocumentStatus.fromWire(document.status),
                rejectionReason = document.rejectionReason,
                expiresAt = document.expiresAt,
                expired = document.expired,
            )
        },
    ),
)

/**
 * The server has one name, not the two the demo world carries. Putting it in
 * both fields keeps the profile screen correct in either language until the
 * contract offers a localised name.
 */
internal fun DriverMeDto.toDomain(): Driver = Driver(
    // The driver row's id, which is what the API scopes trips and bids by, not
    // the profile's: the same person could be a driver in two accounts.
    id = driver.id,
    fullNameEn = driver.name,
    fullNameAr = driver.name,
    phone = profile.phone.orEmpty(),
    verification = if (driver.status == "active") {
        VerificationState.VERIFIED
    } else {
        VerificationState.UNVERIFIED
    },
    // The driver's own truck is whichever of the carrier's fleet they are on.
    // The contract does not say yet, so the first is shown rather than none.
    vehicle = trucks.firstOrNull()?.let { truck ->
        Vehicle(
            truckType = TruckType.entries.firstOrNull { it.wire == truck.truckType } ?: TruckType.DRY,
            truckSize = TruckSize.entries.firstOrNull { it.wire == truck.truckSize }
                ?: TruckSize.CLOSED_LORRY,
            plate = truck.licencePlate,
            // The contract carries kilograms; the app has always shown tonnes.
            capacityTons = truck.capacityKg?.let { it / 1000.0 } ?: 0.0,
        )
    },
)
