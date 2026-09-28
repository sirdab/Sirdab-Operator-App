package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class VerificationState { UNVERIFIED, PENDING, VERIFIED }

/**
 * What the truck is built as: `bodyType` on the server, one of the platform's three equipment axes.
 *
 * A reefer is not a body. It is a [BOX] whose [EquipmentTemperature] is not ambient, which is why
 * the three questions are asked separately rather than as one list.
 */
@Serializable
enum class EquipmentBody(val wire: String) {
    BOX("box"),
    OPEN("open"),
    FLATBED("flatbed"),
    CURTAIN_SIDE("curtain_side"),
    LOWBED("lowbed"),
    TANKER("tanker"),
    CONTAINER("container"),
    TIPPER("tipper");

    companion object {
        /** Null for a value this build cannot name, or for an axis the server left unclassified. */
        fun fromWire(wire: String?): EquipmentBody? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * How big the truck is: `sizeClass` on the server. [PICKUP] is the wanit class, cargo vans and
 * pickups up to about 1.5 t, and is a size rather than a body.
 */
@Serializable
enum class EquipmentSize(val wire: String) {
    PICKUP("pickup"),
    LIGHT("light"),
    MEDIUM("medium"),
    HEAVY("heavy"),
    TRAILER("trailer");

    companion object {
        fun fromWire(wire: String?): EquipmentSize? = entries.firstOrNull { it.wire == wire }
    }
}

/** What the truck can hold the load at: `temperature` on the server. */
@Serializable
enum class EquipmentTemperature(val wire: String) {
    AMBIENT("ambient"),
    CHILLED("chilled"),
    FROZEN("frozen"),
    MULTI("multi");

    companion object {
        fun fromWire(wire: String?): EquipmentTemperature? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * The truck this operator drives.
 *
 * Not to be confused with [VehicleType], which is still the demo load board's
 * single-axis taxonomy and is unrelated to what the operator owns.
 */
@Serializable
data class Vehicle(
    /** Null when the fleet's catalog row never classified that axis. */
    val bodyType: EquipmentBody?,
    val sizeClass: EquipmentSize?,
    val temperature: EquipmentTemperature?,
    val plate: String,
    val capacityTons: Double,
)

/**
 * The demo load board's requirement taxonomy, one axis instead of two.
 *
 * Kept only because the seeded loads use it. Real loads come from the TMS with
 * `bodyType`, `sizeClass` and `temperature`, so this retires with the demo board.
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
