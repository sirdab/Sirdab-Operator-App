package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.demoLatency
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.Vehicle
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.StartDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepositoryMock(private val world: DemoWorld) : AuthRepository {

    /** Verified name returned by the mock Nafath handoff, kept so vehicle-submit can finalize. */
    private var pendingVerifiedNameEn: String = "Faisal Al-Otaibi"
    private var pendingVerifiedNameAr: String = "فيصل العتيبي"

    override fun observeDriver(): Flow<Driver> = world.state.map { it.driver }

    override suspend fun ensureReady() = world.ensureLoaded()

    override fun resolveStartDestination(): StartDestination =
        if (world.state.value.onboardingComplete) StartDestination.MAIN else StartDestination.ONBOARDING

    override suspend fun requestOtp(phone: String): AppResult<Unit> {
        demoLatency()
        world.setDriverPhone(phone)
        return AppResult.Success(Unit)
    }

    override suspend fun verifyOtp(code: String): AppResult<Unit> {
        demoLatency()
        // Demo: any 6 digits pass.
        return AppResult.Success(Unit)
    }

    override suspend fun startNafath(): AppResult<String> {
        demoLatency()
        return AppResult.Success(pendingVerifiedNameEn)
    }

    override suspend fun submitVehicle(type: VehicleType, plate: String, capacityTons: Double): AppResult<Unit> {
        demoLatency()
        world.completeOnboarding(
            fullNameEn = pendingVerifiedNameEn,
            fullNameAr = pendingVerifiedNameAr,
            vehicle = Vehicle(type = type, plate = plate, capacityTons = capacityTons),
        )
        return AppResult.Success(Unit)
    }
}
