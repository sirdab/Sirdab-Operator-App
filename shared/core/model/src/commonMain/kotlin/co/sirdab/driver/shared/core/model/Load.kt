package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CargoType { GENERAL, REEFER, HAZMAT, OVERSIZED, CONTAINER, LIVESTOCK }

@Serializable
enum class HandlingFlag { FRAGILE, REEFER_TEMP, HAZMAT, OVERSIZED_PERMIT, MULTI_STOP, TAIL_LIFT }

@Serializable
data class TimeWindow(
    val startMillis: Long,
    val endMillis: Long,
)

@Serializable
data class ReeferRange(
    val minC: Int,
    val maxC: Int,
)

@Serializable
data class Load(
    val id: String,
    val shipperId: String,
    val originCityId: String,
    val destinationCityId: String,
    val originName: String,
    val destinationName: String,
    val distanceKm: Int,
    val suggestedRateSar: Int,
    val requiredVehicle: VehicleType,
    val cargoType: CargoType = CargoType.GENERAL,
    val weightTons: Double,
    val pickupWindow: TimeWindow,
    val dropoffWindow: TimeWindow,
    val handlingFlags: List<HandlingFlag> = emptyList(),
    val reeferRange: ReeferRange? = null,
    val requiresCertification: String? = null,
    val stops: Int = 1,
    val instantBook: Boolean = false,
    val fixedRateSar: Int? = null,
    val polylineId: String? = null,
    val postedAtMillis: Long,
    val distanceFromDriverKm: Int = 0,
)
