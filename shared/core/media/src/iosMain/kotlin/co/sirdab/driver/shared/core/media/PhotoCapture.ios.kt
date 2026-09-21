package co.sirdab.driver.shared.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.math.max

/**
 * `UIImagePickerController` for both sources.
 *
 * The camera and the library are the same controller with a different source
 * type, and both ask for their own permission when presented, so the app does
 * not prompt for anything until the driver has asked to take a photo.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberPhotoCapture(onCaptured: (CapturedImage) -> Unit): PhotoCapture {
    // The delegate outlives each presentation, so it reads the newest callback
    // rather than the one captured when it was created.
    val latest by rememberUpdatedState(onCaptured)
    val delegate = remember { PickerDelegate() }
    delegate.onCaptured = { latest(it) }

    return remember(delegate) {
        object : PhotoCapture {
            override fun launch(source: PhotoSource) {
                val host = topViewController() ?: return
                val picker = UIImagePickerController().apply {
                    sourceType = source.toSourceType()
                    setDelegate(delegate)
                }
                host.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun PhotoSource.toSourceType(): UIImagePickerControllerSourceType = when (this) {
    // Falls back to the library on a simulator, which has no camera at all.
    PhotoSource.CAMERA ->
        if (UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
            )
        ) {
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        } else {
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        }

    PhotoSource.LIBRARY ->
        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class PickerDelegate :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    var onCaptured: ((CapturedImage) -> Unit)? = null

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(flag = true, completion = null)
        // Cancelling is silent, and so is an image the system could not hand
        // over: neither is something to interrupt a driver about.
        image?.toCapturedImage()?.let { onCaptured?.invoke(it) }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(flag = true, completion = null)
    }
}

/**
 * Redraws the image at the capped size.
 *
 * Redrawing rather than re-tagging is what normalises the orientation: a photo
 * taken in portrait carries a rotation flag that plenty of viewers ignore, and
 * a delivery proof that reaches dispatch on its side is a support call.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toCapturedImage(): CapturedImage? {
    val width = size.useContents { width }
    val height = size.useContents { height }
    if (width <= 0.0 || height <= 0.0) return null

    val longest = max(width, height)
    val ratio = if (longest > MAX_EDGE_PX) MAX_EDGE_PX / longest else 1.0
    val targetWidth = width * ratio
    val targetHeight = height * ratio

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), true, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    val data = UIImageJPEGRepresentation(scaled ?: this, JPEG_QUALITY / 100.0) ?: return null
    return CapturedImage(data.toByteArray(), JPEG_CONTENT_TYPE)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}

/** Whatever is on screen right now, so the picker is not presented under a sheet. */
private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
