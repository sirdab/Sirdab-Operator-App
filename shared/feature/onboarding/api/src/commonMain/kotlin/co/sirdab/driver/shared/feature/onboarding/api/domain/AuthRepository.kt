package co.sirdab.driver.shared.feature.onboarding.api.domain

import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import kotlinx.coroutines.flow.Flow

/** Where the app should open on cold start. */
enum class StartDestination { ONBOARDING, MAIN }

/**
 * Onboarding + session contract. Demo mode backs it with local simulated data; HTTP could back it
 * later without the UI changing.
 */
/**
 * Who the signed-in driver is, from the server.
 *
 * A JWT carries ids, not people, so without this the profile screen has no name
 * to show. Separate from [AuthRepository] because it is a read of the driver
 * record rather than anything to do with the session.
 */
interface DriverProfileRemote {
    suspend fun fetch(): AppResult<Driver>
}

interface AuthRepository {
    fun observeDriver(): Flow<Driver>

    /** True once the persisted world exists and has been read. */
    suspend fun ensureReady()

    fun resolveStartDestination(): StartDestination

    suspend fun requestOtp(phone: String): AppResult<Unit>

    /**
     * Verify the code, and link this phone to whatever a dispatcher set up for it.
     *
     * The linking is not a separate step the caller has to remember: a driver never signs
     * themselves up, so the only thing standing between a verified phone and a working session is
     * a call the app can make on their behalf. It fails only when no dispatcher ever added the
     * number, which is the one case worth telling them about.
     */
    suspend fun verifyOtp(code: String): AppResult<Unit>

}
