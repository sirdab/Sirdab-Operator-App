package co.sirdab.driver.shared.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The claims the TMS reads off the access token. The API keeps no second copy of the session, so
 * these decide what a request may do, and the app needs them to tell three states apart: signed in
 * and ready, signed in but not yet linked to a workspace, and not a driver at all.
 *
 * [scopeId] is the `drivers.id` row this person is. It is what scopes trips and bids, so the app
 * needs it to talk about itself.
 */
data class DriverClaims(
    val subject: String,
    /** The tenant. Was `account_id` before the server grew organizations above workspaces. */
    val workspaceId: String?,
    val role: String?,
    val scopeType: String?,
    val scopeId: String?,
    val expiresAtEpochSeconds: Long?,
) {
    /**
     * The driver endpoints accept either a `driver` role or a `driver` scope: a carrier-role
     * membership scoped to a driver passes too.
     */
    val isDriver: Boolean get() = role == "driver" || scopeType == "driver"

    /**
     * A valid sign-in with no tenant. Not a login failure: this phone has not been linked to the
     * driver row a dispatcher created for it, and until it is the API answers 403
     * `no_active_workspace` to everything.
     */
    val hasWorkspace: Boolean get() = !workspaceId.isNullOrBlank()
}

/**
 * Reads the payload of a JWT without verifying it.
 *
 * Verification is the server's job and it does it properly, against Supabase, on every request. The
 * app decodes only to decide what to show; it never grants itself anything on the strength of this.
 */
object JwtDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(token: String): DriverClaims? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val decoded = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
        }.getOrNull() ?: return null

        val claims = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null
        val appMetadata = claims["app_metadata"]?.let { runCatching { it.jsonObject }.getOrNull() }

        return DriverClaims(
            subject = claims.string("sub").orEmpty(),
            workspaceId = appMetadata?.string("workspace_id"),
            role = claims.string("tms_role"),
            scopeType = claims.string("tms_scope_type"),
            scopeId = claims.string("tms_scope_id"),
            expiresAtEpochSeconds = claims.string("exp")?.toLongOrNull(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNullSafe() }.getOrNull() }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        content.takeIf { it.isNotBlank() && it != "null" }
}
