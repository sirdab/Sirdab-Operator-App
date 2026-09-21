package co.sirdab.driver.shared.core.auth

import co.sirdab.driver.shared.core.network.ApiErrorCode
import co.sirdab.driver.shared.core.network.ApiFailure
import co.sirdab.driver.shared.core.network.ApiFailureException
import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.core.network.TmsJson
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase Auth, the three calls the driver app needs: ask for a code, exchange the code for a
 * session, and refresh that session.
 *
 * Hand-written rather than pulled from an SDK. It is three endpoints and one token shape, and the
 * app needs its own claim decoding and secure storage regardless, so a client library would add a
 * dependency tree without removing any of the work.
 */
class SupabaseAuthApi(
    private val http: HttpClient,
    private val environment: TmsEnvironment,
) {

    /** Sends the OTP. Locally no SMS is sent and the test numbers accept a fixed code. */
    suspend fun requestOtp(phone: String): Result<Unit> = runCatching {
        post("otp", TmsJson.encodeToString(OtpRequest.serializer(), OtpRequest(phone)))
    }.map { }

    suspend fun verifyOtp(phone: String, code: String): Result<SupabaseSession> = runCatching {
        val body = TmsJson.encodeToString(VerifyRequest.serializer(), VerifyRequest(phone = phone, token = code))
        TmsJson.decodeFromString(SupabaseSession.serializer(), post("verify", body))
    }

    suspend fun refresh(refreshToken: String): Result<SupabaseSession> = runCatching {
        val body = TmsJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        TmsJson.decodeFromString(SupabaseSession.serializer(), post("token?grant_type=refresh_token", body))
    }

    /** Returns the response body, or throws [ApiFailureException]. */
    private suspend fun post(path: String, body: String): String {
        val response: HttpResponse = try {
            http.post("${environment.supabaseUrl.trimEnd('/')}/auth/v1/$path") {
                contentType(ContentType.Application.Json)
                header("apikey", environment.supabaseAnonKey)
                setBody(body)
            }
        } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            throw ApiFailureException(ApiFailure.Transport(error))
        }

        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw ApiFailureException(authError(response.status.value, text))
        }
        return text
    }

    /** Supabase uses its own error shape, not the TMS envelope. */
    private fun authError(status: Int, text: String): ApiFailure {
        val message = runCatching { TmsJson.decodeFromString(SupabaseError.serializer(), text) }
            .getOrNull()
            ?.bestMessage()
            ?: "Sign-in failed ($status)."

        return ApiFailure.Http(
            status = status,
            rawCode = "supabase_auth",
            code = ApiErrorCode.Unknown,
            message = message,
        )
    }
}

@Serializable
private data class OtpRequest(val phone: String)

@Serializable
private data class VerifyRequest(
    val phone: String,
    val token: String,
    val type: String = "sms",
)

@Serializable
private data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
private data class SupabaseError(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val msg: String? = null,
    val message: String? = null,
) {
    fun bestMessage(): String? = errorDescription ?: msg ?: message ?: error
}

@Serializable
data class SupabaseSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String = "bearer",
)
