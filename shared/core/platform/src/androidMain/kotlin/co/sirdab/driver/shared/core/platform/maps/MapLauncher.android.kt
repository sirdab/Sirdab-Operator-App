package co.sirdab.driver.shared.core.platform.maps

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

class AndroidMapLauncher(private val context: Context) : MapLauncher {

    override fun navigateTo(lat: Double, lng: Double, label: String?) {
        // Turn-by-turn straight away, because the button says Directions and a driver pressing it
        // at a loading bay wants the next instruction, not a pin to press Directions on. Google
        // Maps alone answers this scheme; the two fallbacks below cover a phone without it.
        if (open("google.navigation:q=$lat,$lng&mode=d")) return

        // The open scheme every navigation app registers for, so a driver who uses Waze rather
        // than Maps still gets their own. The repeated coordinates are what drop a named pin
        // rather than merely centring the map.
        val pin = "$lat,$lng" + label?.takeIf { it.isNotBlank() }?.let { "(${Uri.encode(it)})" }
            .orEmpty()
        if (open("geo:$lat,$lng?q=$pin")) return

        // No navigation app at all. Every phone still has a browser.
        open("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
    }

    private fun open(uri: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                // Started from an application context, so it needs its own task.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.isSuccess
}

actual fun platformMapLauncherModule(): Module = module {
    single<MapLauncher> { AndroidMapLauncher(androidContext()) }
}
