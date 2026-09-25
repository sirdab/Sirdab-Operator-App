package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.demoLatency
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.DriverVerification
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Sign-in with no server, walking the same screens as the real thing.
 *
 * It defers to the demo's own onboarding profile rather than short-circuiting to the tabs: the
 * checklist, the document steps and the review screen are most of this flow, and a demo that
 * skipped them could not be used to review them.
 */
class AuthRepositoryMock(
    private val world: DemoWorld,
    private val onboarding: DriverOnboardingRepository,
) : AuthRepository {

    override fun observeDriver(): Flow<Driver> = world.state.map { it.driver }

    /** The demo driver is always somebody's driver, so there is always a board to show. */
    override fun observeHasWorkspace(): Flow<Boolean> = flowOf(true)

    override fun hasWorkspace(): Boolean = true

    /** The demo world is held in memory and is never behind, so there is nothing to fetch. */
    override suspend fun refreshDriver() = Unit

    /** No ops to wait for in the demo, so nothing is withheld. */
    override fun observeVerification(): Flow<DriverVerification?> =
        flowOf(DriverVerification.unrestricted)

    /** One world, one fleet: there is nothing to switch between. */
    override fun observeActiveWorkspace(): Flow<String?> = flowOf(null)

    override suspend fun switchWorkspace(workspaceId: String): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun resolveDestination(): DriverDestination {
        world.ensureLoaded()
        // A demo world that has been through onboarding stays through it, the way a real session
        // would; anything else starts at the phone.
        return if (world.state.value.onboardingComplete) DriverDestination.MAIN else DriverDestination.SIGN_IN
    }

    override suspend fun requestOtp(phone: String): AppResult<Unit> {
        demoLatency()
        world.setDriverPhone(phone)
        return AppResult.Success(Unit)
    }

    /**
     * Demo: any 6 digits pass, and then the profile decides, exactly as it does against a server.
     *
     * A fresh demo world has nothing on file, so this lands on the checklist; one that has already
     * been through it goes to the tabs.
     */
    override suspend fun verifyOtp(code: String): AppResult<DriverDestination> {
        demoLatency()
        return when (val profile = onboarding.refresh()) {
            is AppResult.Success -> AppResult.Success(profile.data.destination())
            is AppResult.Failure -> profile
        }
    }

    /** Nothing is queued in the demo: every write lands in the world as it is made. */
    override fun observeUnsentWrites(): Flow<Int> = flowOf(0)

    /** Back to the start: the demo world is the session, so resetting it is signing out. */
    override suspend fun signOut() = world.reset()
}
