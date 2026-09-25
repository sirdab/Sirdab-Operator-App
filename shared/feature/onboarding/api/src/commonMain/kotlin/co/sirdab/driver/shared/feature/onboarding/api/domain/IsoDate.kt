package co.sirdab.driver.shared.feature.onboarding.api.domain

/**
 * A date the contract will accept: `yyyy-MM-dd` and nothing else.
 *
 * The date picker can only produce this shape, so this guards the draft rather than the driver —
 * a half-written date reaching the server fails the whole write for a reason no one can see.
 */
fun isIsoDate(value: String): Boolean =
    value.length == 10 &&
        value[4] == '-' && value[7] == '-' &&
        value.filterIndexed { index, _ -> index != 4 && index != 7 }.all { it.isDigit() }
