package co.sirdab.driver.shared.feature.onboarding.api.domain

import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import kotlinx.coroutines.flow.StateFlow

/** What the driver types about themselves. Every field here is one PATCH away from the server. */
data class DetailsDraft(
    val name: String = "",
    val nationality: Nationality? = null,
    val licenceNumber: String = "",
    /** ISO `yyyy-MM-dd`, the only form the contract's date fields take. */
    val licenceExpiresAt: String = "",
) {
    /**
     * Nationality and expiry are optional to the contract and required here: a dispatcher checking
     * paperwork needs both, and a blank one costs a phone call later to ask what could have been
     * asked now.
     */
    val canSubmit: Boolean
        get() = name.isNotBlank() &&
            nationality != null &&
            licenceNumber.isNotBlank() &&
            isIsoDate(licenceExpiresAt)
}

/** What the driver says about the truck they drive. */
data class TruckDraft(
    val licencePlate: String = "",
    val truckType: TruckType = TruckType.DRY,
    val truckSize: TruckSize = TruckSize.CLOSED_LORRY,
    val capacityTons: String = "",
) {
    /** The contract wants a plate; everything else it will take a default for. */
    val canSubmit: Boolean get() = licencePlate.isNotBlank()

    /** The app has always talked in tonnes; the contract carries kilograms. */
    val capacityKg: Int? get() = capacityTons.trim().toDoubleOrNull()?.let { (it * 1000).toInt() }
}

/**
 * The driver's own profile, and everything sign-up does to it.
 *
 * Every call here answers with the whole profile rather than a fragment, because the server
 * recomputes what is still missing on every write and the app should never be guessing at that.
 * The one exception is adding a truck, which the contract answers with the truck alone; this
 * re-reads so callers see the same shape regardless.
 */
interface DriverOnboardingRepository {

    /** The last profile read, for screens that render it. Null until the first [refresh]. */
    val profile: StateFlow<DriverProfile?>

    /**
     * `GET /api/driver/profile`: read the profile, creating it if this phone has never had one.
     *
     * This is sign-up. There is no separate registration call any more, and no state in which a
     * verified phone has no profile, so the first thing after a code is accepted is this.
     */
    suspend fun refresh(): AppResult<DriverProfile>

    /**
     * Forget the cached profile. Called when the session ends, so the next person to sign in on
     * this phone never sees the last driver's name, phone or truck while their own loads.
     */
    fun clear()

    /** `PATCH /api/driver/profile`. Partial by design: send only what the driver just filled in. */
    suspend fun saveDetails(draft: DetailsDraft): AppResult<DriverProfile>

    /** `POST /api/driver/profile/trucks`. Re-adding a plate updates that truck rather than doubling it. */
    suspend fun addTruck(draft: TruckDraft): AppResult<DriverProfile>

    /** `DELETE /api/driver/profile/trucks/:id`. The server refuses the last one. */
    suspend fun removeTruck(truckId: String): AppResult<DriverProfile>

    /**
     * Mint, upload, confirm: one photograph becomes one document.
     *
     * [truckId] is required for a vehicle registration and refused for the other kinds. Sending a
     * replacement for a slot that is already filled supersedes it, which is how a driver answers a
     * rejection.
     */
    suspend fun uploadDocument(
        kind: DriverDocumentKind,
        truckId: String?,
        bytes: ByteArray,
        contentType: String,
    ): AppResult<DriverProfile>
}
