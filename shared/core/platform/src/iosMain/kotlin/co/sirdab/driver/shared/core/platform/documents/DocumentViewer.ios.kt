package co.sirdab.driver.shared.core.platform.documents

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

/**
 * Files under the temporary directory, shown in the system's document preview.
 *
 * Temporary on purpose: every document is fetched fresh from the TMS, so one iOS clears away is
 * one the driver can simply download again. The preview has its own share button, which is where
 * printing, sending and saving to Files live.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosDocumentViewer : DocumentViewer {

    /**
     * The preview on screen now. UIKit holds a document controller's delegate weakly, and the
     * controller itself not at all, so without this both are freed while the driver reads.
     */
    private var showing: Preview? = null

    override suspend fun showPdf(bytes: ByteArray, fileName: String): Boolean {
        val dir = NSTemporaryDirectory() + DOCUMENTS_DIR
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val path = "$dir/${safePdfFileName(fileName)}"
        // A full disk answers false rather than throwing.
        if (!bytes.toNSData().writeToFile(path, atomically = true)) return false
        val url = NSURL.fileURLWithPath(path)
        val host = topViewController() ?: return false

        val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
        val preview = Preview(host, controller) { showing = null }
        controller.delegate = preview
        showing = preview
        if (controller.presentPreviewAnimated(true)) return true
        showing = null

        // Nothing to preview it with. The share sheet can still save it to Files or send it on.
        val share = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        // An iPad shows the sheet as a popover, which needs something to point at.
        share.popoverPresentationController?.sourceView = host.view
        host.presentViewController(share, animated = true, completion = null)
        return true
    }

    private companion object {
        const val DOCUMENTS_DIR = "documents"
    }
}

/** Tells the preview which screen to cover, and the viewer when the driver has closed it. */
private class Preview(
    private val host: UIViewController,
    @Suppress("unused") private val controller: UIDocumentInteractionController,
    private val onEnd: () -> Unit,
) : NSObject(), UIDocumentInteractionControllerDelegateProtocol {

    override fun documentInteractionControllerViewControllerForPreview(
        controller: UIDocumentInteractionController,
    ): UIViewController = host

    override fun documentInteractionControllerDidEndPreview(controller: UIDocumentInteractionController) {
        onEnd()
    }
}

/** Whatever is on screen right now, so the preview is not presented under a sheet. */
private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

/** `dataWithBytes:length:` copies, so the scoped allocation is safe to free. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return memScoped {
        NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
    }
}

actual fun platformDocumentViewerModule(): Module = module {
    single<DocumentViewer> { IosDocumentViewer() }
}
