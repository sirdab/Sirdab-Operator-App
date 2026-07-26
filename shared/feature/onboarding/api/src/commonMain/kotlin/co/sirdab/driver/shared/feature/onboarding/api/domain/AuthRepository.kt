package co.sirdab.driver.shared.feature.onboarding.api.domain

import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.VehicleType
import kotlinx.coroutines.flow.Flow

/** Where the app should open on cold start. */
enum class StartDestination { ONBOARDING, MAIN }

/**
 * Onboarding + session contract. Demo mode backs it with local simulated data; HTTP could back it
 * later without the UI changing.
 */
interface AuthRepository {
    fun observeDriver(): Flow<Driver>

    /** True once the persisted world exists and has been read. */
    suspend fun ensureReady()

    fun resolveStartDestination(): StartDestination

    suspend fun requestOtp(phone: String): AppResult<Unit>
    suspend fun verifyOtp(code: String): AppResult<Unit>

    /** Returns the Nafath-verified full name (localized display handled by the caller). */
    suspend fun startNafath(): AppResult<String>

    suspend fun submitVehicle(type: VehicleType, plate: String, capacityTons: Double): AppResult<Unit>
}
