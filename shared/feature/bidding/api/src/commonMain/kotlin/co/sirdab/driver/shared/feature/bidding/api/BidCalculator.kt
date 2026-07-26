package co.sirdab.driver.shared.feature.bidding.api

import co.sirdab.driver.shared.core.model.BidBreakdown
import kotlin.math.roundToInt

/**
 * Pure profit math for the bid composer. No dependencies so it's trivially unit-testable and
 * identical on both platforms.
 */
object BidCalculator {
    const val FUEL_SAR_PER_KM = 0.55
    const val TOLL_SAR_PER_KM = 0.05
    const val COMMISSION_RATE = 0.10

    fun breakdown(rateSar: Int, distanceKm: Int, suggestedRateSar: Int): BidBreakdown {
        val fuel = (distanceKm * FUEL_SAR_PER_KM).roundToInt()
        val tolls = (distanceKm * TOLL_SAR_PER_KM).roundToInt()
        val commission = (rateSar * COMMISSION_RATE).roundToInt()
        val net = rateSar - fuel - tolls - commission
        return BidBreakdown(
            rateSar = rateSar,
            fuelCostSar = fuel,
            tollsSar = tolls,
            commissionSar = commission,
            netProfitSar = net,
            winProbability = winProbability(rateSar, suggestedRateSar),
        )
    }

    /** Lower bids win more often; near or above the suggested rate the odds fall off. */
    fun winProbability(rateSar: Int, suggestedRateSar: Int): Float {
        if (suggestedRateSar <= 0) return 0.5f
        val ratio = rateSar.toDouble() / suggestedRateSar
        return when {
            ratio <= 0.92 -> 0.95f
            ratio <= 1.00 -> 0.80f
            ratio <= 1.05 -> 0.68f
            ratio <= 1.15 -> 0.45f
            ratio <= 1.25 -> 0.28f
            else -> 0.15f
        }
    }
}
