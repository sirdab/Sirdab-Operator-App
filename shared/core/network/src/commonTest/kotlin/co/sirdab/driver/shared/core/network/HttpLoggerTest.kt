package co.sirdab.driver.shared.core.network

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import kotlin.test.Test

/**
 * The log exists to be read by a person and pasted into a bug report, which is
 * exactly why what it must never carry is a working session.
 */
class HttpLoggerTest {

    @Test
    fun `blanks the tokens the auth endpoint answers with`() {
        val body = """{"access_token":"eyJhbGciOi.abc","token_type":"bearer",""" +
            """"refresh_token":"6mtdfatma4h7"}"""

        val safe = redactSecrets(body)

        assertThat(safe).doesNotContain("eyJhbGciOi.abc")
        assertThat(safe).doesNotContain("6mtdfatma4h7")
        // Still readable as the same response, which is the point of redacting
        // rather than dropping the body.
        assertThat(safe).contains(""""token_type":"bearer"""")
    }

    @Test
    fun `leaves everything else exactly as it was`() {
        val body = """{"reference":"TRP-000002","status":"assigned","stopCount":2}"""

        assertThat(redactSecrets(body)).isEqualTo(body)
    }

    @Test
    fun `keeps the photo bytes out of the log`() {
        // The upload is the one request whose body is megabytes of JPEG.
        assertThat(isLoggableBody("http://host:54321/storage/v1/object/upload/sign/x.jpg"))
            .isEqualTo(false)
        assertThat(isLoggableBody("http://host:4400/api/driver/trips")).isEqualTo(true)
    }

    @Test
    fun `blanks the credential in a signed storage url`() {
        val line = "UPLOAD OK 200 1024B -> http://host:54321/storage/v1/object/upload/sign/x.jpg?token=eyJsecret"
        val body = """{"downloadUrl":"https://host/storage/v1/object/sign/doc.jpg?token=eyJread&download=1"}"""

        assertThat(redactSecrets(line)).doesNotContain("eyJsecret")
        assertThat(redactSecrets(line)).contains("/storage/v1/object/upload/sign/x.jpg?token=***")
        assertThat(redactSecrets(body)).doesNotContain("eyJread")
        // Only the credential goes; the rest of the query is still readable.
        assertThat(redactSecrets(body)).contains("&download=1")
    }
}
