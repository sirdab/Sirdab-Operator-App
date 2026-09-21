package co.sirdab.driver.shared.core.network

/**
 * The two halves of putting a file on the server: mint a row, send the bytes.
 *
 * Kept as two calls as well as one, because the two callers want different
 * things. Sign-up is a person standing at a screen with signal, so it uploads
 * and gets an id back. The write queue has to survive losing the signal
 * mid-way, so it mints, records what it got, and only then sends.
 */
class FileUploader(private val api: TmsApiClient) {

    suspend fun mint(
        purpose: String,
        contentType: String,
        sizeBytes: Int,
    ): Result<FileUploadTarget> = api.post(
        path = DRIVER_FILES_PATH,
        body = api.encode(
            FileUploadRequest.serializer(),
            FileUploadRequest(purpose, contentType, sizeBytes),
        ),
        deserializer = FileUploadTarget.serializer(),
    ).map { it.value }

    suspend fun send(target: FileUploadTarget, bytes: ByteArray): Result<Unit> =
        api.putBytes(target.uploadUrl, bytes, target.headers)

    /** Mint and send in one go, returning the id to cite. */
    suspend fun upload(
        purpose: FilePurpose,
        contentType: String,
        bytes: ByteArray,
    ): Result<String> {
        val target = mint(purpose.wire, contentType, bytes.size).getOrElse {
            return Result.failure(it)
        }
        return send(target, bytes).map { target.fileId }
    }
}
