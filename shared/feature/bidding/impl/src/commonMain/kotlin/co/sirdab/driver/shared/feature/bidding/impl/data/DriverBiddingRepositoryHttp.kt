package co.sirdab.driver.shared.feature.bidding.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverBid
import co.sirdab.driver.shared.core.model.DriverPosting
import co.sirdab.driver.shared.core.model.DriverTruck
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.network.ApiSuccess
import co.sirdab.driver.shared.core.network.Paginated
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.feature.bidding.api.DriverBiddingRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `GET /driver/postings`, `POST /driver/postings/:id/bids` and `GET /driver/bids`.
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

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun placeBid(
        postingId: String,
        amountCents: Int,
        truckId: String,
        note: String?,
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
            idempotencyKey = Uuid.random().toString(),
        ).toAppResult { it.toDomain() }
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

    private fun <T, R> Result<ApiSuccess<T>>.toAppResult(transform: (T) -> R): AppResult<R> = fold(
        onSuccess = { AppResult.Success(transform(it.value)) },
        onFailure = { error ->
            AppResult.Failure(
                error.apiFailure?.toAppError() ?: AppError(error.message ?: "Something went wrong."),
            )
        },
    )

    private companion object {
        const val POSTINGS = "api/driver/postings"
        const val BIDS = "api/driver/bids"
        const val MAX_LIMIT = 100
    }
}
