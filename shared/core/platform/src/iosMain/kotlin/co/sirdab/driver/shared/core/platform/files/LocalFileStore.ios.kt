package co.sirdab.driver.shared.core.platform.files

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * Files under Application Support, which iOS does not evict the way it evicts
 * caches, marked as excluded from backup so a driver's proof photos are never
 * copied off the device by iCloud.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosLocalFileStore : LocalFileStore {

    private val manager = NSFileManager.defaultManager

    private val root: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true,
        ).first() as String
        val dir = "$base/$OUTBOX_DIR"
        manager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        NSURL.fileURLWithPath(dir)
            .setResourceValue(true, forKey = EXCLUDED_FROM_BACKUP, error = null)
        dir
    }

    /**
     * Returns the bare file name, not an absolute path.
     *
     * The app's container path is not stable on iOS: it changes when the app is
     * updated or restored, so an absolute path saved in the queue would point at
     * nothing after the next update, and every queued proof would be dropped as
     * "no longer on this device". The name is resolved against [root] each time.
     */
    override suspend fun write(name: String, bytes: ByteArray): String {
        val written = bytes.toNSData().writeToFile(resolve(name), atomically = true)
        // A full disk answers false rather than throwing. Queuing a row for bytes
        // that were never written is a proof that can only ever be dropped, so
        // this must fail where the driver can still see it and retake the photo.
        check(written) { "Could not save the photo on this device." }
        return name
    }

    override suspend fun read(path: String): ByteArray? =
        NSData.dataWithContentsOfFile(resolve(path))?.toByteArray()

    override suspend fun delete(path: String) {
        manager.removeItemAtPath(resolve(path), error = null)
    }

    /**
     * Where [stored] lives today. Rows queued by earlier builds hold an absolute
     * path into a container that may have moved since, so only its file name is
     * trusted: every file this store writes sits directly under [root].
     */
    private fun resolve(stored: String): String = "$root/${stored.substringAfterLast('/')}"

    /** `dataWithBytes:length:` copies, so the scoped allocation is safe to free. */
    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return memScoped {
            NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply {
            usePinned { memcpy(it.addressOf(0), bytes, length) }
        }
    }

    private companion object {
        const val OUTBOX_DIR = "outbox"
        const val EXCLUDED_FROM_BACKUP = "NSURLIsExcludedFromBackupKey"
    }
}

actual fun platformFileStoreModule(): Module = module {
    single<LocalFileStore> { IosLocalFileStore() }
}
