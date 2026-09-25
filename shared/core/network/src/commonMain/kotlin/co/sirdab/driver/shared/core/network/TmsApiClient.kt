package co.sirdab.driver.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.timeout
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** A successful call, plus whether the server replayed an earlier identical write. */
data class ApiSuccess<T>(val value: T, val replayed: Boolean = false)

typealias ApiResult<T> = Result<ApiSuccess<T>>

/**
 * The one place that knows how to talk to the TMS.
 *
 * It owns the four conventions the API expects of every caller: the bearer token, the
 * `Idempotency-Key` header on writes, the error envelope, and a single refresh-and-retry on 401.
 *
 * Bodies are serialized once, to a string, and that exact string is sent. The server hashes
 * `method + "\n" + path + "\n" + raw body` to decide whether a retry is a replay, so re-encoding an
 * object between attempts (which can reorder keys) would read as a different write and earn a
 * `409 idempotency_key_reused`. The queue stores the string this produces, not the object.
 */
class TmsApiClient(
    private val http: HttpClient,
    private val tokens: TokenProvider,
    private val serverClock: ServerClock,
    private val json: Json = TmsJson,
    private val logger: Logger = platformHttpLogger(),
) {

    suspend fun <T> get(
        path: String,
        deserializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute(HttpMethod.Get, path, deserializer, query = query)

    /**
     * [idempotencyKey] must be one uuid per logical write, generated when the driver acts and
     * reused verbatim on every retry of that same write.
     */
    suspend fun <T> post(
        path: String,
        body: String,
        deserializer: KSerializer<T>,
        idempotencyKey: String? = null,
    ): ApiResult<T> = execute(HttpMethod.Post, path, deserializer, body = body, idempotencyKey = idempotencyKey)

    /**
     * A partial update. Not idempotent-keyed: the contract does not offer it on `PATCH`, and a
     * partial update of the same fields to the same values is its own replay.
     */
    suspend fun <T> patch(
        path: String,
        body: String,
        deserializer: KSerializer<T>,
    ): ApiResult<T> = execute(HttpMethod.Patch, path, deserializer, body = body)

    suspend fun <T> delete(
        path: String,
        deserializer: KSerializer<T>,
    ): ApiResult<T> = execute(HttpMethod.Delete, path, deserializer)

    /**
     * PUT raw bytes to a URL the server minted.
     *
     * Deliberately outside [execute]: no bearer token, no idempotency key, no
     * error envelope and no JSON. The signed URL carries its own short-lived
     * credential, storage recomputes the signature over exactly the headers it
     * signed, and anything this added would invalidate it. A refresh-and-retry
     * would be meaningless too, since the credential is not the session's.
     *
     * The timeout is its own: a proof photo over a warehouse's one bar of
     * signal is a different proposition from a JSON call.
     */
    suspend fun putBytes(
        url: String,
        bytes: ByteArray,
        headers: Map<String, String>,
    ): Result<Unit> = catching {
        val response = http.request(url) {
            method = HttpMethod.Put
            // Content-Type goes through contentType(), not header(): header()
            // appends, so setting it that way leaves two of them and Ktor sends
            // the body's own. Storage validates the type it receives against
            // what it signed, and answers 400 InvalidMimeType on a mismatch.
            val declared = headers.entries
                .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
                ?.value
            contentType(
                declared?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                    ?: ContentType.Application.OctetStream,
            )
            headers
                .filterNot { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
                .forEach { (name, value) -> header(name, value) }
            setBody(bytes)
            timeout {
                requestTimeoutMillis = UPLOAD_TIMEOUT_MILLIS
                socketTimeoutMillis = UPLOAD_TIMEOUT_MILLIS
            }
        }
        readServerDate(response)

        if (!response.status.isSuccess()) {
            val text = catching { response.bodyAsText() }.getOrDefault("")
            // The body was filtered out of the request log because it is a
            // photo, so without this an upload failure is a silent gap between
            // the file being minted and the proof being posted.
            logger.log("UPLOAD FAILED ${response.status.value} ${bytes.size}B -> $url: $text")
            throw ApiFailureException(parseError(response.status.value, text))
        }
        logger.log("UPLOAD OK ${response.status.value} ${bytes.size}B -> $url")
    }.recoverCatching { cause ->
        // Anything that is not already an interpreted failure never reached
        // storage at all, which is a transport problem and worth retrying.
        throw if (cause is ApiFailureException) cause else ApiFailureException(ApiFailure.Transport(cause))
    }

    /** Serializes with the client's own Json so the queued string and the sent bytes agree. */
    fun <B> encode(serializer: KSerializer<B>, body: B): String = json.encodeToString(serializer, body)

    private suspend fun <T> execute(
        method: HttpMethod,
        path: String,
        deserializer: KSerializer<T>,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
        idempotencyKey: String? = null,
    ): ApiResult<T> {
        val first = send(method, path, query, body, idempotencyKey)
        val response = first.getOrElse { return Result.failure(it) }

        // One refresh, one retry, same key and same body. A second 401 means the session is gone.
        if (response.status.value == 401 && tokens.refresh()) {
            val retried = send(method, path, query, body, idempotencyKey)
            val retriedResponse = retried.getOrElse { return Result.failure(it) }
            return interpret(retriedResponse, deserializer)
        }

        return interpret(response, deserializer)
    }

    private suspend fun send(
        method: HttpMethod,
        path: String,
        query: Map<String, String>,
        body: String?,
        idempotencyKey: String?,
    ): Result<HttpResponse> = catching {
        http.request(path.trimStart('/')) {
            this.method = method
            tokens.accessToken()?.let { bearerAuth(it) }
            idempotencyKey?.let { header(IDEMPOTENCY_KEY_HEADER, it) }
            query.forEach { (key, value) -> parameter(key, value) }
            applyBody(body)
        }
    }.recoverCatching { throw ApiFailureException(ApiFailure.Transport(it)) }

    private fun HttpRequestBuilder.applyBody(body: String?) {
        if (body == null) return
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun <T> interpret(response: HttpResponse, deserializer: KSerializer<T>): ApiResult<T> {
        readServerDate(response)

        val text = catching { response.bodyAsText() }
            .getOrElse { return failure(ApiFailure.Transport(it)) }

        if (!response.status.isSuccess()) {
            return failure(parseError(response.status.value, text))
        }

        val value = runCatching { json.decodeFromString(deserializer, text) }
            .getOrElse { return failure(ApiFailure.Malformed(it)) }

        val replayed = response.headers[IDEMPOTENCY_REPLAYED_HEADER]?.equals("true", ignoreCase = true) == true
        return Result.success(ApiSuccess(value, replayed))
    }

    private fun readServerDate(response: HttpResponse) {
        val date = response.headers["Date"] ?: return
        parseHttpDate(date)?.let(serverClock::observeServerDate)
    }

    private fun parseError(status: Int, text: String): ApiFailure {
        val envelope = runCatching { json.decodeFromString(ApiErrorEnvelope.serializer(), text) }.getOrNull()
            ?: return ApiFailure.Http(
                status = status,
                rawCode = "",
                code = ApiErrorCode.Unknown,
                message = "The server returned $status with an unrecognised body.",
            )

        return ApiFailure.Http(
            status = status,
            rawCode = envelope.error.code,
            code = ApiErrorCode.fromWire(envelope.error.code),
            message = envelope.error.message,
            details = envelope.error.details,
        )
    }

    private fun <T> failure(cause: ApiFailure): ApiResult<T> = Result.failure(ApiFailureException(cause))

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed"
        const val UPLOAD_TIMEOUT_MILLIS = 120_000L
    }
}

/** Carries an [ApiFailure] through [Result], which only transports throwables. */
class ApiFailureException(val failure: ApiFailure) : Exception(failure.message)

/** The failure behind a [ApiResult], or null when the throwable came from somewhere else. */
val Throwable.apiFailure: ApiFailure?
    get() = (this as? ApiFailureException)?.failure

private fun io.ktor.http.HttpStatusCode.isSuccess(): Boolean = value in 200..299

/** RFC 7231 IMF-fixdate, the only format an HTTP `Date` header is allowed to use. */
/**
 * [runCatching], minus cancellation.
 *
 * A request cancelled with its screen or its queue job has to stay cancelled. Caught, it came back
 * as an ordinary "network unavailable": the ViewModel showed an error nobody caused, and the queue
 * booked a retry for a drain that had been told to stop.
 */
private inline fun <T> catching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

internal fun parseHttpDate(raw: String): Instant? = runCatching {
    val parts = raw.trim().removeSuffix(" GMT").split(", ", " ", ":")
        .filter { it.isNotBlank() }
    if (parts.size < 7) return null
    val day = parts[1].toInt()
    val month = MONTHS.indexOf(parts[2]).takeIf { it >= 0 } ?: return null
    val year = parts[3].toInt()
    val hour = parts[4].toInt()
    val minute = parts[5].toInt()
    val second = parts[6].toInt()
    Instant.parse(
        buildString {
            append(year.toString().padStart(4, '0')); append('-')
            append((month + 1).toString().padStart(2, '0')); append('-')
            append(day.toString().padStart(2, '0')); append('T')
            append(hour.toString().padStart(2, '0')); append(':')
            append(minute.toString().padStart(2, '0')); append(':')
            append(second.toString().padStart(2, '0')); append('Z')
        },
    )
}.getOrNull()

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
