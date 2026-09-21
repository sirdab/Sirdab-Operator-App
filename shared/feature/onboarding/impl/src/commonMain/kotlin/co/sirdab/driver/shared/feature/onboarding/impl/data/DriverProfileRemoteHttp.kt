package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import co.sirdab.driver.shared.core.model.Vehicle
import co.sirdab.driver.shared.core.model.VerificationState
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileRemote
import kotlinx.serialization.Serializable

@Serializable
internal data class DriverMeDto(
    val profile: ProfileDto,
    val workspaceId: String,
    val driver: DriverDto,
    val carrier: CarrierDto,
    val trucks: List<TruckDto> = emptyList(),
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

    override suspend fun fetch(): AppResult<Driver> =
        api.get(PATH, DriverMeDto.serializer()).fold(
            onSuccess = { AppResult.Success(it.value.toDomain()) },
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
