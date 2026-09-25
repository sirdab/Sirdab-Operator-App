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
 *
 * The access model of 2026-09-21 rewrote what the token hook stamps: `tms_access` and
 * `tms_driver_id` in place of `tms_role`, `tms_scope_type` and `tms_scope_id`. The old names no
 * longer appear in any token, which is why the last test here pins the app reading a token that
 * carries only them as nobody at all.
 */
class JwtDecoderTest {

    @Test
    fun `reads the workspace and the access level and the driver row a driver token carries`() {
        val claims = JwtDecoder.decode(
            token(
                """{"sub":"p1","app_metadata":{"workspace_id":"ws-1","organization_id":"org-1"},""" +
                    """"tms_access":"driver","tms_driver_id":"drv-7"}""",
            ),
        )

        assertThat(claims?.workspaceId).isEqualTo("ws-1")
        assertThat(claims?.driverId).isEqualTo("drv-7")
        assertThat(claims?.isDriver).isEqualTo(true)
        assertThat(claims?.hasWorkspace).isEqualTo(true)
    }

    @Test
    fun `a token with no workspace is signed in but not linked to a fleet`() {
        // The person authenticated fine; no fleet has them on its roster yet. The hook strips the
        // access claims along with the workspace, so there is nothing else to read.
        val claims = JwtDecoder.decode(token("""{"sub":"p1"}"""))

        assertThat(claims?.hasWorkspace).isEqualTo(false)
        assertThat(claims?.isDriver).isEqualTo(false)
        assertThat(claims?.driverId).isNull()
    }

    @Test
    fun `staff on the same workspace are not drivers`() {
        val claims = JwtDecoder.decode(
            token("""{"sub":"p1","app_metadata":{"workspace_id":"ws-1"},"tms_access":"staff"}"""),
        )

        assertThat(claims?.isDriver).isEqualTo(false)
        assertThat(claims?.hasWorkspace).isEqualTo(true)
    }

    @Test
    fun `an admin token is not a driver either`() {
        val claims = JwtDecoder.decode(
            token("""{"sub":"p1","app_metadata":{"workspace_id":"ws-1"},"tms_access":"admin"}"""),
        )

        assertThat(claims?.isDriver).isEqualTo(false)
    }

    @Test
    fun `the retired role claims do not make a driver`() {
        // A token minted before the access model, or by a stale hook. Treating it as a driver would
        // send someone into the app with claims the API no longer honours.
        val claims = JwtDecoder.decode(
            token(
                """{"sub":"p1","app_metadata":{"workspace_id":"ws-1"},"tms_role":"driver",""" +
                    """"tms_scope_type":"driver","tms_scope_id":"drv-7"}""",
            ),
        )

        assertThat(claims?.isDriver).isEqualTo(false)
        assertThat(claims?.driverId).isNull()
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
