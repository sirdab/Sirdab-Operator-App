package co.sirdab.driver.shared.core.media

import androidx.compose.runtime.Composable

/**
 * An image the driver just produced, already downscaled and re-encoded.
 *
 * Never the camera's original: a modern phone takes a 12 megapixel photo of a
 * pallet, and a driver in a warehouse has one bar of signal to send it on. The
 * proof is that the load was there, not that it was photographed in detail, so
 * the bytes are cut down before they ever reach the queue.
 */
class CapturedImage(
    val bytes: ByteArray,
    val contentType: String,
)

/** Where the image came from. */
enum class PhotoSource {
    /** The system camera, for the load actually in front of the driver. */
    CAMERA,

    /** The photo library, for a slip photographed a moment ago or a retry. */
    LIBRARY,
}

/** Opens the platform's camera or picker. The result arrives on the callback. */
interface PhotoCapture {
    fun launch(source: PhotoSource)
}

/**
 * [onCaptured] fires only when the driver actually produced an image; cancelling
 * the camera is silent, because it is not an error and needs no message.
 */
@Composable
expect fun rememberPhotoCapture(onCaptured: (CapturedImage) -> Unit): PhotoCapture

/**
 * The longest edge a proof photo is reduced to, and the JPEG quality it is
 * re-encoded at. Roughly 200-400 KB per photo: legible enough to read a plate
 * or a stamped slip, small enough to leave a depot's dead zone in one go.
 */
internal const val MAX_EDGE_PX = 1600
internal const val JPEG_QUALITY = 80
internal const val JPEG_CONTENT_TYPE = "image/jpeg"
