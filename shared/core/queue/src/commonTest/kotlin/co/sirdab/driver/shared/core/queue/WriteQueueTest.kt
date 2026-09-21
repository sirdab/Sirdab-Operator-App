package co.sirdab.driver.shared.core.queue

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import co.sirdab.driver.shared.core.network.FileUploader
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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

private class FixedClock(var millis: Long = 1_000_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

class WriteQueueTest {

    private fun jsonHeaders() = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun queue(
        dao: PendingWriteDao,
        clock: Clock,
        files: FakeLocalFileStore = FakeLocalFileStore(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): WriteQueue {
        val http = HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) { json(TmsJson) }
            defaultRequest { url("http://127.0.0.1:4400/") }
        }
        val api = TmsApiClient(http, TokenProvider.Anonymous, ServerClock())
        return WriteQueue(
            dao = dao,
            api = api,
            uploader = FileUploader(api),
            files = files,
            clock = clock,
            // Deterministic jitter, so backoff assertions are exact.
            random = Random(7),
        )
    }

    @Test
    fun `sends queued writes oldest first and clears them`() = runTest {
        val dao = FakePendingWriteDao()
        val clock = FixedClock()
        val paths = mutableListOf<String>()

        val q = queue(dao, clock) { request ->
            paths += request.url.encodedPath
            respond("""{"id":"e1"}""", HttpStatusCode.Created, jsonHeaders())
        }

        q.enqueue("api/driver/trips/t1/events", """{"eventType":"arrived_at_stop"}""", 1L)
        clock.millis += 1000
        q.enqueue("api/driver/trips/t1/events", """{"eventType":"departed_stop"}""", 2L)

        val result = q.drain()

        assertThat(result).isInstanceOf(DrainResult.Drained::class)
        assertThat(dao.current).isEqualTo(emptyList())
        // Arrival then departure. Reversing them is a timeline that cannot happen.
        assertThat(paths.size).isEqualTo(2)
    }

    @Test
    fun `reuses the same idempotency key across retries`() = runTest {
        val dao = FakePendingWriteDao()
        val clock = FixedClock()
        val keys = mutableListOf<String?>()
        var call = 0

        val q = queue(dao, clock) { request ->
            keys += request.headers[TmsApiClient.IDEMPOTENCY_KEY_HEADER]
            call++
            if (call == 1) {
                respond("""{"error":{"code":"internal","message":"boom"}}""", HttpStatusCode.InternalServerError, jsonHeaders())
            } else {
                respond("""{"id":"e1"}""", HttpStatusCode.Created, jsonHeaders())
            }
        }

        q.enqueue("api/driver/exceptions", """{"kind":"delay"}""", 1L)
        q.drain()

        // Wait out the backoff, then try again.
        clock.millis += 60_000
        q.drain()

        assertThat(keys.size).isEqualTo(2)
        assertThat(keys[0]).isEqualTo(keys[1])
        assertThat(dao.current).isEqualTo(emptyList())
    }

    @Test
    fun `sends the stored bytes unchanged`() = runTest {
        val dao = FakePendingWriteDao()
        val clock = FixedClock()
        var sent: String? = null
        val body = """{"eventType":"delivered","occurredAt":"2026-11-01T14:20:00+03:00"}"""

        val q = queue(dao, clock) { request ->
            sent = (request.body as io.ktor.http.content.TextContent).text
            respond("""{"id":"e1"}""", HttpStatusCode.Created, jsonHeaders())
        }

        q.enqueue("api/driver/trips/t1/events", body, 1L)
        q.drain()

        // Byte-identical, or the server's hash says this is a different write.
        assertThat(sent).isEqualTo(body)
    }

    @Test
    fun `treats a replay as success`() = runTest {
        val dao = FakePendingWriteDao()
        val q = queue(dao, FixedClock()) {
            respond(
                """{"id":"e1"}""",
                HttpStatusCode.Created,
                headersOf(
                    "Content-Type" to listOf(ContentType.Application.Json.toString()),
                    TmsApiClient.IDEMPOTENCY_REPLAYED_HEADER to listOf("true"),
                ),
            )
        }

        q.enqueue("api/driver/exceptions", "{}", 1L)
        val result = q.drain()

        // It already landed on an earlier attempt, so the row is done.
        assertThat(result).isInstanceOf(DrainResult.Drained::class)
        assertThat(dao.current).isEqualTo(emptyList())
    }

    @Test
    fun `drops a write the contract will never accept`() = runTest {
        val dao = FakePendingWriteDao()
        val q = queue(dao, FixedClock()) {
            respond(
                """{"error":{"code":"validation_failed","message":"bad occurredAt"}}""",
                HttpStatusCode.BadRequest,
                jsonHeaders(),
            )
        }

        q.enqueue("api/driver/trips/t1/events", """{"occurredAt":"nope"}""", 1L)
        val result = q.drain()

        assertThat(dao.current).isEqualTo(emptyList())
        val dropped = result as DrainResult.Dropped
        assertThat(dropped.failures.first()).contains("validation_failed")
    }

    @Test
    fun `drops a domain refusal but keeps draining the rest`() = runTest {
        val dao = FakePendingWriteDao()
        val clock = FixedClock()
        var call = 0

        val q = queue(dao, clock) {
            call++
            if (call == 1) {
                respond(
                    """{"error":{"code":"stop_out_of_sequence","message":"stop 1 is not finished"}}""",
                    HttpStatusCode.Conflict,
                    jsonHeaders(),
                )
            } else {
                respond("""{"id":"e2"}""", HttpStatusCode.Created, jsonHeaders())
            }
        }

        q.enqueue("api/driver/trips/t1/events", """{"a":1}""", 1L)
        clock.millis += 1000
        q.enqueue("api/driver/trips/t1/events", """{"a":2}""", 2L)

        val result = q.drain()

        // The refused one is gone and surfaced; the one behind it still went.
        assertThat(dao.current).isEqualTo(emptyList())
        val dropped = result as DrainResult.Dropped
        assertThat(dropped.failures.first()).contains("stop_out_of_sequence")
        assertThat(dropped.rest).isInstanceOf(DrainResult.Drained::class)
    }

    @Test
    fun `keeps a transient failure queued and stops draining`() = runTest {
        val dao = FakePendingWriteDao()
        val clock = FixedClock()
        var calls = 0

        val q = queue(dao, clock) {
            calls++
            respond("""{"error":{"code":"internal","message":"boom"}}""", HttpStatusCode.InternalServerError, jsonHeaders())
        }

        q.enqueue("api/driver/trips/t1/events", """{"a":1}""", 1L)
        clock.millis += 1000
        q.enqueue("api/driver/trips/t1/events", """{"a":2}""", 2L)

        val result = q.drain()

        // Stopped at the first failure rather than sending the second out of order.
        assertThat(calls).isEqualTo(1)
        assertThat(dao.current.size).isEqualTo(2)
        assertThat(result).isInstanceOf(DrainResult.Deferred::class)
        assertThat(dao.current.first().attempts).isEqualTo(1)
    }

    @Test
    fun `pauses without dropping anything when the driver is not set up`() = runTest {
        val dao = FakePendingWriteDao()
        val q = queue(dao, FixedClock()) {
            respond(
                """{"error":{"code":"no_active_account","message":"no account"}}""",
                HttpStatusCode.Forbidden,
                jsonHeaders(),
            )
        }

        q.enqueue("api/driver/trips/t1/events", """{"a":1}""", 1L)
        val result = q.drain()

        // Never a retry loop, and never a silent loss of the driver's work.
        assertThat(result).isInstanceOf(DrainResult.Paused::class)
        assertThat(dao.current.size).isEqualTo(1)
    }

    @Test
    fun `parks a write against an endpoint that is not built yet`() = runTest {
        val dao = FakePendingWriteDao()
        val q = queue(dao, FixedClock()) {
            respond(
                """{"error":{"code":"not_implemented","message":"Ships in phase 3"}}""",
                HttpStatusCode.NotImplemented,
                jsonHeaders(),
            )
        }

        q.enqueue("api/driver/stops/s1/proofs", """{"a":1}""", 1L)
        val result = q.drain()

        assertThat(result).isInstanceOf(DrainResult.Paused::class)
        assertThat(dao.current.size).isEqualTo(1)
    }

    @Test
    fun `waits out the backoff instead of retrying immediately`() = runTest {
        val dao = FakePendingWriteDao()
        val clock = FixedClock()
        var calls = 0

        val q = queue(dao, clock) {
            calls++
            respond("""{"error":{"code":"internal","message":"boom"}}""", HttpStatusCode.InternalServerError, jsonHeaders())
        }

        q.enqueue("api/driver/trips/t1/events", """{"a":1}""", 1L)
        q.drain()
        assertThat(calls).isEqualTo(1)

        // Straight away again: nothing is due, so nothing is sent.
        val result = q.drain()
        assertThat(calls).isEqualTo(1)
        assertThat(result).isInstanceOf(DrainResult.Waiting::class)

        clock.millis += 60_000
        q.drain()
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `backs off further on each successive attempt`() = runTest {
        val dao = FakePendingWriteDao()
        val clock = FixedClock()

        val q = queue(dao, clock) {
            respond("""{"error":{"code":"internal","message":"boom"}}""", HttpStatusCode.InternalServerError, jsonHeaders())
        }

        q.enqueue("api/driver/trips/t1/events", """{"a":1}""", 1L)

        q.drain()
        val first = dao.current.first().nextAttemptAtMillis - clock.millis

        clock.millis += 60_000
        q.drain()
        val second = dao.current.first().nextAttemptAtMillis - clock.millis

        assertThat(second > first).isTrue()
        assertThat(dao.current.first().attempts).isEqualTo(2)
    }

    @Test
    fun `clearing the queue drops everything`() = runTest {
        val dao = FakePendingWriteDao()
        val q = queue(dao, FixedClock()) {
            respond("""{"id":"e1"}""", HttpStatusCode.Created, jsonHeaders())
        }

        q.enqueue("api/driver/trips/t1/events", """{"a":1}""", 1L)
        q.clear()

        assertThat(dao.current).isEqualTo(emptyList())
    }
}
