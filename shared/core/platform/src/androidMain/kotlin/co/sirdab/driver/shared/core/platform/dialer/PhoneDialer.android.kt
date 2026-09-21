package co.sirdab.driver.shared.core.platform.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

class AndroidPhoneDialer(private val context: Context) : PhoneDialer {

    override fun dial(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
            // Started from an application context, so it needs its own task.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // A device with no dialer (an emulator, a tablet) would otherwise crash the trip screen.
        runCatching { context.startActivity(intent) }
    }
}

actual fun platformDialerModule(): Module = module {
    single<PhoneDialer> { AndroidPhoneDialer(androidContext()) }
}
