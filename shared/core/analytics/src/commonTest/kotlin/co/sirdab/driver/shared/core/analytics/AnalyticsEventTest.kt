package co.sirdab.driver.shared.core.analytics

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContainKey
import assertk.assertions.isEqualTo
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.StopType
import kotlin.test.Test

class AnalyticsEventTest {

    @Test
    fun `a missing value is left out rather than sent as null`() {
        val event = AnalyticsEvent.screenViewed("loads")

        assertThat(event.properties).doesNotContainKey("trip_id")
    }

    @Test
    fun `a failure names its reason, and one with no named reason is other`() {
        val named = AnalyticsEvent.signInFailed(AppError("no", reason = AppErrorReason.NOT_A_DRIVER))
        val unnamed = AnalyticsEvent.signInFailed(AppError("Unable to resolve host"))

        assertThat(named.properties["error_reason"]).isEqualTo("not_a_driver")
        assertThat(unnamed.properties["error_reason"]).isEqualTo("other")
        assertThat(unnamed.properties["error_message"]).isEqualTo("Unable to resolve host")
    }

    @Test
    fun `a long server message is cut down`() {
        val event = AnalyticsEvent.bidFailed("p1", 900, AppError("x".repeat(5_000)), posting = null, nowMillis = 0)

        assertThat((event.properties["error_message"] as String).length).isEqualTo(200)
    }

    @Test
    fun `stop types travel lowercased, like every other enum value`() {
        val event = AnalyticsEvent.stopActionRecorded("t1", "s1", "delivered", StopType.DROPOFF, 2)

        assertThat(event.properties).containsOnly(
            "trip_id" to "t1",
            "stop_id" to "s1",
            "action" to "delivered",
            "stop_type" to "dropoff",
            "stop_sequence" to 2,
        )
    }

    @Test
    fun `minutes count whole minutes, and run negative for early`() {
        assertThat(AnalyticsEvent.minutesBetween(0, 90_000)).isEqualTo(1)
        assertThat(AnalyticsEvent.minutesBetween(600_000, 0)).isEqualTo(-10)
    }
}
