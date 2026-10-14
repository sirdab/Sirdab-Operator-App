package co.sirdab.driver.shared.feature.bidding.api

import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.BidRange
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.Page

/**
 * Postings this carrier may answer, and the bids it has made. A bid is placed once and the
 * dispatcher awards: there are no counters or outbid events.
 */
interface DriverBiddingRepository {
    suspend fun postings(cursor: String? = null, limit: Int = 50): AppResult<Page<DriverPosting>>

    /** The carrier's fleet, since a bid has to commit a specific truck. */
    suspend fun trucks(): AppResult<List<DriverTruck>>

    /**
     * [idempotencyKey] is one per offer the driver means to make, minted by the caller and reused
     * on every retry of that same offer. Minted per call, a bid that landed but whose answer was
     * lost to bad signal would be retried as a second offer and refused as a duplicate.
     *
     * An amount outside the posting's range fails with [AppErrorReason.BID_OUT_OF_RANGE], and with
     * [BidOutOfRange] as the cause when the server said what the range is now.
     */
    suspend fun placeBid(
        postingId: String,
        amountCents: Int,
        truckId: String,
        note: String? = null,
        idempotencyKey: String,
    ): AppResult<DriverBid>

    suspend fun myBids(cursor: String? = null, limit: Int = 50): AppResult<Page<DriverBid>>
}

/**
 * The range a refused bid was held to, as the server has it now. The board may have loaded an
 * older one: the dispatcher can change it while the posting is open, and there is no route to
 * re-read one posting.
 */
class BidOutOfRange(val range: BidRange?) : Exception("bid outside the posting's range")

