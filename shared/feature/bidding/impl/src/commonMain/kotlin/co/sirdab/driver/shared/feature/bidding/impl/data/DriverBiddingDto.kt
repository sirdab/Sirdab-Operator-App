package co.sirdab.driver.shared.feature.bidding.impl.data

import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverBidStatus
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.model.Money
import co.sirdab.driver.shared.core.model.PostingPlace
import co.sirdab.driver.shared.core.model.PostingStatus
import kotlinx.serialization.Serializable
import co.sirdab.driver.shared.core.network.toEpochMillisOrNull

@Serializable
internal data class MoneyDto(val amountCents: Int, val currency: String = "SAR")

@Serializable
internal data class PlaceDto(val label: String, val city: String? = null)

/**
 * What a posting needs, or what a fleet truck is: a workspace's catalog row as the driver app sees
 * it. `/api/driver/me` adds the row's `id` and `code`, which the app has no use for. Any axis may
 * be null on a row that was never classified.
 */
@Serializable
internal data class EquipmentDto(
    val name: String = "",
    val nameAr: String? = null,
    val bodyType: String? = null,
    val sizeClass: String? = null,
    val temperature: String? = null,
)

@Serializable
internal data class DriverPostingDto(
    val id: String,
    val loadId: String,
    val status: String,
    val pickupWindowStart: String? = null,
    val pickupWindowEnd: String? = null,
    val targetRate: MoneyDto? = null,
    val biddingClosesAt: String? = null,
    val origin: PlaceDto,
    val destination: PlaceDto,
    val equipment: EquipmentDto,
)

@Serializable
internal data class BidInputDto(
    val amountCents: Int,
    val truckId: String,
    val note: String? = null,
)

@Serializable
internal data class BidDto(
    val id: String,
    val loadPostingId: String,
    val truckId: String,
    val amountCents: Int,
    val currency: String = "SAR",
    val status: String,
    val note: String? = null,
    val createdAt: String? = null,
)

/** Unknown values degrade rather than throw: a new status must not empty the board. */
internal fun String.toPostingStatus(): PostingStatus = when (this) {
    "awarded" -> PostingStatus.AWARDED
    "cancelled" -> PostingStatus.CANCELLED
    "expired" -> PostingStatus.EXPIRED
    else -> PostingStatus.OPEN
}

internal fun String.toDriverBidStatus(): DriverBidStatus = when (this) {
    "won" -> DriverBidStatus.WON
    "lost" -> DriverBidStatus.LOST
    "withdrawn" -> DriverBidStatus.WITHDRAWN
    "expired" -> DriverBidStatus.EXPIRED
    else -> DriverBidStatus.PENDING
}

internal fun DriverPostingDto.toDomain(): DriverPosting = DriverPosting(
    id = id,
    loadId = loadId,
    status = status.toPostingStatus(),
    origin = PostingPlace(origin.label, origin.city),
    destination = PostingPlace(destination.label, destination.city),
    bodyType = EquipmentBody.fromWire(equipment.bodyType),
    sizeClass = EquipmentSize.fromWire(equipment.sizeClass),
    temperature = EquipmentTemperature.fromWire(equipment.temperature),
    targetRate = targetRate?.let { Money(it.amountCents, it.currency) },
    pickupWindowStartMillis = pickupWindowStart.toEpochMillisOrNull(),
    pickupWindowEndMillis = pickupWindowEnd.toEpochMillisOrNull(),
    biddingClosesAtMillis = biddingClosesAt.toEpochMillisOrNull(),
)

internal fun BidDto.toDomain(): DriverBid = DriverBid(
    id = id,
    loadPostingId = loadPostingId,
    truckId = truckId,
    amount = Money(amountCents, currency),
    status = status.toDriverBidStatus(),
    note = note,
    createdAtMillis = createdAt.toEpochMillisOrNull(),
)

/** Only the fleet is read here; `trucks` stayed top level on the driver profile. */
@Serializable
internal data class MeTrucksDto(val trucks: List<MeTruckDto> = emptyList())

@Serializable
internal data class MeTruckDto(
    val id: String,
    val licencePlate: String,
    val equipment: EquipmentDto,
)

internal fun MeTruckDto.toDomain(): DriverTruck = DriverTruck(
    id = id,
    licencePlate = licencePlate,
    bodyType = EquipmentBody.fromWire(equipment.bodyType),
    sizeClass = EquipmentSize.fromWire(equipment.sizeClass),
    temperature = EquipmentTemperature.fromWire(equipment.temperature),
)
