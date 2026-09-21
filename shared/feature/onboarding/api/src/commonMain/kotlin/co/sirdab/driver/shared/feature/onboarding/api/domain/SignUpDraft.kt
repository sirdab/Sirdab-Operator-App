package co.sirdab.driver.shared.feature.onboarding.api.domain

import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType

/**
 * What a driver tells us about themselves when they sign up.
 *
 * Sign-up mints a platform profile that belongs to the driver rather than to
 * any one workspace, so none of this is a dispatcher's to fill in. It is also
 * an upsert: coming back through with a corrected plate updates the same
 * profile instead of creating a second one.
 *
 * No documents and no invite code. Both were required by earlier versions of
 * this endpoint and neither is now: paperwork is filed separately once a
 * dispatcher has taken the driver on.
 */
data class SignUpDraft(
    val name: String = "",
    val nationality: String = "",
    val licenceNumber: String = "",
    /** ISO `yyyy-MM-dd`, which is the only form the contract's date fields take. */
    val licenceExpiresAt: String = "",
    val truckType: TruckType = TruckType.DRY,
    val truckSize: TruckSize = TruckSize.CLOSED_LORRY,
    val licencePlate: String = "",
    val capacityTons: String = "",
) {
    /** Empty is fine; half a date is not, and the server would refuse the whole sign-up for it. */
    val expiryValid: Boolean
        get() = licenceExpiresAt.isBlank() || isIsoDate(licenceExpiresAt)

    val detailsComplete: Boolean
        get() = name.isNotBlank() && licenceNumber.isNotBlank() && expiryValid

    /** The contract wants at least one truck, and a truck is its plate. */
    val truckComplete: Boolean get() = licencePlate.isNotBlank()

    val canSubmit: Boolean get() = detailsComplete && truckComplete
}

/** A date the contract will accept, or nothing at all. */
fun isIsoDate(value: String): Boolean =
    value.length == 10 &&
        value[4] == '-' && value[7] == '-' &&
        value.filterIndexed { index, _ -> index != 4 && index != 7 }.all { it.isDigit() }
