package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class VerificationState { UNVERIFIED, PENDING, VERIFIED }

/**
 * What the trailer is, as the TMS models it: `truck_type` on the server.
 *
 * Type and size are two separate questions. A closed lorry can be dry, chilled
 * or frozen, and conflating them into one list (the old `VehicleType`) meant the
 * app could not say which.
 */
@Serializable
enum class TruckType(val wire: String) {
    DRY("dry"),
    CHILLED("chilled"),
    FROZEN("frozen"),
}

/**
 * How big the truck is: `truck_size` on the server.
 *
 * Mirrors the server enum exactly, in the same order. Pending Zaheer's list,
 * which may not match: the server has no lowbed or tanker, both of which the
 * demo fixtures still use.
 */
@Serializable
enum class TruckSize(val wire: String) {
    CARGO_VAN("cargo_van"),
    OPEN_DYNA("open_dyna"),
    CLOSED_DYNA("closed_dyna"),
    OPEN_LORRY("open_lorry"),
    CLOSED_LORRY("closed_lorry"),
    WINCH("winch"),
    FLATBED("flatbed"),
    CURTAIN_SIDE("curtain_side"),
    LTL("ltl"),
    TRAILER("trailer"),
}

/**
 * The truck this operator drives.
 *
 * Not to be confused with [VehicleType], which is still the demo load board's
 * single-axis taxonomy and is unrelated to what the operator owns.
 */
@Serializable
data class Vehicle(
    val truckType: TruckType,
    val truckSize: TruckSize,
    val plate: String,
    val capacityTons: Double,
)

/**
 * The demo load board's requirement taxonomy, one axis instead of two.
 *
 * Kept only because the seeded loads use it. Real loads come from the TMS with
 * `truckType` and `truckSize`, so this retires with the demo board.
 */
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
