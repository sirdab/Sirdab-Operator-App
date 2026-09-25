package co.sirdab.driver.shared.feature.onboarding.api.domain

import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType

/**
 * Where the driver stands with us, which is ours to decide, not theirs.
 *
 * Kept apart from [DriverProfile.onboardingCompletedAt] deliberately: that one says the driver has
 * handed over everything asked of them, this one says ops has looked at it.
 */
enum class DriverProfileStatus(val wire: String) {
    PENDING_VERIFICATION("pending_verification"),
    ACTIVE("active"),
    SUSPENDED("suspended");

    companion object {
        fun fromWire(wire: String): DriverProfileStatus =
            entries.firstOrNull { it.wire == wire } ?: PENDING_VERIFICATION
    }
}

/**
 * One thing onboarding still needs, as the server reports it in `missing`.
 *
 * Parsed into a type rather than matched as strings at the call site, because one of them carries
 * an id: a vehicle registration belongs to a particular truck, and a driver with two trucks can be
 * missing one and not the other.
 */
sealed interface OnboardingGap {
    data object Name : OnboardingGap
    data object Licence : OnboardingGap
    data object Truck : OnboardingGap

    /** Proof of who they are: a national id or an iqama, whichever they carry. */
    data object Identity : OnboardingGap
    data object DrivingLicence : OnboardingGap
    data class VehicleRegistration(val truckId: String) : OnboardingGap

    /**
     * Something this build has never heard of.
     *
     * The server may start asking for more than the app knows how to collect, and the honest answer
     * to that is to say so rather than to quietly report onboarding complete. Never silently
     * dropped.
     */
    data class Unsupported(val wire: String) : OnboardingGap

    companion object {
        fun fromWire(wire: String): OnboardingGap = when {
            wire == "name" -> Name
            wire == "licence" -> Licence
            wire == "truck" -> Truck
            wire == "identity" -> Identity
            wire == "driving_licence" -> DrivingLicence
            wire.startsWith(VEHICLE_REGISTRATION_PREFIX) ->
                VehicleRegistration(wire.removePrefix(VEHICLE_REGISTRATION_PREFIX))
            else -> Unsupported(wire)
        }

        private const val VEHICLE_REGISTRATION_PREFIX = "vehicle_registration:"
    }
}

/** A truck on the driver's own profile, which exists with or without a fleet. */
data class ProfileTruck(
    val id: String,
    val licencePlate: String,
    val truckType: TruckType,
    val truckSize: TruckSize,
    val capacityKg: Int?,
)

/**
 * A document the driver has submitted.
 *
 * [rejectionReason] is ops' own words and is shown verbatim: it is the only thing that tells a
 * driver what to photograph differently.
 */
data class ProfileDocument(
    val id: String,
    val kind: DriverDocumentKind,
    val truckId: String?,
    val status: DriverDocumentStatus,
    val rejectionReason: String?,
    val uploadedAt: String?,
    val downloadUrl: String?,
)

/** A fleet that has this driver on its roster. */
data class ProfileWorkspace(
    val workspaceId: String,
    val workspaceName: String,
    val driverId: String,
    val carrierId: String,
)

/**
 * The driver as the platform knows them, outside any fleet.
 *
 * This one object answers every question sign-in has: who they are, what is still missing, whether
 * ops has approved them, and which fleets have taken them on. Reading it is also what creates it,
 * so a verified phone that has never opened the app gets a profile from the same call.
 */
data class DriverProfile(
    val userId: String,
    val name: String,
    val phone: String?,
    val nationality: Nationality?,
    val licenceNumber: String?,
    val licenceExpiresAt: String?,
    val status: DriverProfileStatus,
    /** Set once, the first time [missing] empties, and never cleared. */
    val onboardingCompletedAt: String?,
    val missing: List<OnboardingGap>,
    val trucks: List<ProfileTruck>,
    val documents: List<ProfileDocument>,
    val workspaces: List<ProfileWorkspace>,
) {
    /** True once the driver has handed over everything, whatever ops has done with it since. */
    val onboardingComplete: Boolean get() = onboardingCompletedAt != null

    val hasWorkspace: Boolean get() = workspaces.isNotEmpty()

    /** The document currently filling a slot, or null when nothing has been uploaded for it. */
    fun documentFor(kind: DriverDocumentKind, truckId: String? = null): ProfileDocument? =
        documents.firstOrNull { it.kind == kind && it.truckId == truckId }

    fun isMissing(gap: OnboardingGap): Boolean = gap in missing

    /** True while ops has still to look at a profile that has everything it asked for. */
    val isUnderReview: Boolean
        get() = status == DriverProfileStatus.PENDING_VERIFICATION && missing.isEmpty()

    /**
     * Where this profile says the driver belongs.
     *
     * Waiting on ops is not a locked door. A driver whose paperwork is all in can open the app and
     * use everything their fleet allows — the contract is explicit that `canAcceptLoads` is the
     * fleet's decision and a `pending_verification` platform profile does not block it. Review is
     * something the app tells them about, on a banner they can open, not somewhere it parks them.
     *
     * Only two things hold them out: ops taking them off the platform, and the server still asking
     * for something, which is one screen away from being handed over.
     */
    fun destination(): DriverDestination = when {
        status == DriverProfileStatus.SUSPENDED -> DriverDestination.BLOCKED
        // A document ops sent back reopens its own item, so this is also how a rejection lands the
        // driver on the one screen that can replace it.
        missing.isNotEmpty() -> DriverDestination.ONBOARDING
        else -> DriverDestination.MAIN
    }
}

/** Which of the app's worlds this driver is in right now. */
enum class DriverDestination {
    /** No session: the phone screen. */
    SIGN_IN,

    /** Signed in, and onboarding still wants something. */
    ONBOARDING,

    /** The tabs, whether or not ops has approved the profile yet. */
    MAIN,

    /** Suspended by ops. Nothing to do in the app but read why. */
    BLOCKED,
}
