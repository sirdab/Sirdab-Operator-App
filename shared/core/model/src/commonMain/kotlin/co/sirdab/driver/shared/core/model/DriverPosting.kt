package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

/** Money as the TMS sends it: integer minor units, never a float. */
@Serializable
data class Money(
    val amountCents: Int,
    val currency: String = "SAR",
) {
    val amountSar: Int get() = amountCents / 100
}

@Serializable
enum class PostingStatus { OPEN, AWARDED, CANCELLED, EXPIRED }

/**
 * The least and the most a bid on an open-price posting may ask, in halalas. Either bound may
 * stand alone. The server refuses a bid outside it with `bid_out_of_range`.
 *
 * The dispatcher may set a bound to the halala, and the driver types whole riyals, so [minSar]
 * rounds up and [maxSar] rounds down: they are the whole-riyal amounts that actually fit.
 */
@Serializable
data class BidRange(
    val minCents: Int? = null,
    val maxCents: Int? = null,
) {
    val minSar: Int? get() = minCents?.let { (it + 99) / 100 }
    val maxSar: Int? get() = maxCents?.let { it / 100 }

    /** Long, so a driver typing a very long number cannot overflow their way inside it. */
    operator fun contains(amountCents: Long): Boolean =
        (minCents == null || amountCents >= minCents) && (maxCents == null || amountCents <= maxCents)
}

/**
 * How a bid turned out on the TMS. There is no counter-offer: the carrier may reprice a pending
 * bid, and the dispatcher awards.
 */
@Serializable
enum class DriverBidStatus { PENDING, WON, LOST, WITHDRAWN, EXPIRED }

@Serializable
data class PostingPlace(
    val label: String,
    val city: String? = null,
)

/** One of the carrier's trucks, which is what a bid commits. */
@Serializable
data class DriverTruck(
    val id: String,
    val licencePlate: String,
    /** The fleet catalog row's axes, any of which it may have left unclassified. */
    val bodyType: EquipmentBody?,
    val sizeClass: EquipmentSize?,
    val temperature: EquipmentTemperature?,
)

/**
 * How a posting is priced, which decides the answer a driver is offered.
 *
 * [FIXED] is accepted at the posting's rate and never countered, [RANGE] takes an amount inside
 * its bounds, and [OPEN] takes any amount.
 */
@Serializable
enum class BidType { FIXED, RANGE, OPEN }

/**
 * A load offered to this carrier.
 *
 * An offer with a deadline, carrying no handling flags and no suggested rate of its own.
 *
 * [targetRate] is the whole difference between two products. Set, the posting is
 * take-it-or-leave-it and the driver just accepts, which is what a short
 * fulfilment window needs. Null, and the price is open to bid, inside [bidRange]
 * when the dispatcher set one.
 */
@Serializable
data class DriverPosting(
    val id: String,
    val loadId: String,
    val status: PostingStatus,
    val origin: PostingPlace,
    val destination: PostingPlace,
    /** What the load needs, matched on these axes; null on a posting that was never classified. */
    val bodyType: EquipmentBody?,
    val sizeClass: EquipmentSize?,
    val temperature: EquipmentTemperature?,
    val targetRate: Money? = null,
    /** Null when the price is open without bounds. A fixed rate always sits inside it. */
    val bidRange: BidRange? = null,
    val pickupWindowStartMillis: Long? = null,
    val pickupWindowEndMillis: Long? = null,
    val biddingClosesAtMillis: Long? = null,
    /** The order's own scheduled pickup, which the dispatcher may set apart from the window. */
    val pickupAtMillis: Long? = null,
    /**
     * Origin to destination as the crow flies, not by road: there is no routing provider yet.
     * Null when either end has no position.
     */
    val distanceKm: Int? = null,
) {
    /** A fixed-rate posting is accepted, not negotiated. */
    val isFixedRate: Boolean get() = targetRate != null

    /**
     * Derived the way the server derives the `bidType` it sends, so a range the server moved
     * after a refusal ([bidRange] replaced in place) cannot leave a stale type behind.
     */
    val bidType: BidType
        get() = when {
            targetRate != null -> BidType.FIXED
            bidRange != null -> BidType.RANGE
            else -> BidType.OPEN
        }
}

/** This carrier's offer on a posting, and how it turned out. */
@Serializable
data class DriverBid(
    val id: String,
    val loadPostingId: String,
    val truckId: String,
    val amount: Money,
    val status: DriverBidStatus,
    val note: String? = null,
    val createdAtMillis: Long? = null,
    /**
     * The lowest pending bid another carrier holds on the same posting, amount only. Null when
     * nobody else is bidding, and always on a fixed rate, where every bid is the same price.
     */
    val lowestCompetingBid: Money? = null,
)
