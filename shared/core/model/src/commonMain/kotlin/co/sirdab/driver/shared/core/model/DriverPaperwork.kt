package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

/**
 * The paperwork onboarding collects, as the TMS names it.
 *
 * A citizen carries a national id and a resident an iqama; either one closes the same `identity`
 * gap, which is why the two are alternatives here rather than separate requirements. Most drivers
 * in Saudi road freight are residents, so the choice is not an edge case.
 */
@Serializable
enum class DriverDocumentKind(val wire: String) {
    NATIONAL_ID("national_id"),
    IQAMA("iqama"),
    DRIVING_LICENCE("driving_licence"),
    VEHICLE_REGISTRATION("vehicle_registration");

    /** One of the two the driver picks between to prove who they are. */
    val isIdentity: Boolean get() = this == NATIONAL_ID || this == IQAMA

    /**
     * A vehicle registration names its truck; the other two are refused if they carry one.
     *
     * The contract is strict in both directions, so this decides the request rather than decorating
     * it.
     */
    val needsTruck: Boolean get() = this == VEHICLE_REGISTRATION

    companion object {
        fun fromWire(wire: String): DriverDocumentKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** Where a submitted document stands with ops. */
@Serializable
enum class DriverDocumentStatus(val wire: String) {
    UPLOADED("uploaded"),
    APPROVED("approved"),
    REJECTED("rejected");

    companion object {
        fun fromWire(wire: String): DriverDocumentStatus = entries.firstOrNull { it.wire == wire } ?: UPLOADED
    }
}

/**
 * What the app may send as a document, and the ceiling the server enforces.
 *
 * Photographs are re-encoded to JPEG on the way out of the camera, so in practice only the first
 * of these is ever produced; the rest are here because the contract accepts them and a driver may
 * one day pick a PDF out of their files.
 */
object DocumentUpload {
    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val HEIC = "image/heic"
    const val PDF = "application/pdf"

    const val MAX_BYTES = 10 * 1024 * 1024

    val accepted = setOf(JPEG, PNG, HEIC, PDF)
}

/**
 * Nationality, as ISO 3166-1 alpha-2, which is the form the TMS stores.
 *
 * A list rather than a free text field: it is typed once by the driver and read forever by
 * dispatchers, and "Pakistan", "pakistani" and "PK" are the same fact filed three ways. These are
 * the nationalities Saudi road freight actually employs; the server takes any string, so adding to
 * this list is an app change alone.
 */
@Serializable
enum class Nationality(val wire: String) {
    SAUDI("SA"),
    BANGLADESHI("BD"),
    EGYPTIAN("EG"),
    ERITREAN("ER"),
    ETHIOPIAN("ET"),
    INDIAN("IN"),
    JORDANIAN("JO"),
    NEPALI("NP"),
    PAKISTANI("PK"),
    PALESTINIAN("PS"),
    FILIPINO("PH"),
    SRI_LANKAN("LK"),
    SUDANESE("SD"),
    SYRIAN("SY"),
    TURKISH("TR"),
    YEMENI("YE");

    companion object {
        fun fromWire(wire: String?): Nationality? = entries.firstOrNull { it.wire == wire }
    }
}
