package co.sirdab.driver.shared.core.network

/**
 * The seam between the API client and whoever owns the session. The auth module implements it; the
 * network module must not know how a token is obtained or stored.
 *
 * [accessToken] is read immediately before every request rather than cached by the caller, so a
 * refresh that happened in the meantime is picked up.
 */
interface TokenProvider {
    suspend fun accessToken(): String?

    /** Returns true when a fresh token is now available. A single retry follows a true. */
    suspend fun refresh(): Boolean

    /**
     * A stable id for the person the current token belongs to (the JWT `sub`), or null with no
     * session. The write queue stamps it on every row so one driver's unsent work can never be
     * sent under the next driver's token.
     */
    fun ownerId(): String? = null

    companion object {
        /** For demo mode and for tests that never reach an authenticated endpoint. */
        val Anonymous = object : TokenProvider {
            override suspend fun accessToken(): String? = null
            override suspend fun refresh(): Boolean = false
        }
    }
}
