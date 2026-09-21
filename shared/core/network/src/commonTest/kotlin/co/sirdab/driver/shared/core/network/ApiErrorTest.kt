package co.sirdab.driver.shared.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

/**
 * The dispositions are the offline queue's whole policy. Getting one wrong either loses a driver's
 * write or retries a doomed one forever, so they are pinned to the contract's response table here.
 */
class ApiErrorTest {

    @Test
    fun `a stale token asks for a refresh rather than a drop`() {
        assertThat(http(401, "unauthorized").disposition).isEqualTo(FailureDisposition.Reauthenticate)
    }

    @Test
    fun `not being set up yet pauses the queue instead of looping`() {
        assertThat(http(403, "no_active_account").disposition).isEqualTo(FailureDisposition.Pause)
        assertThat(http(403, "forbidden").disposition).isEqualTo(FailureDisposition.Pause)
    }

    @Test
    fun `a payload the contract rejects is dropped rather than retried`() {
        assertThat(http(400, "validation_failed").disposition).isEqualTo(FailureDisposition.Drop)
        assertThat(http(409, "idempotency_key_reused").disposition).isEqualTo(FailureDisposition.Drop)
        assertThat(http(422, "unknown_reference").disposition).isEqualTo(FailureDisposition.Drop)
        assertThat(http(404, "not_found").disposition).isEqualTo(FailureDisposition.Drop)
    }

    @Test
    fun `transient failures stay queued`() {
        assertThat(http(500, "internal").disposition).isEqualTo(FailureDisposition.Retry)
        assertThat(http(429, "too_many").disposition).isEqualTo(FailureDisposition.Retry)
        assertThat(ApiFailure.Transport(RuntimeException("offline")).disposition)
            .isEqualTo(FailureDisposition.Retry)
    }

    @Test
    fun `an undocumented domain 409 is dropped and keeps its wire code`() {
        // Phase 3 introduces driver domain codes that this build has never heard of.
        val failure = http(409, "stop_out_of_sequence")
        assertThat(failure.disposition).isEqualTo(FailureDisposition.Drop)
        assertThat(failure.code).isEqualTo(ApiErrorCode.Unknown)
        assertThat(failure.rawCode).isEqualTo("stop_out_of_sequence")
    }

    @Test
    fun `an unbuilt endpoint parks the write rather than spinning or losing it`() {
        // Most driver endpoints answer 501 today. Retrying would hammer them; dropping would throw
        // away what the driver recorded.
        assertThat(http(501, "not_implemented").disposition).isEqualTo(FailureDisposition.Pause)
    }

    private fun http(status: Int, code: String) = ApiFailure.Http(
        status = status,
        rawCode = code,
        code = ApiErrorCode.fromWire(code),
        message = "test",
    )
}
