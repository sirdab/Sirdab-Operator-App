package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BidStatus { PENDING, COUNTERED, WON, LOST, WITHDRAWN }

@Serializable
enum class CounterAction { ACCEPT, REJECT, COUNTER }

@Serializable
data class Bid(
    val id: String,
    val loadId: String,
    val amountSar: Int,
    val note: String? = null,
    val status: BidStatus = BidStatus.PENDING,
    val counterAmountSar: Int? = null,
    val placedAtMillis: Long,
    val updatedAtMillis: Long,
)

/** Live events pushed by the simulation while a bid is open. */
@Serializable
sealed interface BidEvent {
    val bidId: String

    @Serializable
    data class Outbid(override val bidId: String, val leadingAmountSar: Int) : BidEvent

    @Serializable
    data class Countered(override val bidId: String, val counterAmountSar: Int) : BidEvent

    @Serializable
    data class Awarded(override val bidId: String) : BidEvent

    @Serializable
    data class Rejected(override val bidId: String) : BidEvent
}

/** Live snapshot of the profit breakdown shown on the bid composer. */
data class BidBreakdown(
    val rateSar: Int,
    val fuelCostSar: Int,
    val tollsSar: Int,
    val commissionSar: Int,
    val netProfitSar: Int,
    val winProbability: Float,
)
