package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

/** A single saved lane the driver auto-bids on (plan screen 18). */
@Serializable
data class AutoBidRule(
    val enabled: Boolean = false,
    val originCityId: String,
    val destinationCityId: String,
    val vehicle: VehicleType,
    val maxRateSar: Int,
)
