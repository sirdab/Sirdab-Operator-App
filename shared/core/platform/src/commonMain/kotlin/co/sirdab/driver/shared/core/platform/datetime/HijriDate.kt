package co.sirdab.driver.shared.core.platform.datetime

import co.sirdab.driver.shared.core.util.localizeDigits

/**
 * Tabular Islamic-calendar (civil) conversion in pure common Kotlin — no platform code, no
 * core-library desugaring. Good enough for demo display of Hijri dates. A precise Umm al-Qura
 * implementation (Android HijrahDate / iOS NSCalendar) can replace this behind the same API later.
 */
private val HIJRI_MONTHS_AR = arrayOf(
    "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
    "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
)
private val HIJRI_MONTHS_EN = arrayOf(
    "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal", "Jumada al-Thani",
    "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhu al-Qidah", "Dhu al-Hijjah",
)

data class HijriDate(val year: Int, val month: Int, val day: Int)

/** Convert epoch millis (UTC) to a tabular Hijri date. */
fun hijriFromEpochMillis(epochMillis: Long): HijriDate {
    val jd = (epochMillis / 86_400_000L) + 2440588L // Unix epoch -> Julian Day Number
    val l0 = jd - 1948440L + 10632L
    val n = (l0 - 1L) / 10631L
    var l = l0 - 10631L * n + 354L
    val j = (10985L - l) / 5316L * ((50L * l) / 17719L) + l / 5670L * ((43L * l) / 15238L)
    l = l - (30L - j) / 15L * ((17719L * j) / 50L) - j / 16L * ((15238L * j) / 43L) + 29L
    val month = ((24L * l) / 709L).toInt()
    val day = (l - (709L * month) / 24L).toInt()
    val year = (30L * n + j - 30L).toInt()
    return HijriDate(year, month, day)
}

fun HijriDate.format(languageCode: String): String {
    val ar = languageCode == "ar" || languageCode == "ur"
    val monthName = (if (ar) HIJRI_MONTHS_AR else HIJRI_MONTHS_EN)[(month - 1).coerceIn(0, 11)]
    val suffix = if (ar) "هـ" else "AH"
    return "$day $monthName ${year}".localizeDigits(languageCode) + " $suffix"
}
