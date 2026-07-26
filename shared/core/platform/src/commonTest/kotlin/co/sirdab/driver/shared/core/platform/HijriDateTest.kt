package co.sirdab.driver.shared.core.platform

import co.sirdab.driver.shared.core.platform.datetime.format
import co.sirdab.driver.shared.core.platform.datetime.hijriFromEpochMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class HijriDateTest {

    @Test
    fun conversionProducesPlausibleFields() {
        // 2026-07-24T00:00:00Z
        val date = hijriFromEpochMillis(1_784_246_400_000L)
        assertTrue(date.year in 1440..1460, "year=${date.year}")
        assertTrue(date.month in 1..12, "month=${date.month}")
        assertTrue(date.day in 1..30, "day=${date.day}")
    }

    @Test
    fun formatCarriesSuffix() {
        val date = hijriFromEpochMillis(1_784_246_400_000L)
        assertTrue(date.format("en").endsWith("AH"))
        assertTrue(date.format("ar").endsWith("هـ"))
    }
}
