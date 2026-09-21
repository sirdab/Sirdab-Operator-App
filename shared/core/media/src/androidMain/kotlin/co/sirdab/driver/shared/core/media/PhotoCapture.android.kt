package co.sirdab.driver.shared.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The system camera and the photo picker, both through the activity result API.
 *
 * `TakePicture` hands the full-size image to a file this app owns rather than
 * returning a thumbnail, and needs no CAMERA permission because the photo is
 * taken by the camera app, not by us. `PickVisualMedia` needs no storage
 * permission for the same reason.
 */
@Composable
actual fun rememberPhotoCapture(onCaptured: (CapturedImage) -> Unit): PhotoCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // One file per composition, reused by each camera launch; the bytes are
    // read out and the file truncated as soon as the photo comes back.
    val target = remember(context) { cameraTargetFile(context) }
    val targetUri = remember(target) { target.toContentUri(context) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) scope.deliver(context, targetUri, onCaptured) { target.delete() }
    }

    val library = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.deliver(context, uri, onCaptured) {}
    }

    return remember(camera, library, targetUri) {
        object : PhotoCapture {
            override fun launch(source: PhotoSource) {
                when (source) {
                    PhotoSource.CAMERA -> camera.launch(targetUri)
                    PhotoSource.LIBRARY -> library.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            }
        }
    }
}

private fun CoroutineScope.deliver(
    context: Context,
    uri: Uri,
    onCaptured: (CapturedImage) -> Unit,
    cleanUp: () -> Unit,
) = launch {
    val image = withContext(Dispatchers.IO) { runCatching { context.readScaled(uri) }.getOrNull() }
    cleanUp()
    if (image != null) onCaptured(image)
}

private fun cameraTargetFile(context: Context): File {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return File(dir, "capture.jpg")
}

private fun File.toContentUri(context: Context): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

/**
 * Decodes at a sample size chosen from the bounds, so a 12 megapixel photo is
 * never fully decoded into memory on a cheap phone, then rotates it to match
 * the EXIF orientation. Without the rotation a proof photographed in portrait
 * arrives in dispatch lying on its side.
 */
private fun Context.readScaled(uri: Uri): CapturedImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
    }
    val decoded = contentResolver.openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return null

    val rotation = contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees() } ?: 0
    val bitmap = decoded.scaledToFit().rotated(rotation)

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    bitmap.recycle()
    return CapturedImage(out.toByteArray(), JPEG_CONTENT_TYPE)
}

private fun sampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
    return sample
}

private fun Bitmap.scaledToFit(): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= MAX_EDGE_PX) return this
    val ratio = MAX_EDGE_PX.toFloat() / longest
    val scaled = Bitmap.createScaledBitmap(
        this,
        (width * ratio).toInt().coerceAtLeast(1),
        (height * ratio).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled != this) recycle()
    return scaled
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated != this) recycle()
    return rotated
}

private fun ExifInterface.rotationDegrees(): Int =
    when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
