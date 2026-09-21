package co.sirdab.driver.shared.feature.trip.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.network.Paginated
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import kotlinx.serialization.builtins.serializer

/**
 * `GET /api/driver/trips` and `GET /api/driver/trips/:id`.
 *
 * Both are reads, so neither carries an idempotency key: the API refuses one on a route that cannot
 * replay, and would answer 400 `idempotency_unsupported`.
 */
class DriverTripRepositoryHttp(private val api: TmsApiClient) : DriverTripRepository {

    override suspend fun trips(cursor: String?, limit: Int): AppResult<Page<DriverTrip>> {
        val query = buildMap {
            put("limit", limit.coerceIn(MIN_LIMIT, MAX_LIMIT).toString())
            // The cursor is opaque and endpoint-specific: sent back exactly as it arrived, or
            // omitted. Anything else earns a 400 invalid_cursor.
            cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
        }

        return api.get(
            path = TRIPS,
            deserializer = Paginated.serializer(DriverTripSummaryDto.serializer()),
            query = query,
        ).toAppResult { page ->
            Page(items = page.items.map { it.toDomain() }, nextCursor = page.nextCursor)
        }
    }

    override suspend fun trip(id: String): AppResult<DriverTrip> =
        api.get("$TRIPS/$id", DriverTripDetailDto.serializer()).toAppResult { it.toDomain() }

    private fun <T, R> Result<co.sirdab.driver.shared.core.network.ApiSuccess<T>>.toAppResult(
        transform: (T) -> R,
    ): AppResult<R> = fold(
        onSuccess = { AppResult.Success(transform(it.value)) },
        onFailure = { error ->
            AppResult.Failure(
                error.apiFailure?.toAppError() ?: AppError(error.message ?: "Could not load trips."),
            )
        },
    )

    private companion object {
        const val TRIPS = "api/driver/trips"
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
    }
}
