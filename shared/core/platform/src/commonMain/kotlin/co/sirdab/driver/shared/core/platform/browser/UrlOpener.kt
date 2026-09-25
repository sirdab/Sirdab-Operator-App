package co.sirdab.driver.shared.core.platform.browser

import org.koin.core.module.Module

/**
 * Hands a URL to whatever the phone uses to view one.
 *
 * The app's own documents arrive as short-lived signed links that may be a photograph or a PDF, and
 * the platform already knows how to show either. Rendering them in-app would mean shipping a PDF
 * viewer to solve a problem the browser solved.
 */
interface UrlOpener {
    fun open(url: String)
}

/** Platform binding for [UrlOpener], wired into Koin like the dialer. */
expect fun platformUrlOpenerModule(): Module
