package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

/**
 * What one completed trip paid.
 *
 * Deliberately not a wallet ledger: an operator is paid as soon as proof of
 * delivery lands, so there is no balance to hold, nothing pending, and no payout
 * to track. This is a record of work done, which is all the history screen ever
 * showed.
 */
@Serializable
data class TripEarning(
    val id: String,
    val amountSar: Int,
    val descriptionEn: String,
    val descriptionAr: String,
    val earnedAtMillis: Long,
)
