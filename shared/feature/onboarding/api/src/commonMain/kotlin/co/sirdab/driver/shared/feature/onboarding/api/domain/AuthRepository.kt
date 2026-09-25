package co.sirdab.driver.shared.feature.onboarding.api.domain

import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.DriverVerification
import kotlinx.coroutines.flow.Flow

/**
 * Who is signed in, and how they got there.
 *
 * Sign-up itself lives in [DriverOnboardingRepository]: this one owns the phone, the code, the
 * token and the way out. The two meet at [resolveDestination], because where a verified phone
 * belongs is a question only the driver's profile can answer.
 */
/**
 * Who the signed-in driver is, from the server.
 *
 * A JWT carries ids, not people, so without this the profile screen has no name
 * to show. Separate from [AuthRepository] because it is a read of the driver
 * record rather than anything to do with the session.
 */
interface DriverProfileRemote {
    suspend fun fetch(): AppResult<FleetDriver>
}

/** The driver as one fleet sees them: who they are, and whether they may work there. */
data class FleetDriver(val driver: Driver, val verification: DriverVerification)

interface AuthRepository {
    fun observeDriver(): Flow<Driver>

    /**
     * Whether the session carries a workspace, which is what the driver endpoints scope by.
     *
     * False for a driver no fleet has taken on yet: the account works, the profile is theirs, and
     * there is no board to show them. Every workspace-scoped call would answer `403`, so the app
     * does not make them.
     */
    fun observeHasWorkspace(): Flow<Boolean>

    /**
     * The same fact, now, for a caller that cannot wait a frame for it.
     *
     * The shell decides which tabs exist as it first composes, and a flow's first value arrives
     * after that, so reading it asynchronously would build the board for a driver who has none and
     * then take it away again.
     */
    fun hasWorkspace(): Boolean

    /**
     * The fleet's verdict on this driver: whether they may carry freight, and where their
     * paperwork stands.
     *
     * Null until a fleet has been asked, which is every driver who holds no workspace. The server
     * owns this answer — the app renders it and gates on it, and never works it out from the
     * document list.
     */
    fun observeVerification(): Flow<DriverVerification?>

    /**
     * The fleet the session is stamped into, of the several a driver may belong to.
     *
     * Read off the token rather than the profile: the profile lists every fleet that has this
     * driver on its roster, but only one of them is the one the API answers for right now.
     */
    fun observeActiveWorkspace(): Flow<String?>

    /**
     * Work in a different fleet from now on.
     *
     * The token is what scopes every driver route, so switching is not a local preference: the
     * server moves the session and the app trades its token in for one carrying the new claims.
     * Everything read under the old fleet — the profile, the verdict, the trips — is re-read.
     */
    suspend fun switchWorkspace(workspaceId: String): AppResult<Unit>

    /**
     * Restore the session and work out where this launch opens.
     *
     * Reads the profile when there is a session to read it with, so a cold start lands on the same
     * screen the driver left rather than on the phone entry every time.
     */
    suspend fun resolveDestination(): DriverDestination

    suspend fun requestOtp(phone: String): AppResult<Unit>

    /**
     * Verify the code and find out where the driver belongs.
     *
     * The profile read that follows is also what creates the profile, so a number nobody has ever
     * seen is not an error here: it is a driver with everything still to fill in.
     */
    suspend fun verifyOtp(code: String): AppResult<DriverDestination>

    /**
     * Ask the server again about this driver: their platform profile, and their fleet's verdict.
     *
     * Both halves, because the profile screen shows both and a driver pulling it down cannot know
     * which one has moved — ops approving a document and a fleet clearing them to work are two
     * different people in two different consoles.
     */
    suspend fun refreshDriver()

    /**
     * Work the driver recorded that has not reached the server yet.
     *
     * Signing out is the one moment this concerns anybody but the sync itself: the session that
     * could send these is about to be thrown away, so the driver gets told what they are about to
     * lose while they can still change their mind.
     */
    fun observeUnsentWrites(): Flow<Int>

    /**
     * End the session, and leave nothing of this driver on the phone.
     *
     * One last attempt to send the outbox first, because a driver back in signal should lose
     * nothing; then the tokens, the cached profile and whatever is still queued all go. Phones are
     * handed between drivers, and the next one's session must not send the last one's work.
     */
    suspend fun signOut()
}
