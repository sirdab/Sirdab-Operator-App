package co.sirdab.driver.shared.core.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

private const val CHANNEL_ID = "driver_demo"

class AndroidNotifier(private val context: Context) : Notifier {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver",
                NotificationManager.IMPORTANCE_HIGH,
            )
            manager.createNotificationChannel(channel)
        }
    }

    override fun post(id: String, title: String, body: String) {
        if (!hasPermission()) return
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(context.applicationInfo.icon)
            .setAutoCancel(true)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    override suspend fun ensurePermission(): Boolean = hasPermission()

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

actual fun platformNotifierModule(): Module = module {
    single<Notifier> { AndroidNotifier(androidContext()) }
}
