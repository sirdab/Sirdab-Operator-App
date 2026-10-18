package co.sirdab.driver.shared.feature.bidding.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.network.ApiFailure
import co.sirdab.driver.shared.core.network.Paginated
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.TmsJson
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.core.network.toAppResult
import co.sirdab.driver.shared.feature.bidding.api.BidOutOfRange
import co.sirdab.driver.shared.feature.bidding.api.DriverBiddingRepository

/**
 * `GET /driver/postings`, `POST /driver/postings/:id/bids`, `PATCH /driver/bids/:id` and
 * `GET /driver/bids`.
 *
 * A bid is sent directly rather than queued. Unlike a trip event, it is only
 * worth anything before the posting closes, so a bid that syncs tomorrow is a
 * bid on something already awarded. The driver is told it failed instead.
 */
class DriverBiddingRepositoryHttp(
    private val api: TmsApiClient,
) : DriverBiddingRepository {

    override suspend fun postings(cursor: String?, limit: Int): AppResult<Page<DriverPosting>> {
        val query = buildMap {
            put("limit", limit.coerceIn(1, MAX_LIMIT).toString())
            cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
        }
        return api.get(
            path = POSTINGS,
            deserializer = Paginated.serializer(DriverPostingDto.serializer()),
            query = query,
        ).toAppResult { page ->
            Page(page.items.map { it.toDomain() }, page.nextCursor)
        }
    }

    override suspend fun trucks(): AppResult<List<DriverTruck>> =
        api.get("api/driver/me", MeTrucksDto.serializer())
            .toAppResult { me -> me.trucks.map { it.toDomain() } }

    override suspend fun placeBid(
        postingId: String,
        amountCents: Int,
        truckId: String,
        note: String?,
        idempotencyKey: String,
    ): AppResult<DriverBid> {
        val body = api.encode(
            BidInputDto.serializer(),
            BidInputDto(
                amountCents = amountCents,
                truckId = truckId,
                note = note?.takeIf { it.isNotBlank() },
            ),
        )
        return api.post(
            path = "$POSTINGS/$postingId/bids",
            body = body,
            deserializer = BidDto.serializer(),
            // A retry on bad signal must not become a second offer; the server
            // also holds one-bid-per-carrier, so this is belt and braces.
            idempotencyKey = idempotencyKey,
        ).fold(
            onSuccess = { AppResult.Success(it.value.toDomain()) },
            onFailure = { AppResult.Failure(it.toBiddingError()) },
        )
    }

    override suspend fun updateBid(
        bidId: String,
        amountCents: Int,
        idempotencyKey: String,
    ): AppResult<DriverBid> {
        val body = api.encode(UpdateBidInputDto.serializer(), UpdateBidInputDto(amountCents))
        return api.patch(
            path = "$BIDS/$bidId",
            body = body,
            deserializer = BidDto.serializer(),
            // The server tells the dispatcher about every edit; a replayed one must not tell twice.
            idempotencyKey = idempotencyKey,
        ).fold(
            onSuccess = { AppResult.Success(it.value.toDomain()) },
            onFailure = { AppResult.Failure(it.toBiddingError()) },
        )
    }

    /**
     * The refusals a bid or an edit can meet, named so the sheet can say them in the driver's
     * language; anything else keeps the server's message. A bid outside the range carries the range
     * in `details`, so the sheet can show the real one.
     */
    private fun Throwable.toBiddingError(): AppError {
        val error = toAppError()
        val failure = apiFailure as? ApiFailure.Http ?: return error
        val reason = when (failure.rawCode) {
            "bid_not_pending" -> AppErrorReason.BID_DECIDED
            // Gone from this carrier's board, one way or another: awarded or withdrawn, past its
            // deadline, no longer offered to it, or (a 404 on a posting or bid) never its to see.
            "posting_not_open", "bidding_closed", "posting_not_visible", "not_found" -> AppErrorReason.BIDDING_CLOSED
            "driver_not_verified" -> AppErrorReason.DRIVER_NOT_VERIFIED
            "rate_is_fixed" -> AppErrorReason.RATE_IS_FIXED
            else -> error.reason
        }
        if (reason != AppErrorReason.BID_OUT_OF_RANGE) return error.copy(reason = reason)
        val details = failure.details ?: return error
        val range = runCatching { TmsJson.decodeFromJsonElement(BidRangeDto.serializer(), details) }
            .getOrNull() ?: return error
        return error.copy(cause = BidOutOfRange(range.toDomain()))
    }

    override suspend fun myBids(cursor: String?, limit: Int): AppResult<Page<DriverBid>> {
        val query = buildMap {
            put("limit", limit.coerceIn(1, MAX_LIMIT).toString())
            cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
        }
        return api.get(
            path = BIDS,
            deserializer = Paginated.serializer(BidDto.serializer()),
            query = query,
        ).toAppResult { page ->
            Page(page.items.map { it.toDomain() }, page.nextCursor)
        }
    }

    private companion object {
        const val POSTINGS = "api/driver/postings"
        const val BIDS = "api/driver/bids"
        const val MAX_LIMIT = 100
    }
}
