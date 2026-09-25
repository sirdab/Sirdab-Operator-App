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

    @Test
    fun keyboardDigitsBecomeWestern() {
        assertEquals("0501234567", "٠٥٠١٢٣٤٥٦٧".westernDigits())
        assertEquals("123456", "۱۲۳۴۵۶".westernDigits())
        assertEquals("123", "1-2 3".westernDigits())
    }

    @Test
    fun everyWayOfWritingASaudiMobileGivesTheSameNineDigits() {
        listOf("0501234567", "+966 50 123 4567", "00966501234567", "966501234567", "501234567", "٠٥٠١٢٣٤٥٦٧")
            .forEach { assertEquals("501234567", saudiMobileDigits(it), it) }
    }

    @Test
    fun typingDigitByDigitNeverLosesTheNumber() {
        var field = ""
        "0501234567".forEach { field = saudiMobileDigits(field + it) }
        assertEquals("501234567", field)
    }

    @Test
    fun onlyAWholeNumberStartingWithFiveIsAMobile() {
        assertEquals(true, isSaudiMobile("501234567"))
        assertEquals(false, isSaudiMobile("50123456"))
        assertEquals(false, isSaudiMobile("112345678"))
    }
}
