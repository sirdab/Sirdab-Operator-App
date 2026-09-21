package co.sirdab.driver.shared.core.network

/**
 * Where the app points. The TMS API and Supabase Auth are two different hosts: the driver signs in
 * against Supabase and then calls the TMS with the token Supabase issued.
 *
 * Cloud values are still to be provided by the API side, so only [Local] is real today.
 */
data class TmsEnvironment(
    val apiBaseUrl: String,
    val supabaseUrl: String,
    val supabaseAnonKey: String,
) {
    companion object {
        /**
         * `supabase status -o env` prints the anon key for a local stack. It is not a secret: it is
         * the publishable key every client ships with, and RLS is what actually guards the data.
         */
        fun local(supabaseAnonKey: String) = TmsEnvironment(
            apiBaseUrl = "http://127.0.0.1:4400",
            supabaseUrl = "http://127.0.0.1:54321",
            supabaseAnonKey = supabaseAnonKey,
        )
    }
}
