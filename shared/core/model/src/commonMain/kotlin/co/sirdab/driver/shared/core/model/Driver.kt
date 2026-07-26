package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class VerificationState { UNVERIFIED, PENDING, VERIFIED }

@Serializable
enum class VehicleType {
    FLATBED,
    CURTAIN_SIDER,
    REEFER,
    CONTAINER_40FT,
    DRY_VAN,
    VAN_3T,
    LOWBED,
    TANKER,
}

@Serializable
data class Vehicle(
    val type: VehicleType,
    val plate: String,
    val capacityTons: Double,
)

@Serializable
data class Driver(
    val id: String,
    val fullNameEn: String,
    val fullNameAr: String,
    val phone: String,
    val verification: VerificationState = VerificationState.UNVERIFIED,
    val carrierScore: Double = 0.0,
    val tripsCompleted: Int = 0,
    val onTimePercent: Int = 0,
    val vehicle: Vehicle? = null,
    /** Feature-flag-like markers used by the demo scenario switcher. */
    val personaKey: String = "flatbed_verified",
)
