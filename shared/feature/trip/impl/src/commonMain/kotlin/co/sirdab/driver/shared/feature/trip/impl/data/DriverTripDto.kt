package co.sirdab.driver.shared.feature.trip.impl.data

import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.StopAddress
import co.sirdab.driver.shared.core.model.StopContact
import co.sirdab.driver.shared.core.model.StopStatus
import co.sirdab.driver.shared.core.model.StopType
import co.sirdab.driver.shared.core.model.TripLeg
import co.sirdab.driver.shared.core.model.TripLifecycle
import co.sirdab.driver.shared.core.model.TripStop
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The wire shapes, mirroring `packages/tms-contracts/src/driver/trips.ts` field for field.
 *
 * Kept separate from the domain model on purpose. The contract is versioned by someone else and has
 * a breaking change already announced for the stop address, so the app absorbs that here, in the
 * mapper, rather than across every screen.
 */
@Serializable
internal data class DriverTripSummaryDto(
    val id: String,
    val reference: String,
    val status: String,
    val scheduledAt: String? = null,
    val inCity: Boolean = false,
    val stopCount: Int = 0,
)

@Serializable
internal data class DriverTripDetailDto(
    val id: String,
    val reference: String,
    val status: String,
    val scheduledAt: String? = null,
    val inCity: Boolean = false,
    val stopCount: Int = 0,
    val legs: List<DriverTripLegDto> = emptyList(),
    val stops: List<DriverTripStopDto> = emptyList(),
)

@Serializable
internal data class DriverTripLegDto(
    val id: String,
    val sequence: Int,
    val originAddressId: String,
    val destinationAddressId: String,
)

@Serializable
internal data class DriverTripStopDto(
    val id: String,
    val legId: String,
    val stopType: String,
    val sequenceNumber: Int,
    val status: String,
    val plannedAt: String? = null,
    val arrivedAt: String? = null,
    val departedAt: String? = null,
    val address: StopAddressDto,
    val contact: StopContactDto? = null,
    val photoProofCount: Int = 0,
)

@Serializable
internal data class StopAddressDto(
    val id: String,
    val label: String,
    val street: String? = null,
    val district: String? = null,
    val buildingNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
internal data class StopContactDto(
    @SerialName("name") val name: String,
    @SerialName("phone") val phone: String,
)

/**
 * Timestamps arrive as ISO-8601 with an explicit offset. An unparseable one becomes null rather
 * than throwing: a trip with one bad date is still a trip the driver has to run, and losing the
 * whole screen over it would be worse than losing the time.
 */
internal fun String?.toEpochMillisOrNull(): Long? {
    if (this.isNullOrBlank()) return null
    return runCatching { Instant.parse(this).toEpochMilliseconds() }.getOrNull()
}

/**
 * An unknown enum value maps to the safest reading rather than throwing, so a server that adds a
 * status does not break every client that has not upgraded.
 */
internal fun String.toTripLifecycle(): TripLifecycle = when (this) {
    "created" -> TripLifecycle.CREATED
    "assigned" -> TripLifecycle.ASSIGNED
    "in_transit" -> TripLifecycle.IN_TRANSIT
    "complete" -> TripLifecycle.COMPLETE
    "cancelled" -> TripLifecycle.CANCELLED
    "failed" -> TripLifecycle.FAILED
    else -> TripLifecycle.CREATED
}

internal fun String.toStopType(): StopType =
    if (this == "dropoff") StopType.DROPOFF else StopType.PICKUP

internal fun String.toStopStatus(): StopStatus = when (this) {
    "arrived" -> StopStatus.ARRIVED
    "departed" -> StopStatus.DEPARTED
    "completed" -> StopStatus.COMPLETED
    "skipped" -> StopStatus.SKIPPED
    else -> StopStatus.PENDING
}

internal fun DriverTripSummaryDto.toDomain(): DriverTrip = DriverTrip(
    id = id,
    reference = reference,
    status = status.toTripLifecycle(),
    scheduledAtMillis = scheduledAt.toEpochMillisOrNull(),
    inCity = inCity,
    stopCount = stopCount,
)

internal fun DriverTripDetailDto.toDomain(): DriverTrip = DriverTrip(
    id = id,
    reference = reference,
    status = status.toTripLifecycle(),
    scheduledAtMillis = scheduledAt.toEpochMillisOrNull(),
    inCity = inCity,
    stopCount = stopCount,
    legs = legs.map { TripLeg(it.id, it.sequence, it.originAddressId, it.destinationAddressId) },
    // The server sends them in running order; sorting here means a client never depends on that.
    stops = stops.sortedBy { it.sequenceNumber }.map { it.toDomain() },
)

internal fun DriverTripStopDto.toDomain(): TripStop = TripStop(
    id = id,
    legId = legId,
    stopType = stopType.toStopType(),
    sequenceNumber = sequenceNumber,
    status = status.toStopStatus(),
    plannedAtMillis = plannedAt.toEpochMillisOrNull(),
    arrivedAtMillis = arrivedAt.toEpochMillisOrNull(),
    departedAtMillis = departedAt.toEpochMillisOrNull(),
    address = StopAddress(
        id = address.id,
        label = address.label,
        street = address.street,
        district = address.district,
        buildingNumber = address.buildingNumber,
        zipCode = address.zipCode,
        city = address.city,
        lat = address.lat,
        lng = address.lng,
    ),
    contact = contact?.let { StopContact(it.name, it.phone) },
    photoProofCount = photoProofCount,
)
