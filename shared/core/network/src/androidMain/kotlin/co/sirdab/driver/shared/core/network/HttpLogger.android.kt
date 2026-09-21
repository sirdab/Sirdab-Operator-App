package co.sirdab.driver.shared.core.network

import android.util.Log
import io.ktor.client.plugins.logging.Logger

/**
 * logcat, in chunks.
 *
 * Ktor hands the whole request over as one multi-line string, and a single
 * `Log.d` call is truncated near 4 kB. A trip with a dozen stops is bigger
 * than that, so the one line worth reading is exactly the one that would be
 * cut off.
 */
private class LogcatLogger : Logger {
    override fun log(message: String) {
        redactSecrets(message).lineSequence().forEach { line ->
            line.chunked(MAX_LINE).forEach { Log.d(HTTP_LOG_TAG, it) }
        }
    }

    private companion object {
        /** Comfortably under logcat's per-entry limit, with room for the tag. */
        const val MAX_LINE = 3500
    }
}

actual fun platformHttpLogger(): Logger = LogcatLogger()
