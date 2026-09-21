package co.sirdab.driver.shared.core.auth

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test

/**
 * The claims decide which screen a signed-in person sees, and the three rejected states look
 * identical at the Supabase layer, so they are separated here.
 */
class JwtDecoderTest {

    @Test
    fun `reads the account and role and driver id a driver token carries`() {
        val claims = JwtDecoder.decode(
            token(
                """{"sub":"p1","app_metadata":{"workspace_id":"ws-1"},"tms_role":"driver",""" +
                    """"tms_scope_type":"driver","tms_scope_id":"drv-7"}""",
            ),
        )

        assertThat(claims?.workspaceId).isEqualTo("ws-1")
        assertThat(claims?.scopeId).isEqualTo("drv-7")
        assertThat(claims?.isDriver).isEqualTo(true)
        assertThat(claims?.hasWorkspace).isEqualTo(true)
    }

    @Test
    fun `a carrier membership scoped to a driver still counts as a driver`() {
        val claims = JwtDecoder.decode(
            token("""{"sub":"p1","app_metadata":{"workspace_id":"ws-1"},"tms_role":"carrier","tms_scope_type":"driver"}"""),
        )

        assertThat(claims?.isDriver).isEqualTo(true)
    }

    @Test
    fun `a token with no account is signed in but not provisioned`() {
        // The person authenticated fine; no dispatcher has created their membership yet.
        val claims = JwtDecoder.decode("""{"sub":"p1","tms_role":"driver"}""".let(::token))

        assertThat(claims?.hasWorkspace).isEqualTo(false)
        assertThat(claims?.isDriver).isEqualTo(true)
    }

    @Test
    fun `a dispatcher token is not a driver`() {
        val claims = JwtDecoder.decode(
            token("""{"sub":"p1","app_metadata":{"workspace_id":"ws-1"},"tms_role":"dispatcher"}"""),
        )

        assertThat(claims?.isDriver).isEqualTo(false)
        assertThat(claims?.hasWorkspace).isEqualTo(true)
    }

    @Test
    fun `garbage decodes to nothing rather than throwing`() {
        assertThat(JwtDecoder.decode("not-a-jwt")).isNull()
        assertThat(JwtDecoder.decode("a.b.c")).isNull()
        assertThat(JwtDecoder.decode("")).isNull()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun token(payload: String): String {
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"HS256","typ":"JWT"}""".encodeToByteArray())
        return "$header.${encoder.encode(payload.encodeToByteArray())}.signature"
    }
}
