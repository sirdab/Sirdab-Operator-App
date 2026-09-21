package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.demoLatency
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.Vehicle
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.StartDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepositoryMock(private val world: DemoWorld) : AuthRepository {

    /** Stand-in name the demo world completes onboarding with. */
    private val pendingVerifiedNameEn: String = "Faisal Al-Otaibi"
    private val pendingVerifiedNameAr: String = "فيصل العتيبي"

    override fun observeDriver(): Flow<Driver> = world.state.map { it.driver }

    override suspend fun ensureReady() = world.ensureLoaded()

    override fun resolveStartDestination(): StartDestination =
        if (world.state.value.onboardingComplete) StartDestination.MAIN else StartDestination.ONBOARDING

    override suspend fun requestOtp(phone: String): AppResult<Unit> {
        demoLatency()
        world.setDriverPhone(phone)
        return AppResult.Success(Unit)
    }

    /**
     * Demo: any 6 digits pass, and verifying is the whole of onboarding.
     *
     * The same shape as the real thing, where a verified phone is either
     * already a driver somewhere or is not one at all. The demo world's driver
     * always is.
     */
    override suspend fun verifyOtp(code: String): AppResult<Unit> {
        demoLatency()
        world.completeOnboarding(
            fullNameEn = pendingVerifiedNameEn,
            fullNameAr = pendingVerifiedNameAr,
            // The demo driver already has a truck in the fixtures; keeping it is
            // truer to the real flow, where the dispatcher recorded it.
            vehicle = world.state.value.driver.vehicle
                ?: Vehicle(TruckType.DRY, TruckSize.CLOSED_LORRY, plate = "", capacityTons = 0.0),
        )
        return AppResult.Success(Unit)
    }
}
