package co.sirdab.driver.shared.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NumeralsTest {

    @Test
    fun englishKeepsWesternDigits() {
        assertEquals("3240", "3240".localizeDigits("en"))
        assertEquals("3240", "3240".localizeDigits("hi"))
    }

    @Test
    fun arabicAndUrduUseArabicIndic() {
        assertEquals("٣٢٤٠", "3240".localizeDigits("ar"))
        assertEquals("٣٢٤٠", "3240".localizeDigits("ur"))
    }

    @Test
    fun groupingAddsThousandsSeparator() {
        assertEquals("3,240", 3240.toGroupedString("en"))
        assertEquals("1,234,567", 1234567.toGroupedString("en"))
    }

    @Test
    fun groupingLocalizesDigitsButKeepsSeparator() {
        assertEquals("٣,٢٤٠", 3240.toGroupedString("ar"))
    }
}
