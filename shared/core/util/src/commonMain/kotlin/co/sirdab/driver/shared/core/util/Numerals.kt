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

/**
 * The digits in this string, as Western digits, whatever keyboard typed them.
 *
 * Arabic and Urdu keyboards type Arabic-Indic (٠-٩) or Extended Arabic-Indic (۰-۹) digits, which
 * `isDigit()` accepts and a server expecting `+9665...` does not. Every Unicode decimal digit is
 * mapped to its value; anything else is dropped.
 */
fun String.westernDigits(): String = buildString {
    for (c in this@westernDigits) c.digitToIntOrNull()?.let { append(it) }
}

/**
 * The nine national digits of a Saudi mobile number, from however the driver typed or pasted it.
 *
 * `0501234567`, `+966 50 123 4567`, `00966501234567` and `501234567` are all the same phone. Taking
 * the first nine digits of the first form, as the field used to, kept `050123456`: a code sent to a
 * number that does not exist. The trunk `0` and the country code are dropped before truncating.
 */
fun saudiMobileDigits(input: String): String {
    var digits = input.westernDigits()
    digits = when {
        digits.startsWith("00966") -> digits.drop(5)
        // Only once it is longer than a national number: typed digit by digit, "966" on its own
        // is still ambiguous, and dropping it early would eat the start of what they are typing.
        digits.startsWith("966") && digits.length > NATIONAL_MOBILE_LENGTH -> digits.drop(3)
        else -> digits
    }
    return digits.removePrefix("0").take(NATIONAL_MOBILE_LENGTH)
}

/** Whether [digits] (as [saudiMobileDigits] returns them) is a whole Saudi mobile number. */
fun isSaudiMobile(digits: String): Boolean =
    digits.length == NATIONAL_MOBILE_LENGTH && digits.startsWith("5") && digits.all { it in '0'..'9' }

private const val NATIONAL_MOBILE_LENGTH = 9
