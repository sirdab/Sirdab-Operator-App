package co.sirdab.driver.shared.core.network

/**
 * The two halves of putting a file on the server: mint a row, send the bytes.
 *
 * Two calls rather than one because the write queue has to survive losing the signal mid-way: it
 * mints, records what it got, and only then sends.
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
}
