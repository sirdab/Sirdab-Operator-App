package co.sirdab.driver.shared.core.network

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The error codes the TMS documents. Branch on the code, never on the HTTP status alone: a 409 can
 * be either the idempotency guard or a domain rule, and they want opposite handling.
 *
 * A code the contract has not published yet (the driver domain 409s arrive with phase 3) parses as
 * [Unknown] while [ApiFailure.Http.rawCode] keeps the wire string.
 */
enum class ApiErrorCode(val wire: String) {
    ValidationFailed("validation_failed"),
    InvalidCursor("invalid_cursor"),
    IdempotencyUnsupported("idempotency_unsupported"),
    Unauthorized("unauthorized"),
    // Renamed with the 2026-09-21 access model. Both spellings are matched: the older one is
    // still in the contract's own error table, and a server on either answers the same question.
    NoActiveWorkspace("no_active_workspace"),
    NoActiveAccount("no_active_account"),
    Forbidden("forbidden"),
    NotFound("not_found"),
    IdempotencyKeyReused("idempotency_key_reused"),
    UnknownReference("unknown_reference"),
    NotImplemented("not_implemented"),
    Internal("internal"),
    Unknown("");

    companion object {
        fun fromWire(wire: String): ApiErrorCode = entries.firstOrNull { it.wire == wire } ?: Unknown
    }
}

@Serializable
internal data class ApiErrorEnvelope(val error: ApiErrorBody)

@Serializable
internal data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

sealed interface ApiFailure {
    val message: String

    /** The server answered with the documented error envelope. */
    data class Http(
        val status: Int,
        val rawCode: String,
        val code: ApiErrorCode,
        override val message: String,
        val details: JsonElement? = null,
    ) : ApiFailure

    /** The request never completed: no connectivity, timeout, DNS, TLS. */
    data class Transport(val cause: Throwable) : ApiFailure {
        override val message: String get() = cause.message ?: "network unavailable"
    }

    /** A 2xx whose body did not match what the contract says it should be. */
    data class Malformed(val cause: Throwable) : ApiFailure {
        override val message: String get() = cause.message ?: "unexpected response"
    }
}

/**
 * What the offline write queue should do with a failure, straight from the response table in the
 * driver app contract. Reads are simpler, but share the vocabulary.
 */
enum class FailureDisposition {
    /** Transient. Keep the write queued and retry with backoff. */
    Retry,

    /** The write will never succeed as written. Drop it and surface it. */
    Drop,

    /** The token is stale. Refresh, then retry the same request with the same key. */
    Reauthenticate,

    /** Not allowed, or not set up yet. Stop draining and tell the driver; never loop. */
    Pause,
}

val ApiFailure.disposition: FailureDisposition
    get() = when (this) {
        is ApiFailure.Transport -> FailureDisposition.Retry
        is ApiFailure.Malformed -> FailureDisposition.Retry
        is ApiFailure.Http -> when {
            status == 401 -> FailureDisposition.Reauthenticate
            status == 403 -> FailureDisposition.Pause
            // Not built yet. Retrying would spin against an endpoint that cannot answer until the
            // phase ships, and dropping would throw away work the driver actually did, so the queue
            // parks instead. Nothing should be queued against a 501 endpoint in the first place.
            status == 501 -> FailureDisposition.Pause
            status == 429 || status >= 500 -> FailureDisposition.Retry
            else -> FailureDisposition.Drop
        }
    }

/**
 * Bridges a transport failure into the result type the existing screens already render.
 *
 * Two server answers are named rather than relayed, because they are not errors the driver can do
 * anything about and the server's own wording says so badly: an endpoint that has not shipped yet,
 * and a token with no workspace on it. Everything else keeps the server's message, which for a
 * validation failure beats anything the app could invent.
 */
fun ApiFailure.toAppError(): AppError = when (this) {
    is ApiFailure.Http -> AppError(message, reason = reason())
    is ApiFailure.Transport -> AppError(message, cause)
    is ApiFailure.Malformed -> AppError(message, cause)
}

private fun ApiFailure.Http.reason(): AppErrorReason? = when {
    // A phase that has not landed. The screen says "coming soon", not "something went wrong".
    status == 501 || code == ApiErrorCode.NotImplemented -> AppErrorReason.UNAVAILABLE
    code == ApiErrorCode.NoActiveWorkspace || code == ApiErrorCode.NoActiveAccount ->
        AppErrorReason.NOT_PROVISIONED
    else -> null
}
