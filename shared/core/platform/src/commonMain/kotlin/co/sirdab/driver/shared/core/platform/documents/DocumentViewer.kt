package co.sirdab.driver.shared.core.platform.documents

import org.koin.core.module.Module

/**
 * Shows a PDF the app already holds in whatever the phone views PDFs with.
 *
 * For files the app had to fetch itself, with the driver's session. An order's slip comes from the
 * TMS behind a bearer token, so unlike the signed links the browser is handed, there is no URL
 * anything else could open. The bytes go to the app's cache, and the viewer is given that file:
 * from there the driver can show it at the gate, print it, send it on, or save it for later.
 * Shipping a viewer of our own would solve a problem the phone has solved.
 */
interface DocumentViewer {
    /**
     * Writes [bytes] under [fileName] and opens them. False when nothing on the phone would take
     * the file, not even to share it.
     *
     * [fileName] is what the viewer, and anything the driver shares it to, calls the file.
     */
    suspend fun showPdf(bytes: ByteArray, fileName: String): Boolean
}

/** Platform binding for [DocumentViewer], wired into Koin like the dialer and the browser. */
expect fun platformDocumentViewerModule(): Module

/**
 * [name] made safe to write: the server already sends one, but it is written to disk and handed
 * to other apps, so nothing in it is taken on trust.
 */
internal fun safePdfFileName(name: String): String {
    val stem = name.removeSuffix(".pdf")
        .replace(Regex("[^A-Za-z0-9_-]+"), "-")
        .trim('-')
        .take(MAX_STEM)
        .ifEmpty { "document" }
    return "$stem.pdf"
}

private const val MAX_STEM = 80
