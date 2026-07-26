package co.sirdab.driver.shared.core.util

/**
 * Arabic-Indic numeral rendering. Per the plan: use Arabic-Indic digits (٠١٢٣) for display in
 * Arabic/Urdu, Western digits everywhere else and in all input fields.
 */
private val ARABIC_INDIC = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

fun String.toArabicIndicDigits(): String = buildString {
    for (c in this@toArabicIndicDigits) {
        append(if (c in '0'..'9') ARABIC_INDIC[c - '0'] else c)
    }
}

/** Localize a numeric display string for the given language code ("ar"/"ur" -> Arabic-Indic). */
fun String.localizeDigits(languageCode: String): String =
    if (languageCode == "ar" || languageCode == "ur") toArabicIndicDigits() else this

fun Int.toLocalizedString(languageCode: String): String = toString().localizeDigits(languageCode)

/** Group thousands with a comma, then localize digits. e.g. 3240 -> "3,240" -> "٣٬٢٤٠". */
fun Int.toGroupedString(languageCode: String): String {
    val grouped = toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
    return grouped.localizeDigits(languageCode)
}
