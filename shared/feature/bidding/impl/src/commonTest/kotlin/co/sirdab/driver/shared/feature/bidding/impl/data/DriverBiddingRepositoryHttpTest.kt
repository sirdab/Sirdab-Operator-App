package co.sirdab.driver.shared.feature.bidding.impl.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.BidRange
import co.sirdab.driver.shared.core.model.BidType
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
import co.sirdab.driver.shared.core.model.DriverBidStatus
import co.sirdab.driver.shared.core.model.Money
import co.sirdab.driver.shared.core.model.PostingStatus
import co.sirdab.driver.shared.core.network.ServerClock
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.TmsJson
import co.sirdab.driver.shared.core.network.TokenProvider
import co.sirdab.driver.shared.feature.bidding.api.BidOutOfRange
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

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
                   "equipment":{"name":"Reefer trailer","nameAr":null,"bodyType":"box",
                     "sizeClass":"trailer","temperature":"chilled"}}],"nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val posting = (repo.postings() as AppResult.Success).data.items.single()

        assertThat(posting.status).isEqualTo(PostingStatus.OPEN)
        assertThat(posting.bodyType).isEqualTo(EquipmentBody.BOX)
        assertThat(posting.sizeClass).isEqualTo(EquipmentSize.TRAILER)
        assertThat(posting.temperature).isEqualTo(EquipmentTemperature.CHILLED)
        assertThat(posting.targetRate).isEqualTo(Money(185000, "SAR"))
        assertThat(posting.origin.city).isEqualTo("Riyadh")
        assertThat(posting.biddingClosesAtMillis).isNotNull()
        // A named price is take-it-or-leave-it, which the sheet renders differently.
        assertThat(posting.isFixedRate).isTrue()
    }

    @Test
    fun `reads the lane's distance and the order's pickup time`() = runTest {
        val repo = repository {
            respond(
                """{"items":[{"id":"p1","loadId":"l1","status":"open","targetRate":null,
                   "bidRange":{"minCents":90000,"maxCents":null},"bidType":"range",
                   "pickupAt":"2026-10-19T08:00:00+03:00","distanceKm":150,
                   "postedAt":"2026-10-18T09:30:00.000Z",
                   "origin":{"label":"Depot","city":"Dammam"},"destination":{"label":"Store","city":"Al-Ahsa"},
                   "equipment":{"name":"Box","bodyType":"box","sizeClass":"light","temperature":"ambient"}}],
                   "nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val posting = (repo.postings() as AppResult.Success).data.items.single()

        assertThat(posting.distanceKm).isEqualTo(150)
        assertThat(posting.pickupAtMillis).isEqualTo(Instant.parse("2026-10-19T05:00:00Z").toEpochMilliseconds())
        assertThat(posting.bidType).isEqualTo(BidType.RANGE)
    }

    @Test
    fun `an open-price posting has no target rate`() = runTest {
        val repo = repository {
            respond(
                """{"items":[{"id":"p1","loadId":"l1","status":"open","targetRate":null,
                   "origin":{"label":"Depot"},"destination":{"label":"Store"},
                   "equipment":{"name":"Flatbed","nameAr":null,"bodyType":"flatbed",
                     "sizeClass":null,"temperature":null}}],"nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val posting = (repo.postings() as AppResult.Success).data.items.single()

        assertThat(posting.targetRate).isNull()
        assertThat(posting.isFixedRate).isEqualTo(false)
        assertThat(posting.bidType).isEqualTo(BidType.OPEN)
        // A server before 10.36.0 sends neither; the card leaves them out.
        assertThat(posting.distanceKm).isNull()
        assertThat(posting.pickupAtMillis).isNull()
        // An axis the posting never classified stays null rather than a guessed default.
        assertThat(posting.bodyType).isEqualTo(EquipmentBody.FLATBED)
        assertThat(posting.sizeClass).isNull()
        assertThat(posting.temperature).isNull()
    }

    @Test
    fun `reads an open posting's bid range`() = runTest {
        val repo = repository {
            respond(
                """{"items":[${openPosting("p1", """{"minCents":100000,"maxCents":200000}""")},
                   ${openPosting("p2", """{"minCents":null,"maxCents":200000}""")},
                   ${openPosting("p3", "null")}],"nextCursor":null}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val postings = (repo.postings() as AppResult.Success).data.items

        assertThat(postings[0].bidRange).isEqualTo(BidRange(100000, 200000))
        // Either bound may stand alone.
        assertThat(postings[1].bidRange).isEqualTo(BidRange(null, 200000))
        assertThat(postings[2].bidRange).isNull()
    }

    @Test
    fun `a bound set to the halala reads as the whole riyals that fit inside it`() {
        val range = BidRange(minCents = 100050, maxCents = 199950)

        // 1,000 SAR is below 1,000.50 and 2,000 above 1,999.50; the driver can only type riyals.
        assertThat(range.minSar).isEqualTo(1001)
        assertThat(range.maxSar).isEqualTo(1999)
        assertThat(100000L in range).isFalse()
        assertThat(100100L in range).isTrue()
    }

    @Test
    fun `a bid outside the range is named and carries the range the server holds`() = runTest {
        val repo = repository {
            respond(
                """{"error":{"code":"bid_out_of_range","message":"this bid is outside the posting's range",
                   "details":{"minCents":120000,"maxCents":null}}}""",
                HttpStatusCode.Conflict,
                jsonHeaders(),
            )
        }

        val error = (repo.placeBid("p1", 100000, "t1", idempotencyKey = "k1") as AppResult.Failure).error

        assertThat(error.reason).isEqualTo(AppErrorReason.BID_OUT_OF_RANGE)
        assertThat(error.cause).isNotNull().isInstanceOf(BidOutOfRange::class)
        assertThat((error.cause as BidOutOfRange).range).isEqualTo(BidRange(120000, null))
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
    fun `an edit patches the bid's price under the caller's key`() = runTest {
        lateinit var seen: HttpRequestData
        var sent: String? = null
        val repo = repository { request ->
            seen = request
            sent = (request.body as io.ktor.http.content.TextContent).text
            respond(
                """{"id":"b1","loadPostingId":"p1","truckId":"t1","amountCents":150000,"currency":"SAR",
                   "status":"pending","lowestCompetingBid":{"amountCents":140000,"currency":"SAR"}}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val bid = (repo.updateBid("b1", amountCents = 150000, idempotencyKey = "edit-1") as AppResult.Success).data

        assertThat(seen.method).isEqualTo(HttpMethod.Patch)
        assertThat(seen.url.encodedPath).isEqualTo("/api/driver/bids/b1")
        assertThat(seen.headers[TmsApiClient.IDEMPOTENCY_KEY_HEADER]).isEqualTo("edit-1")
        assertThat(sent).isEqualTo("""{"amountCents":150000}""")
        assertThat(bid.amount).isEqualTo(Money(150000))
        assertThat(bid.lowestCompetingBid).isEqualTo(Money(140000))
    }

    @Test
    fun `an edit outside the range carries the range the server holds`() = runTest {
        val repo = repository {
            respond(
                """{"error":{"code":"bid_out_of_range","message":"outside the range",
                   "details":{"minCents":null,"maxCents":120000}}}""",
                HttpStatusCode.Conflict,
                jsonHeaders(),
            )
        }

        val error = (repo.updateBid("b1", 200000, idempotencyKey = "k1") as AppResult.Failure).error

        assertThat((error.cause as BidOutOfRange).range).isEqualTo(BidRange(null, 120000))
    }

    @Test
    fun `names the refusals a bid or an edit can meet`() = runTest {
        val named = mapOf(
            "bid_not_pending" to AppErrorReason.BID_DECIDED,
            "posting_not_open" to AppErrorReason.BIDDING_CLOSED,
            "bidding_closed" to AppErrorReason.BIDDING_CLOSED,
            "posting_not_visible" to AppErrorReason.BIDDING_CLOSED,
            "not_found" to AppErrorReason.BIDDING_CLOSED,
            "driver_not_verified" to AppErrorReason.DRIVER_NOT_VERIFIED,
            "rate_is_fixed" to AppErrorReason.RATE_IS_FIXED,
            "bidder_not_independent" to AppErrorReason.NOT_A_BIDDER,
        )
        named.forEach { (code, reason) ->
            val repo = repository {
                respond(
                    """{"error":{"code":"$code","message":"refused"}}""",
                    if (code == "not_found") HttpStatusCode.NotFound else HttpStatusCode.Conflict,
                    jsonHeaders(),
                )
            }

            val edit = (repo.updateBid("b1", 150000, idempotencyKey = "k1") as AppResult.Failure).error
            val bid = (repo.placeBid("p1", 150000, "t1", idempotencyKey = "k2") as AppResult.Failure).error

            assertThat(edit.reason).isEqualTo(reason)
            assertThat(bid.reason).isEqualTo(reason)
            // The server's sentence stays behind as the fallback.
            assertThat(edit.message).isEqualTo("refused")
        }
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
                   {"id":"t1","licencePlate":"RUH 1234","equipment":{"id":"e1","name":"Closed lorry",
                     "nameAr":null,"code":null,"bodyType":"box","sizeClass":"medium","temperature":"ambient"}},
                   {"id":"t2","licencePlate":"RUH 2345","equipment":{"id":"e2","name":"Reefer trailer",
                     "nameAr":null,"code":"RT","bodyType":"box","sizeClass":"trailer","temperature":"chilled"}}]}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val trucks = (repo.trucks() as AppResult.Success).data

        assertThat(trucks.size).isEqualTo(2)
        assertThat(trucks[1].temperature).isEqualTo(EquipmentTemperature.CHILLED)
        assertThat(trucks[1].sizeClass).isEqualTo(EquipmentSize.TRAILER)
        assertThat(trucks[0].licencePlate).isEqualTo("RUH 1234")
    }

    @Test
    fun `a company driver's refusal is named rather than relayed`() = runTest {
        val repo = repository {
            respond(
                """{"error":{"code":"bidder_not_independent","message":"only independent operators bid from the app"}}""",
                HttpStatusCode.Forbidden,
                jsonHeaders(),
            )
        }

        val failure = repo.postings() as AppResult.Failure

        assertThat(failure.error.reason).isEqualTo(AppErrorReason.NOT_A_BIDDER)
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

    private fun openPosting(id: String, bidRange: String) =
        """{"id":"$id","loadId":"l1","status":"open","targetRate":null,"bidRange":$bidRange,
           "origin":{"label":"Depot"},"destination":{"label":"Store"},
           "equipment":{"name":"Flatbed","bodyType":"flatbed","sizeClass":null,"temperature":null}}"""

    private companion object {
        const val BID_JSON = """
        {"id":"b1","loadPostingId":"p1","truckId":"t1","amountCents":170000,
         "currency":"SAR","status":"pending","createdAt":"2026-09-19T01:26:32+03:00"}
        """
    }
}
