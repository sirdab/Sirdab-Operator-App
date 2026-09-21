package co.sirdab.driver.shared.feature.bidding.impl.data

import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverBidStatus
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.Money
import co.sirdab.driver.shared.core.model.PostingPlace
import co.sirdab.driver.shared.core.model.PostingStatus
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class MoneyDto(val amountCents: Int, val currency: String = "SAR")

@Serializable
internal data class PlaceDto(val label: String, val city: String? = null)

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
    val truckType: String,
    val truckSize: String,
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

internal fun String?.toMillisOrNull(): Long? {
    if (this.isNullOrBlank()) return null
    return runCatching { Instant.parse(this).toEpochMilliseconds() }.getOrNull()
}

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

internal fun String.toTruckType(): TruckType =
    TruckType.entries.firstOrNull { it.wire == this } ?: TruckType.DRY

internal fun String.toTruckSize(): TruckSize =
    TruckSize.entries.firstOrNull { it.wire == this } ?: TruckSize.CLOSED_LORRY

internal fun DriverPostingDto.toDomain(): DriverPosting = DriverPosting(
    id = id,
    loadId = loadId,
    status = status.toPostingStatus(),
    origin = PostingPlace(origin.label, origin.city),
    destination = PostingPlace(destination.label, destination.city),
    truckType = truckType.toTruckType(),
    truckSize = truckSize.toTruckSize(),
    targetRate = targetRate?.let { Money(it.amountCents, it.currency) },
    pickupWindowStartMillis = pickupWindowStart.toMillisOrNull(),
    pickupWindowEndMillis = pickupWindowEnd.toMillisOrNull(),
    biddingClosesAtMillis = biddingClosesAt.toMillisOrNull(),
)

internal fun BidDto.toDomain(): DriverBid = DriverBid(
    id = id,
    loadPostingId = loadPostingId,
    truckId = truckId,
    amount = Money(amountCents, currency),
    status = status.toDriverBidStatus(),
    note = note,
    createdAtMillis = createdAt.toMillisOrNull(),
)

/** Only the fleet is read here; `trucks` stayed top level on the driver profile. */
@Serializable
internal data class MeTrucksDto(val trucks: List<MeTruckDto> = emptyList())

@Serializable
internal data class MeTruckDto(
    val id: String,
    val licencePlate: String,
    val truckType: String,
    val truckSize: String,
)

internal fun MeTruckDto.toDomain(): DriverTruck = DriverTruck(
    id = id,
    licencePlate = licencePlate,
    truckType = truckType.toTruckType(),
    truckSize = truckSize.toTruckSize(),
)
