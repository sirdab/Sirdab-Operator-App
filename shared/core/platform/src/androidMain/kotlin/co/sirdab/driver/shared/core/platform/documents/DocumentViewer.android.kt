package co.sirdab.driver.shared.core.platform.documents

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Files under `cache/documents`, handed out through the app's FileProvider.
 *
 * The cache on purpose: every document is fetched fresh from the TMS, so a copy the system evicts
 * under storage pressure is one the driver can simply download again. Only that directory is
 * shared, so the grant reaches the PDF and nothing else the app keeps.
 */
class AndroidDocumentViewer(private val context: Context) : DocumentViewer {

    override suspend fun showPdf(bytes: ByteArray, fileName: String): Boolean {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, DOCUMENTS_DIR).apply { mkdirs() }
                File(dir, safePdfFileName(fileName)).apply { writeBytes(bytes) }
            }.getOrNull()
        } ?: return false
        // Throws when the directory is missing from file_paths.xml, which is a build mistake, but
        // one that should cost a document rather than the trip screen.
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return false

        if (start(view(uri))) return true
        // No PDF viewer on the phone. Whatever takes a file still can: WhatsApp, Drive, a printer.
        return start(share(uri, file.name))
    }

    private fun view(uri: Uri) = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, PDF)
        // Started from an application context, so it needs its own task.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun share(uri: Uri, name: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PDF
            putExtra(Intent.EXTRA_STREAM, uri)
            // The stream alone carries no grant through the chooser; the clip is what does.
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** A phone with nothing registered for the intent would otherwise crash the screen that asked. */
    private fun start(intent: Intent): Boolean = runCatching { context.startActivity(intent) }.isSuccess

    private companion object {
        /** Must match the `documents` entry in the app's `file_paths.xml`. */
        const val DOCUMENTS_DIR = "documents"
        const val PDF = "application/pdf"
    }
}

actual fun platformDocumentViewerModule(): Module = module {
    single<DocumentViewer> { AndroidDocumentViewer(androidContext()) }
}
