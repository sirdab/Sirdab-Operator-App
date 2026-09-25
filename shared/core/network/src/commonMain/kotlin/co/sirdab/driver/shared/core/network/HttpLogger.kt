package co.sirdab.driver.shared.core.network

import io.ktor.client.plugins.logging.Logger

/**
 * Where Ktor's request log goes on each platform.
 *
 * A named sink rather than Ktor's default, which lands under whatever tag the
 * platform gives stdout and is impossible to filter. This one is greppable:
 *
 *     adb logcat -s TmsApi:D
 */
expect fun platformHttpLogger(): Logger

/** The tag every line is written under, on both platforms. */
const val HTTP_LOG_TAG = "TmsApi"

/**
 * Whether this request's body is worth printing.
 *
 * Storage uploads are the photo itself: a few hundred kilobytes of JPEG, which
 * would bury every useful line in the log and tell nobody anything. The call
 * is still logged, without its body, by [TmsApiClient.putBytes].
 */
internal fun isLoggableBody(path: String): Boolean = !path.contains(STORAGE_PATH_MARKER)

private const val STORAGE_PATH_MARKER = "/storage/v1/"

/**
 * Blanks out credentials that live in a body rather than a header.
 *
 * Sanitizing headers is not enough: the Supabase token endpoint answers with
 * the access and refresh tokens in its JSON, so a log meant to be pasted into
 * a bug report or shared on a call would carry a working session with it. The
 * refresh token is the worse of the two, because it outlives the other.
 */
fun redactSecrets(message: String): String {
    val fields = SECRET_FIELDS.fold(message) { text, field ->
        Regex(""""$field"\s*:\s*"[^"]*"""").replace(text, """"$field":"***"""")
    }
    // Signed storage URLs carry their credential in the query string, not in a field: an upload
    // target's `?token=` or a document's download link, which is ten minutes of read access to a
    // driver's ID. The URL stays readable; only the credential goes.
    return SIGNED_QUERY_PARAM.replace(fields) { match -> "${match.groupValues[1]}***" }
}

private val SECRET_FIELDS = listOf("access_token", "refresh_token", "apikey", "token")

private val SIGNED_QUERY_PARAM =
    Regex("""([?&](?:token|sig|signature|X-Amz-Signature|X-Amz-Credential|X-Amz-Security-Token)=)[^&"\s]*""", RegexOption.IGNORE_CASE)
