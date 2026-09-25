package co.sirdab.driver.shared.core.platform.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

class AndroidUrlOpener(private val context: Context) : UrlOpener {

    override fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            // Started from an application context, so it needs its own task.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // A device with nothing registered for http would otherwise crash the screen that asked.
        runCatching { context.startActivity(intent) }
    }
}

actual fun platformUrlOpenerModule(): Module = module {
    single<UrlOpener> { AndroidUrlOpener(androidContext()) }
}
