package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

/**
 * The paperwork a fleet holds on a driver, as ops reviews it.
 *
 * Five kinds, not the three sign-up collects: this is the workspace-scoped verification surface
 * that `GET /api/driver/me` reports, and it carries the identity document a resident holds and the
 * photo of the truck as well. Kept apart from [DriverDocumentKind] on purpose — they are two
 * different document surfaces with two different vocabularies, and conflating them would file a
 * driver's iqama as a kind the onboarding endpoint has never heard of.
 */
@Serializable
enum class VerificationDocumentKind(val wire: String) {
    NATIONAL_ID("national_id"),
    IQAMA("iqama"),
    DRIVING_LICENCE("driving_licence"),
    ISTIMARA("istimara"),
    TRUCK_PHOTO("truck_photo");

    companion object {
        fun fromWire(wire: String): VerificationDocumentKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** Where one document stands with ops. */
@Serializable
enum class VerificationDocumentStatus(val wire: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    companion object {
        fun fromWire(wire: String): VerificationDocumentStatus =
            entries.firstOrNull { it.wire == wire } ?: PENDING
    }
}

/**
 * Where the whole review stands, in the server's words.
 *
 * Never computed from the document list here: the server accounts for expiry too, and an approved
 * licence that has passed its date takes the driver back out of [DriverVerification.canAcceptLoads]
 * without any document's status changing.
 */
@Serializable
enum class VerificationSummary(val wire: String) {
    PENDING("pending"),
    PARTIAL("partial"),
    REJECTED("rejected"),
    APPROVED("approved");

    companion object {
        fun fromWire(wire: String): VerificationSummary = entries.firstOrNull { it.wire == wire } ?: PENDING
    }
}

/**
 * One document in the fleet's file on this driver.
 *
 * [expired] is the field to act on rather than [expiresAt]: an approved document can be expired,
 * and the server has already worked out which.
 */
@Serializable
data class VerificationDocument(
    val id: String,
    val kind: VerificationDocumentKind,
    val status: VerificationDocumentStatus,
    val rejectionReason: String?,
    val expiresAt: String?,
    val expired: Boolean,
)

/**
 * Whether this driver may carry freight for this fleet, and why not.
 *
 * [canAcceptLoads] is the server's own answer and the only thing worth gating on. It is not a
 * summary of the documents: a driver whose paperwork is spotless still cannot work if the fleet
 * has deactivated their driver row or their truck.
 */
@Serializable
data class DriverVerification(
    val status: VerificationSummary,
    val canAcceptLoads: Boolean,
    val documents: List<VerificationDocument> = emptyList(),
) {
    val hasRejection: Boolean get() = documents.any { it.status == VerificationDocumentStatus.REJECTED }

    companion object {
        /**
         * What demo mode and a driver with no fleet get.
         *
         * Permissive on purpose: the gate exists to keep a driver off a board the server would
         * refuse, and where there is no server there is nothing to refuse them.
         */
        val unrestricted = DriverVerification(VerificationSummary.APPROVED, canAcceptLoads = true)
    }
}
