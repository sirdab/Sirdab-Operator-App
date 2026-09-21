package co.sirdab.driver.shared.core.network

import io.ktor.client.plugins.logging.Logger
import platform.Foundation.NSLog

/** NSLog, so the lines show up in the Xcode console and in Console.app. */
private class NsLogLogger : Logger {
    override fun log(message: String) {
        redactSecrets(message).lineSequence().forEach { NSLog("%s %s", HTTP_LOG_TAG, it) }
    }
}

actual fun platformHttpLogger(): Logger = NsLogLogger()
