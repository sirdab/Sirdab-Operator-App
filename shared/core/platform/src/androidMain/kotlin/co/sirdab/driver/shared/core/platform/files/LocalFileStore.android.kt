package co.sirdab.driver.shared.core.platform.files

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Files under `filesDir/outbox`, which is app-private and not backed up to the
 * cloud, so a proof photo never leaves the device except through the upload.
 */
class AndroidLocalFileStore(context: Context) : LocalFileStore {

    private val root = File(context.applicationContext.filesDir, OUTBOX_DIR)

    override suspend fun write(name: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        root.mkdirs()
        val file = File(root, name)
        file.writeBytes(bytes)
        file.absolutePath
    }

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.isFile) file.readBytes() else null
    }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { File(path).delete() }
    }

    private companion object {
        const val OUTBOX_DIR = "outbox"
    }
}

actual fun platformFileStoreModule(): Module = module {
    single<LocalFileStore> { AndroidLocalFileStore(androidContext()) }
}
