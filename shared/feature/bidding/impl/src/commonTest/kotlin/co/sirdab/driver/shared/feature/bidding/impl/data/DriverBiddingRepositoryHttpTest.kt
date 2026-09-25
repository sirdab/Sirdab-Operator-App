package co.sirdab.driver.shared.feature.bidding.impl.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverBidStatus
import co.sirdab.driver.shared.core.model.Money
import co.sirdab.driver.shared.core.model.PostingStatus
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
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

class DriverBiddingRepositoryHttpTest {

    @Test
    fun `maps a fixed-rate posting`() = runTest {
        val repo = repository {
            respond(
                """{"items":[{"id":"p1","loadId":"l1","status":"open",
                   "pickupWindowStart":"2026-10-01T08:00:00+03:00","pickupWindowEnd":null,
                   "targetRate":{"amountCents":185000,"currency":"SAR"},
                   "biddingClosesAt":"2026-09-30T20:00:00+03:00",
                   "origin":{"label":"Depot","city":"Riyadh"},
                   "destination":{"label":"Store","city":"Jeddah"},
                   "truckType":"chilled","truckSize":"trailer"}],"nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val posting = (repo.postings() as AppResult.Success).data.items.single()

        assertThat(posting.status).isEqualTo(PostingStatus.OPEN)
        assertThat(posting.truckType).isEqualTo(TruckType.CHILLED)
        assertThat(posting.truckSize).isEqualTo(TruckSize.TRAILER)
        assertThat(posting.targetRate).isEqualTo(Money(185000, "SAR"))
        assertThat(posting.origin.city).isEqualTo("Riyadh")
        assertThat(posting.biddingClosesAtMillis).isNotNull()
        // A named price is take-it-or-leave-it, which the sheet renders differently.
        assertThat(posting.isFixedRate).isTrue()
    }

    @Test
    fun `an open-price posting has no target rate`() = runTest {
        val repo = repository {
            respond(
                """{"items":[{"id":"p1","loadId":"l1","status":"open","targetRate":null,
                   "origin":{"label":"Depot"},"destination":{"label":"Store"},
                   "truckType":"dry","truckSize":"flatbed"}],"nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val posting = (repo.postings() as AppResult.Success).data.items.single()

        assertThat(posting.targetRate).isNull()
        assertThat(posting.isFixedRate).isEqualTo(false)
    }

    @Test
    fun `converts riyals to the minor units the contract carries`() = runTest {
        var sent: String? = null
        val repo = repository { request ->
            sent = (request.body as io.ktor.http.content.TextContent).text
            respond(BID_JSON, HttpStatusCode.Created, jsonHeaders())
        }

        repo.placeBid("p1", amountCents = 170000, truckId = "t1", note = "tonight", idempotencyKey = "k1")

        assertThat(sent?.contains("\"amountCents\":170000")).isEqualTo(true)
        assertThat(sent?.contains("\"truckId\":\"t1\"")).isEqualTo(true)
    }

    @Test
    fun `sends an idempotency key so a retry is not a second offer`() = runTest {
        lateinit var seen: HttpRequestData
        val repo = repository { request ->
            seen = request
            respond(BID_JSON, HttpStatusCode.Created, jsonHeaders())
        }

        repo.placeBid("p1", 170000, "t1", idempotencyKey = "offer-1")

        // The caller's key, exactly: it is what makes the caller's retry a replay.
        assertThat(seen.headers[TmsApiClient.IDEMPOTENCY_KEY_HEADER]).isEqualTo("offer-1")
    }

    @Test
    fun `surfaces the server's refusal of a duplicate bid`() = runTest {
        val repo = repository {
            respond(
                """{"error":{"code":"already_bid","message":"you have already bid on this"}}""",
                HttpStatusCode.Conflict,
                jsonHeaders(),
            )
        }

        val result = repo.placeBid("p1", 170000, "t1", idempotencyKey = "k1")

        assertThat(result is AppResult.Failure).isTrue()
        assertThat((result as AppResult.Failure).error.message)
            .isEqualTo("you have already bid on this")
    }

    @Test
    fun `maps my bids with their outcome`() = runTest {
        val repo = repository {
            respond(
                """{"items":[{"id":"b1","loadPostingId":"p1","truckId":"t1","amountCents":170000,
                   "currency":"SAR","status":"won","note":null,
                   "createdAt":"2026-09-19T01:26:32+03:00"}],"nextCursor":"c1"}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val page = (repo.myBids() as AppResult.Success).data

        assertThat(page.items.single().status).isEqualTo(DriverBidStatus.WON)
        assertThat(page.items.single().amount.amountSar).isEqualTo(1700)
        assertThat(page.nextCursor).isEqualTo("c1")
    }

    @Test
    fun `reads an unknown status as pending rather than emptying the board`() = runTest {
        val repo = repository {
            respond(
                """{"items":[{"id":"b1","loadPostingId":"p1","truckId":"t1","amountCents":1,
                   "status":"under_review"}],"nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val bid = (repo.myBids() as AppResult.Success).data.items.single()

        assertThat(bid.status).isEqualTo(DriverBidStatus.PENDING)
    }

    @Test
    fun `reads the carrier fleet from the driver profile`() = runTest {
        val repo = repository {
            respond(
                """{"driverId":"d1","trucks":[
                   {"id":"t1","licencePlate":"RUH 1234","truckType":"dry","truckSize":"closed_lorry"},
                   {"id":"t2","licencePlate":"RUH 2345","truckType":"chilled","truckSize":"trailer"}]}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val trucks = (repo.trucks() as AppResult.Success).data

        assertThat(trucks.size).isEqualTo(2)
        assertThat(trucks[1].truckType).isEqualTo(TruckType.CHILLED)
        assertThat(trucks[0].licencePlate).isEqualTo("RUH 1234")
    }

    private fun jsonHeaders() = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DriverBiddingRepositoryHttp {
        val http = HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) { json(TmsJson) }
            defaultRequest { url("http://127.0.0.1:4400/") }
        }
        return DriverBiddingRepositoryHttp(
            TmsApiClient(http, TokenProvider.Anonymous, ServerClock()),
        )
    }

    private companion object {
        const val BID_JSON = """
        {"id":"b1","loadPostingId":"p1","truckId":"t1","amountCents":170000,
         "currency":"SAR","status":"pending","createdAt":"2026-09-19T01:26:32+03:00"}
        """
    }
}
