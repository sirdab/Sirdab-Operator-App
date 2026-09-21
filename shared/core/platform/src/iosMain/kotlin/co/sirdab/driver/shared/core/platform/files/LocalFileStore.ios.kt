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

    override suspend fun write(name: String, bytes: ByteArray): String {
        val path = "$root/$name"
        bytes.toNSData().writeToFile(path, atomically = true)
        return path
    }

    override suspend fun read(path: String): ByteArray? =
        NSData.dataWithContentsOfFile(path)?.toByteArray()

    override suspend fun delete(path: String) {
        manager.removeItemAtPath(path, error = null)
    }

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
