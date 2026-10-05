package co.sirdab.driver.shared.core.network

/**
 * Where the app points. The TMS API and Supabase Auth are two different hosts: the driver signs in
 * against Supabase and then calls the TMS with the token Supabase issued. The values come from the
 * `driver.*` properties in gradle.properties.
 */
data class TmsEnvironment(
    val apiBaseUrl: String,
    val supabaseUrl: String,
    val supabaseAnonKey: String,
)
