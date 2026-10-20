package co.sirdab.driver.shared.feature.onboarding.api.domain

import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.Nationality
import co.sirdab.driver.shared.core.model.EquipmentBody
import co.sirdab.driver.shared.core.model.EquipmentSize
import co.sirdab.driver.shared.core.model.EquipmentTemperature
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

/**
 * A truck type the driver may give a truck: one the workspaces they drive for run, or Sirdab B2B's
 * for a driver in none yet.
 *
 * The server refuses any other combination of the three axes, so the truck form offers these and
 * nothing else. There is no id: a truck is written in its axes, and those go back as they came.
 */
data class TruckType(
    /** The workspace's own name for it. */
    val name: String,
    val nameAr: String?,
    val bodyType: EquipmentBody,
    val sizeClass: EquipmentSize,
    val temperature: EquipmentTemperature,
) {
    /** The Arabic name in Arabic when the workspace gave one, and [name] everywhere else. */
    fun displayName(language: String): String =
        nameAr?.takeIf { language == "ar" && it.isNotBlank() } ?: name
}

/** What the driver says about the truck they drive. */
data class TruckDraft(
    val licencePlate: String = "",
    /** One of [DriverOnboardingRepository.truckTypes], and none until the driver picks. */
    val type: TruckType? = null,
    val capacityTons: String = "",
) {
    /**
     * A plate and a type. No type is preselected: the list is the workspace's, and a default the
     * driver scrolled past is a truck registered as something it is not.
     */
    val canSubmit: Boolean get() = licencePlate.isNotBlank() && type != null

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

    /**
     * `GET /api/driver/equipment-types`: the truck types this driver may pick. Empty when none of
     * their workspaces runs a type a truck can be written as.
     */
    suspend fun truckTypes(): AppResult<List<TruckType>>

    /**
     * `POST /api/driver/profile/trucks`. Re-adding a plate updates that truck rather than doubling it.
     * A type outside [truckTypes] fails with [co.sirdab.driver.shared.core.model.AppErrorReason.EQUIPMENT_NOT_OFFERED].
     */
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
