package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.auth.AuthState
import co.sirdab.driver.shared.core.auth.SessionManager
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.VerificationState
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.feature.onboarding.api.domain.AuthRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileRemote
import co.sirdab.driver.shared.feature.onboarding.api.domain.StartDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Sign-in against the real stack: Supabase issues the token, the TMS reads the membership out of
 * it.
 *
 * A driver never signs themselves up. A dispatcher creates the driver row against a phone number,
 * and the first time that phone verifies here the app asks the server to link the two. From the
 * driver's side there is one step, entering a code, and either they are in or their number was
 * never added.
 */
class AuthRepositoryTms(
    private val session: SessionManager,
    private val profile: DriverProfileRemote,
    private val api: TmsApiClient,
) : AuthRepository {

    /** The OTP screen verifies with a code alone, so the number has to be held between the steps. */
    private var pendingPhone: String? = null

    /** Filled from `GET /driver/me` once there is a session to ask with. */
    private val remote = MutableStateFlow<Driver?>(null)

    override fun observeDriver(): Flow<Driver> =
        combine(session.state, remote) { state, fetched ->
            val claims = (state as? AuthState.Ready)?.claims
            // The server's answer wins once it arrives; until then only the ids
            // in the token are known, which is enough to render the shell.
            fetched ?: Driver(
                // The driver's own row id, which is what the API scopes trips by.
                id = claims?.scopeId.orEmpty(),
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

    override suspend fun ensureReady() {
        session.restore()
        refreshProfile()
    }

    /** Best effort: a missing name is a worse screen, not a broken one. */
    private suspend fun refreshProfile() {
        if (session.state.value !is AuthState.Ready) return
        (profile.fetch() as? AppResult.Success)?.let { remote.value = it.data }
    }

    override fun resolveStartDestination(): StartDestination =
        if (session.state.value is AuthState.Ready) StartDestination.MAIN else StartDestination.ONBOARDING

    override suspend fun requestOtp(phone: String): AppResult<Unit> {
        pendingPhone = phone
        return session.requestOtp(phone).toAppResult { }
    }

    override suspend fun verifyOtp(code: String): AppResult<Unit> {
        val phone = pendingPhone
            ?: return AppResult.Failure(
                AppError("Enter your phone number again.", reason = AppErrorReason.PHONE_MISSING),
            )

        return session.verifyOtp(phone, code).fold(
            onSuccess = { state ->
                when (state) {
                    is AuthState.Ready -> {
                        refreshProfile()
                        AppResult.Success(Unit)
                    }
                    // The code was right and Supabase signed them in, but the
                    // token names no workspace. One call fixes that, if a
                    // dispatcher added this number.
                    is AuthState.AwaitingLink -> linkWorkspace()
                    is AuthState.NotADriver -> AppResult.Failure(
                        AppError(
                            "This account is not a driver account.",
                            reason = AppErrorReason.NOT_A_DRIVER,
                        ),
                    )
                    AuthState.SignedOut -> AppResult.Failure(
                        AppError("Sign-in failed.", reason = AppErrorReason.SIGN_IN_FAILED),
                    )
                }
            },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }


    /**
     * Ask the server to find the driver rows that carry this phone.
     *
     * The request has no body at all: the phone comes from the verified session, so there is
     * nothing here a caller could forge. On success the membership exists but the token in hand
     * predates it, which is why the session is refreshed before anyone asks it a question.
     */
    private suspend fun linkWorkspace(): AppResult<Unit> {
        val result = api.post(
            path = "api/driver/onboarding",
            body = "{}",
            deserializer = DriverOnboardingResultDto.serializer(),
        )

        return result.fold(
            onSuccess = {
                session.refresh()
                refreshProfile()
                if (session.state.value is AuthState.Ready) {
                    AppResult.Success(Unit)
                } else {
                    // Linked to something the driver app cannot use, which is a
                    // different problem from never having been added.
                    AppResult.Failure(
                        AppError(
                            "This account is not a driver account.",
                            reason = AppErrorReason.NOT_A_DRIVER,
                        ),
                    )
                }
            },
            onFailure = {
                AppResult.Failure(
                    AppError(
                        "Ask your dispatcher to add your number.",
                        reason = AppErrorReason.NOT_PROVISIONED,
                    ),
                )
            },
        )
    }

    private fun <T, R> Result<T>.toAppResult(transform: (T) -> R): AppResult<R> = fold(
        onSuccess = { AppResult.Success(transform(it)) },
        onFailure = { AppResult.Failure(it.toAppError()) },
    )
}

private fun Throwable.toAppError(): AppError =
    apiFailure?.toAppError() ?: AppError(message ?: "Something went wrong.")
