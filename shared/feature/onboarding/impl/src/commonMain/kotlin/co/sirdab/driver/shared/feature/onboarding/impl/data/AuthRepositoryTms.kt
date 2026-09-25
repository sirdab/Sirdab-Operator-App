package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.auth.AuthState
import co.sirdab.driver.shared.core.auth.SecureStore
import co.sirdab.driver.shared.core.auth.SessionManager
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.DriverVerification
import co.sirdab.driver.shared.core.model.Vehicle
import co.sirdab.driver.shared.core.model.VerificationState
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.core.queue.WriteQueue
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverDestination
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileRemote
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The session half of sign-in: the phone, the code, the token, and the way out.
 *
 * Where a verified phone lands is not this class's opinion — it asks the driver's profile, which
 * is the only thing that knows whether they have finished signing up and whether ops has approved
 * them. Reading that profile is also what creates it, so there is no state in which a verified
 * phone has nowhere to go.
 */
class AuthRepositoryTms(
    private val session: SessionManager,
    private val profile: DriverProfileRemote,
    private val onboarding: DriverOnboardingRepository,
    private val api: TmsApiClient,
    /**
     * The outbox, which signing out has to deal with.
     *
     * Here rather than in the screen because only this class knows when the session stops being
     * able to send: the queued writes and the token that authorises them end together.
     */
    private val queue: WriteQueue,
    /**
     * Where the last destination the server gave is kept, for a launch with no signal.
     *
     * The profile itself lives in memory only, so without this a cold start offline has nothing to
     * go on and sends a working driver to the sign-up checklist — away from their trips and the
     * outbox, which is the one part of the app built for exactly that moment.
     */
    private val store: SecureStore,
) : AuthRepository {

    /** The OTP screen verifies with a code alone, so the number has to be held between the steps. */
    private var pendingPhone: String? = null

    /** Filled from `GET /driver/me` once there is a workspace to ask with. */
    private val remote = MutableStateFlow<Driver?>(null)

    /** The same call's other half: whether this fleet will let them work. */
    private val fleetVerification = MutableStateFlow<DriverVerification?>(null)

    override fun observeDriver(): Flow<Driver> =
        combine(session.state, remote, onboarding.profile) { state, fetched, platform ->
            val claims = (state as? AuthState.Ready)?.claims
            // The fleet's answer wins once it arrives, then the driver's own profile, which is the
            // only one a driver with no fleet has. Failing both, the ids in the token are enough to
            // render the shell.
            fetched ?: platform?.toDriver() ?: Driver(
                id = claims?.driverId.orEmpty(),
                fullNameEn = "",
                fullNameAr = "",
                phone = pendingPhone.orEmpty(),
                verification = if (claims != null) {
                    VerificationState.VERIFIED
                } else {
                    VerificationState.UNVERIFIED
                },
            )
        }

    override fun observeHasWorkspace(): Flow<Boolean> = session.state.map { it is AuthState.Ready }

    override fun hasWorkspace(): Boolean = session.state.value is AuthState.Ready

    override fun observeVerification(): Flow<DriverVerification?> = fleetVerification

    /**
     * Both halves of what the app knows about this driver, read again.
     *
     * The same pair [switchWorkspace] re-reads, for the same reason: the platform profile carries
     * what ops has approved and the fleet's own file carries whether they may work, and neither is
     * derivable from the other.
     */
    override suspend fun refreshDriver() {
        onboarding.refresh()
        refreshFleetProfile()
    }

    override fun observeActiveWorkspace(): Flow<String?> =
        session.state.map { (it as? AuthState.Ready)?.claims?.workspaceId }

    override suspend fun switchWorkspace(workspaceId: String): AppResult<Unit> {
        // Everything queued was recorded against the fleet being left, and the token is what scopes
        // it. Sent now it lands; sent after the switch it is a trip the new fleet has never heard
        // of, dropped or parked with nobody told. So: send what can go, and refuse to move while
        // anything is left rather than lose a driver's afternoon.
        runCatching { queue.drain() }
        if (queue.observeCount().first() > 0) {
            return AppResult.Failure(
                AppError(
                    "Some of your work has not been sent yet.",
                    reason = AppErrorReason.UNSENT_WORK,
                ),
            )
        }

        val result = api.post(
            path = "api/v1/me/active-workspace",
            body = api.encode(SwitchWorkspaceDto.serializer(), SwitchWorkspaceDto(workspaceId)),
            deserializer = SwitchWorkspaceResultDto.serializer(),
        )

        return result.fold(
            onSuccess = {
                // The server rotates its own session cookies, which is nothing to a phone holding a
                // bearer token: without this the app would keep calling the old fleet's API with
                // the old claims and wonder why nothing changed.
                session.refresh()
                onboarding.refresh()
                refreshFleetProfile()
                AppResult.Success(Unit)
            },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    override suspend fun resolveDestination(): DriverDestination {
        session.restore()
        if (session.state.value is AuthState.SignedOut) return DriverDestination.SIGN_IN

        return when (val result = readProfile()) {
            is AppResult.Success -> result.data
            // Offline, or the server having a bad day. The session is real, so the last answer we
            // hold beats bouncing the driver to the phone screen for a code they already gave.
            is AppResult.Failure -> onboarding.profile.value?.destination()
                ?: lastDestination()
                ?: DriverDestination.ONBOARDING
        }
    }

    override suspend fun requestOtp(phone: String): AppResult<Unit> {
        pendingPhone = phone
        return session.requestOtp(phone).fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    override suspend fun verifyOtp(code: String): AppResult<DriverDestination> {
        val phone = pendingPhone
            ?: return AppResult.Failure(
                AppError("Enter your phone number again.", reason = AppErrorReason.PHONE_MISSING),
            )

        return session.verifyOtp(phone, code).fold(
            onSuccess = { state ->
                // A new sign-in. Whatever is still held from whoever used this phone last must not
                // stand in for this person while their own profile loads.
                forgetDriver()
                when (state) {
                    // Signed in against a workspace that is not a driver's. The driver app is the
                    // wrong app, and no amount of onboarding will change that. The session goes
                    // too: kept, the next launch would restore it and walk straight past this.
                    is AuthState.NotADriver -> {
                        session.signOut()
                        AppResult.Failure(
                            AppError(
                                "This account is not a driver account.",
                                reason = AppErrorReason.NOT_A_DRIVER,
                            ),
                        )
                    }
                    AuthState.SignedOut -> {
                        session.signOut()
                        AppResult.Failure(
                            AppError("Sign-in failed.", reason = AppErrorReason.SIGN_IN_FAILED),
                        )
                    }
                    // Ready and AwaitingLink ask the same question of the same endpoint: the
                    // profile says where this person belongs, the claims do not.
                    else -> readProfile()
                }
            },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }

    /**
     * Read the profile — creating it, if this is the first time — and say where it points.
     *
     * A suspension arrives as an error from the read rather than as a status on it, so it is
     * turned back into a destination here: it is a screen to show, not a failure to retry.
     */
    private suspend fun readProfile(): AppResult<DriverDestination> {
        val read: AppResult<DriverDestination> = when (val result = onboarding.refresh()) {
            is AppResult.Success -> {
                refreshFleetProfile()
                AppResult.Success(result.data.destination())
            }
            is AppResult.Failure -> when (result.error.reason) {
                AppErrorReason.DRIVER_SUSPENDED -> AppResult.Success(DriverDestination.BLOCKED)
                else -> result
            }
        }
        if (read is AppResult.Success) {
            runCatching { store.put(LAST_DESTINATION_KEY, read.data.name) }
        }
        return read
    }

    /**
     * Only destinations a server read can produce are trusted. SIGN_IN is never one of them: a
     * session that exists is the reason this is being asked.
     */
    private suspend fun lastDestination(): DriverDestination? {
        val stored = runCatching { store.get(LAST_DESTINATION_KEY) }.getOrNull() ?: return null
        return DriverDestination.entries
            .firstOrNull { it.name == stored }
            ?.takeIf { it != DriverDestination.SIGN_IN }
    }

    /**
     * Best effort: a missing name is a worse screen, not a broken one.
     *
     * Skipped entirely for a driver no fleet has taken on. `GET /driver/me` is workspace-scoped and
     * answers `403 no_active_workspace` to them, and the profile — read moments ago — is a better
     * authority on that than the token, whose workspace claim outlives a fleet that dropped them.
     */
    private suspend fun refreshFleetProfile() {
        if (session.state.value !is AuthState.Ready) return
        if (onboarding.profile.value?.hasWorkspace == false) return
        (profile.fetch() as? AppResult.Success)?.let { result ->
            remote.value = result.data.driver
            fleetVerification.value = result.data.verification
        }
    }

    override fun observeUnsentWrites(): Flow<Int> = queue.observeCount()

    override suspend fun signOut() {
        // Best effort and last chance: in signal this empties the outbox, and out of it the rows
        // are lost either way, because the only session that could have sent them ends here.
        runCatching { queue.drain() }
        queue.clear()
        session.signOut()
        runCatching { store.remove(LAST_DESTINATION_KEY) }

        // Everything read under the old session goes with it. Leaving the name and the truck on
        // screen while the next driver signs in is how one driver's shift gets recorded as
        // another's.
        forgetDriver()
        pendingPhone = null
    }

    private fun forgetDriver() {
        remote.value = null
        fleetVerification.value = null
        onboarding.clear()
    }
}

private const val LAST_DESTINATION_KEY = "driver.last_destination"

/**
 * The driver's own profile, as the rest of the app models a driver.
 *
 * `pending_verification` is a real state, not a failure: the account works and the documents are
 * with ops. The first truck is the one shown, as everywhere else in the app.
 */
internal fun DriverProfile.toDriver(): Driver = Driver(
    id = userId,
    // The server keeps one name, not the two the demo world carries.
    fullNameEn = name,
    fullNameAr = name,
    phone = phone.orEmpty(),
    verification = when (status) {
        DriverProfileStatus.ACTIVE -> VerificationState.VERIFIED
        DriverProfileStatus.PENDING_VERIFICATION -> VerificationState.PENDING
        DriverProfileStatus.SUSPENDED -> VerificationState.UNVERIFIED
    },
    vehicle = trucks.firstOrNull()?.let { truck ->
        Vehicle(
            truckType = truck.truckType,
            truckSize = truck.truckSize,
            plate = truck.licencePlate,
            // The contract carries kilograms; the app has always shown tonnes.
            capacityTons = truck.capacityKg?.let { it / 1000.0 } ?: 0.0,
        )
    },
)

private fun Throwable.toAppError(): AppError =
    apiFailure?.toAppError() ?: AppError(message ?: "Something went wrong.")
