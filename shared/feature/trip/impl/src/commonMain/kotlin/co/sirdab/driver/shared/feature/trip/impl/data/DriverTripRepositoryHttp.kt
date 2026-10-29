package co.sirdab.driver.shared.feature.trip.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.Page
import co.sirdab.driver.shared.core.network.ApiFailure
import co.sirdab.driver.shared.core.network.Paginated
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.core.network.toAppResult
import co.sirdab.driver.shared.feature.trip.api.DownloadedDocument
import co.sirdab.driver.shared.feature.trip.api.DriverTripRepository
import kotlinx.serialization.builtins.serializer

/**
 * `GET /api/driver/trips`, `GET /api/driver/trips/:id` and, for a leg's order documents,
 * `GET /api/driver/legs/:id/documents/:key` (contracts 10.52.0).
 *
 * All reads, so none carries an idempotency key: the API refuses one on a route that cannot
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
        ).toAppResult("Could not load trips.") { page ->
            Page(items = page.items.map { it.toDomain() }, nextCursor = page.nextCursor)
        }
    }

    override suspend fun trip(id: String): AppResult<DriverTrip> =
        api.get("$TRIPS/$id", DriverTripDetailDto.serializer())
            .toAppResult("Could not load trips.") { it.toDomain() }

    override suspend fun legDocument(legId: String, key: String): AppResult<DownloadedDocument> =
        api.getFile("$LEGS/$legId/documents/$key").fold(
            onSuccess = { (file) ->
                // The server always names it; the fallback is for one that one day does not.
                AppResult.Success(DownloadedDocument(file.bytes, file.fileName ?: "$key.pdf"))
            },
            onFailure = { AppResult.Failure(it.toDocumentError()) },
        )

    /**
     * The two refusals a document can meet, named so the screen can say them in the driver's
     * language. A 404 is the order no longer listing it or the leg no longer being this driver's;
     * a 502 `document_unavailable` is the system that sent the order failing to hand it over.
     */
    private fun Throwable.toDocumentError(): AppError {
        val error = toAppError("Could not download the document.")
        val failure = apiFailure as? ApiFailure.Http ?: return error
        val reason = when {
            failure.rawCode == "document_unavailable" || failure.status == 502 -> AppErrorReason.DOCUMENT_UNAVAILABLE
            failure.status == 404 -> AppErrorReason.DOCUMENT_GONE
            else -> error.reason
        }
        return error.copy(reason = reason)
    }

    private companion object {
        const val TRIPS = "api/driver/trips"
        const val LEGS = "api/driver/legs"
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
    }
}
