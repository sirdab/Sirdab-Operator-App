package co.sirdab.driver.shared.core.network

import kotlinx.serialization.Serializable

/** What a file is for. Mirrors the contract's FilePurpose. */
enum class FilePurpose(val wire: String) {
    PROOF_PHOTO("proof_photo"),
    DRIVER_DOCUMENT("driver_document"),
    TRUCK_DOCUMENT("truck_document"),
    TRUCK_PHOTO("truck_photo"),
}

/** The image formats the contract's UploadContentType accepts. */
object UploadContentType {
    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val WEBP = "image/webp"
    const val PDF = "application/pdf"
}

@Serializable
data class FileUploadRequest(
    val purpose: String,
    val contentType: String,
    val sizeBytes: Int,
)

/**
 * Where the bytes go.
 *
 * [headers] are the ones the signature was computed over. They are replayed
 * verbatim and nothing is added to them: storage recomputes the signature from
 * what it receives, so an extra header is the difference between an upload and
 * a 403.
 */
@Serializable
data class FileUploadTarget(
    val fileId: String,
    val uploadUrl: String,
    val method: String = "PUT",
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: String? = null,
)

/** The path the driver app mints upload targets on. */
const val DRIVER_FILES_PATH = "api/driver/files"
