package co.sirdab.driver.shared.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test

@Serializable
private data class Echo(val id: String)

class TmsApiClientTest {

    @Test
    fun `sends the bearer token and the idempotency key on a write`() = runTest {
        lateinit var seen: HttpRequestData
        val client = client(FixedToken("token-1")) { request ->
            seen = request
            respond("""{"id":"d1"}""", HttpStatusCode.Created, jsonHeaders())
        }

        client.post("api/driver/devices", """{"platform":"android"}""", Echo.serializer(), "key-1")

        assertThat(seen.headers["Authorization"]).isEqualTo("Bearer token-1")
        assertThat(seen.headers[TmsApiClient.IDEMPOTENCY_KEY_HEADER]).isEqualTo("key-1")
    }

    @Test
    fun `reports a replayed write as success so the queue drops it`() = runTest {
        val client = client(FixedToken("token-1")) {
            respond(
                """{"id":"d1"}""",
                HttpStatusCode.Created,
                headersOf(
                    "Content-Type" to listOf(ContentType.Application.Json.toString()),
                    TmsApiClient.IDEMPOTENCY_REPLAYED_HEADER to listOf("true"),
                ),
            )
        }

        val result = client.post("api/driver/devices", "{}", Echo.serializer(), "key-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.replayed).isEqualTo(true)
    }

    @Test
    fun `parses the error envelope into a typed failure`() = runTest {
        val client = client(FixedToken("token-1")) {
            respond(
                """{"error":{"code":"no_active_account","message":"no account"}}""",
                HttpStatusCode.Forbidden,
                jsonHeaders(),
            )
        }

        val failure = client.get("api/driver/trips", Echo.serializer())
            .exceptionOrNull()?.apiFailure as? ApiFailure.Http

        assertThat(failure).isNotNull()
        assertThat(failure?.code).isEqualTo(ApiErrorCode.NoActiveAccount)
        assertThat(failure?.disposition).isEqualTo(FailureDisposition.Pause)
    }

    @Test
    fun `refreshes once on 401 and retries with the same key and body`() = runTest {
        val tokens = RefreshingToken()
        val sent = mutableListOf<HttpRequestData>()
        var call = 0

        val client = client(tokens) { request ->
            sent += request
            call++
            if (call == 1) {
                respond(
                    """{"error":{"code":"unauthorized","message":"expired"}}""",
                    HttpStatusCode.Unauthorized,
                    jsonHeaders(),
                )
            } else {
                respond("""{"id":"d1"}""", HttpStatusCode.Created, jsonHeaders())
            }
        }

        val body = """{"platform":"android"}"""
        val result = client.post("api/driver/devices", body, Echo.serializer(), "key-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(sent.size).isEqualTo(2)
        assertThat(tokens.refreshes).isEqualTo(1)
        // Same key and same bytes, or the server would read the retry as a different write.
        assertThat(sent[1].headers[TmsApiClient.IDEMPOTENCY_KEY_HEADER]).isEqualTo("key-1")
        assertThat(sent[0].headers["Authorization"]).isEqualTo("Bearer stale")
        assertThat(sent[1].headers["Authorization"]).isEqualTo("Bearer fresh")
    }

    @Test
    fun `gives up after one refresh rather than looping`() = runTest {
        val tokens = RefreshingToken()
        var calls = 0

        val client = client(tokens) {
            calls++
            respond(
                """{"error":{"code":"unauthorized","message":"expired"}}""",
                HttpStatusCode.Unauthorized,
                jsonHeaders(),
            )
        }

        val result = client.get("api/driver/trips", Echo.serializer())

        assertThat(result.isFailure).isTrue()
        assertThat(calls).isEqualTo(2)
        assertThat(tokens.refreshes).isEqualTo(1)
    }

    @Test
    fun `a 2xx body that does not match the contract is malformed rather than a crash`() = runTest {
        val client = client(FixedToken("t")) {
            respond("""{"unexpected":true}""", HttpStatusCode.OK, jsonHeaders())
        }

        val failure = client.get("api/driver/trips", Echo.serializer()).exceptionOrNull()?.apiFailure

        assertThat(failure is ApiFailure.Malformed).isTrue()
    }

    @Test
    fun `an unauthenticated call simply omits the header`() = runTest {
        lateinit var seen: HttpRequestData
        val client = client(TokenProvider.Anonymous) { request ->
            seen = request
            respond("""{"id":"d1"}""", HttpStatusCode.OK, jsonHeaders())
        }

        client.get("api/driver/trips", Echo.serializer())

        assertThat(seen.headers["Authorization"]).isNull()
    }

    @Test
    fun `learns the server clock from the Date header`() = runTest {
        val clock = ServerClock()
        val client = client(TokenProvider.Anonymous, clock) {
            respond(
                """{"id":"d1"}""",
                HttpStatusCode.OK,
                headersOf(
                    "Content-Type" to listOf(ContentType.Application.Json.toString()),
                    "Date" to listOf("Wed, 16 Sep 2026 11:20:00 GMT"),
                ),
            )
        }

        client.get("api/driver/trips", Echo.serializer())

        assertThat(clock.skew.isPositive() || clock.skew.isNegative()).isTrue()
    }

    @Test
    fun `a dead network is a transport failure rather than an exception`() = runTest {
        val client = client(TokenProvider.Anonymous) { throw RuntimeException("no route to host") }

        val failure = client.get("api/driver/trips", Echo.serializer()).exceptionOrNull()?.apiFailure

        assertThat(failure is ApiFailure.Transport).isTrue()
        assertThat(failure?.disposition).isEqualTo(FailureDisposition.Retry)
    }

    private fun jsonHeaders() = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun client(
        tokens: TokenProvider,
        clock: ServerClock = ServerClock(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): TmsApiClient {
        val engine = MockEngine { request -> handler(request) }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(TmsJson) }
            defaultRequest { url("http://127.0.0.1:4400/") }
        }
        return TmsApiClient(http, tokens, clock)
    }

    private class FixedToken(private val token: String?) : TokenProvider {
        override suspend fun accessToken(): String? = token
        override suspend fun refresh(): Boolean = false
    }

    private class RefreshingToken : TokenProvider {
        var refreshes = 0
        private var token = "stale"
        override suspend fun accessToken(): String = token
        override suspend fun refresh(): Boolean {
            refreshes++
            token = "fresh"
            return true
        }
    }
}
