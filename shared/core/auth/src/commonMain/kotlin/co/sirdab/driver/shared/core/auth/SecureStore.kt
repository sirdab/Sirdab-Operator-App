package co.sirdab.driver.shared.core.auth

/**
 * Where the session lives between launches.
 *
 * Deliberately not the app's DataStore. A refresh token is a long-lived bearer credential for a
 * driver's whole account; on a rooted or jailbroken phone, plain preferences are readable. Android
 * backs this with a Keystore key, iOS with the Keychain.
 */
interface SecureStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}
