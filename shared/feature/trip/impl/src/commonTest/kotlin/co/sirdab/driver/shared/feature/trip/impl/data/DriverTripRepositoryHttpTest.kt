package co.sirdab.driver.shared.feature.trip.impl.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverTrip
import co.sirdab.driver.shared.core.model.StopStatus
import co.sirdab.driver.shared.core.model.StopType
import co.sirdab.driver.shared.core.model.TripLifecycle
import co.sirdab.driver.shared.core.network.ServerClock
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.TmsJson
import co.sirdab.driver.shared.core.network.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DriverTripRepositoryHttpTest {

    @Test
    fun `maps a page of trips and keeps the cursor opaque`() = runTest {
        lateinit var seen: HttpRequestData
        val repo = repository { request ->
            seen = request
            respond(
                """{"items":[{"id":"t1","reference":"TRP-000001","status":"in_transit",
                   "scheduledAt":"2026-10-01T08:00:00+03:00","inCity":false,"stopCount":2}],
                   "nextCursor":"MjAyNi0x"}""".trimIndent(),
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val result = repo.trips(cursor = "abc123", limit = 25)
        val page = (result as AppResult.Success).data

        assertThat(page.items).isEqualTo(
            listOf(
                DriverTrip(
                    id = "t1",
                    reference = "TRP-000001",
                    status = TripLifecycle.IN_TRANSIT,
                    scheduledAtMillis = 1790830800000L,
                    inCity = false,
                    stopCount = 2,
                ),
            ),
        )
        assertThat(page.nextCursor).isEqualTo("MjAyNi0x")
        // Sent back byte for byte; a reconstructed cursor earns a 400 invalid_cursor.
        assertThat(seen.url.parameters["cursor"]).isEqualTo("abc123")
        assertThat(seen.url.parameters["limit"]).isEqualTo("25")
    }

    @Test
    fun `omits the cursor on the first page`() = runTest {
        lateinit var seen: HttpRequestData
        val repo = repository { request ->
            seen = request
            respond("""{"items":[],"nextCursor":null}""", HttpStatusCode.OK, jsonHeaders())
        }

        repo.trips()

        assertThat(seen.url.parameters["cursor"]).isNull()
    }

    @Test
    fun `clamps the limit to what the contract accepts`() = runTest {
        lateinit var seen: HttpRequestData
        val repo = repository { request ->
            seen = request
            respond("""{"items":[],"nextCursor":null}""", HttpStatusCode.OK, jsonHeaders())
        }

        repo.trips(limit = 5000)

        assertThat(seen.url.parameters["limit"]).isEqualTo("100")
    }

    @Test
    fun `maps a trip detail into stops in running order`() = runTest {
        val repo = repository {
            respond(DETAIL_JSON, HttpStatusCode.OK, jsonHeaders())
        }

        val trip = (repo.trip("t1") as AppResult.Success).data

        assertThat(trip.stops.map { it.sequenceNumber }).isEqualTo(listOf(1, 2))
        assertThat(trip.stops[0].stopType).isEqualTo(StopType.PICKUP)
        assertThat(trip.stops[0].status).isEqualTo(StopStatus.COMPLETED)
        assertThat(trip.stops[1].stopType).isEqualTo(StopType.DROPOFF)
        assertThat(trip.legs).isNotNull()
        assertThat(trip.legs.size).isEqualTo(1)
    }

    @Test
    fun `composes the address lines the server no longer flattens`() = runTest {
        val repo = repository { respond(DETAIL_JSON, HttpStatusCode.OK, jsonHeaders()) }

        val stop = (repo.trip("t1") as AppResult.Success).data.stops.first()

        assertThat(stop.address.addressLine).isEqualTo("7421 King Fahd Road")
        assertThat(stop.address.districtLine).isEqualTo("Al Olaya, 12345")
        assertThat(stop.address.city).isEqualTo("Riyadh")
    }

    @Test
    fun `falls back to the label when there is no street`() = runTest {
        val repo = repository { respond(DETAIL_JSON, HttpStatusCode.OK, jsonHeaders()) }

        val dropoff = (repo.trip("t1") as AppResult.Success).data.stops[1]

        // An address entered in a hurry may be a label and a city, and the
        // driver still has to be told somewhere to go.
        assertThat(dropoff.address.addressLine).isEqualTo("Store")
        assertThat(dropoff.address.districtLine).isNull()
    }

    @Test
    fun `reads how many photos are already attached to a stop`() = runTest {
        val repo = repository { respond(DETAIL_JSON, HttpStatusCode.OK, jsonHeaders()) }

        val stops = (repo.trip("t1") as AppResult.Success).data.stops

        assertThat(stops[0].photoProofCount).isEqualTo(2)
        assertThat(stops[1].photoProofCount).isEqualTo(0)
    }

    @Test
    fun `sorts stops even when the server sends them out of order`() = runTest {
        val repo = repository { respond(OUT_OF_ORDER_JSON, HttpStatusCode.OK, jsonHeaders()) }

        val trip = (repo.trip("t1") as AppResult.Success).data

        assertThat(trip.stops.map { it.sequenceNumber }).isEqualTo(listOf(1, 2))
    }

    @Test
    fun `points at the first stop that is not finished with`() = runTest {
        val repo = repository { respond(DETAIL_JSON, HttpStatusCode.OK, jsonHeaders()) }

        val trip = (repo.trip("t1") as AppResult.Success).data

        // Stop 1 is completed, so the driver is working stop 2.
        assertThat(trip.currentStop?.sequenceNumber).isEqualTo(2)
    }

    @Test
    fun `parses an offset timestamp into epoch millis`() = runTest {
        val repo = repository { respond(DETAIL_JSON, HttpStatusCode.OK, jsonHeaders()) }

        val trip = (repo.trip("t1") as AppResult.Success).data

        // 2026-10-01T08:00:00+03:00 is 05:00:00Z.
        assertThat(trip.scheduledAtMillis).isEqualTo(1790830800000L)
    }

    @Test
    fun `keeps a trip whose timestamp is unreadable`() = runTest {
        val repo = repository {
            respond(
                """{"items":[{"id":"t1","reference":"R","status":"assigned",
                   "scheduledAt":"not-a-date","inCity":true,"stopCount":0}],"nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val page = (repo.trips() as AppResult.Success).data

        assertThat(page.items.size).isEqualTo(1)
        assertThat(page.items[0].scheduledAtMillis).isNull()
    }

    @Test
    fun `reads an unknown status as created rather than failing the screen`() = runTest {
        val repo = repository {
            respond(
                """{"items":[{"id":"t1","reference":"R","status":"teleporting",
                   "inCity":false,"stopCount":0}],"nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val page = (repo.trips() as AppResult.Success).data

        assertThat(page.items[0].status).isEqualTo(TripLifecycle.CREATED)
    }

    @Test
    fun `surfaces the server's message when a trip is gone`() = runTest {
        val repo = repository {
            respond(
                """{"error":{"code":"not_found","message":"That resource does not exist."}}""",
                HttpStatusCode.NotFound,
                jsonHeaders(),
            )
        }

        val result = repo.trip("missing")

        assertThat(result is AppResult.Failure).isTrue()
        assertThat((result as AppResult.Failure).error.message)
            .isEqualTo("That resource does not exist.")
    }

    @Test
    fun `never sends an idempotency key on a read`() = runTest {
        lateinit var seen: HttpRequestData
        val repo = repository { request ->
            seen = request
            respond("""{"items":[],"nextCursor":null}""", HttpStatusCode.OK, jsonHeaders())
        }

        repo.trips()

        // The API refuses the header on a route that cannot replay.
        assertThat(seen.headers[TmsApiClient.IDEMPOTENCY_KEY_HEADER]).isNull()
    }

    private fun jsonHeaders() = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DriverTripRepositoryHttp {
        val http = HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) { json(TmsJson) }
            defaultRequest { url("http://127.0.0.1:4400/") }
        }
        return DriverTripRepositoryHttp(
            TmsApiClient(http, TokenProvider.Anonymous, ServerClock()),
        )
    }

    private companion object {
        const val DETAIL_JSON = """
        {
          "id": "t1", "reference": "TRP-000001", "status": "in_transit",
          "scheduledAt": "2026-10-01T08:00:00+03:00", "inCity": false, "stopCount": 2,
          "legs": [{"id":"l1","sequence":1,"originAddressId":"a1","destinationAddressId":"a2"}],
          "stops": [
            {"id":"s1","legId":"l1","stopType":"pickup","sequenceNumber":1,"status":"completed",
             "plannedAt":"2026-10-01T08:00:00+03:00","arrivedAt":null,"departedAt":null,
             "address":{"id":"a1","label":"Depot","buildingNumber":"7421",
                        "street":"King Fahd Road","district":"Al Olaya","zipCode":"12345",
                        "city":"Riyadh","lat":24.7,"lng":46.6},
             "photoProofCount":2,
             "contact":{"name":"Desk","phone":"+966500000999"}},
            {"id":"s2","legId":"l1","stopType":"dropoff","sequenceNumber":2,"status":"pending",
             "plannedAt":null,"arrivedAt":null,"departedAt":null,
             "address":{"id":"a2","label":"Store","city":"Jeddah"},
             "contact":null}
          ]
        }
        """

        const val OUT_OF_ORDER_JSON = """
        {
          "id": "t1", "reference": "R", "status": "assigned", "inCity": true, "stopCount": 2,
          "legs": [],
          "stops": [
            {"id":"s2","legId":"l1","stopType":"dropoff","sequenceNumber":2,"status":"pending",
             "address":{"id":"a2","label":"Store"}},
            {"id":"s1","legId":"l1","stopType":"pickup","sequenceNumber":1,"status":"pending",
             "address":{"id":"a1","label":"Depot"}}
          ]
        }
        """
    }
}
