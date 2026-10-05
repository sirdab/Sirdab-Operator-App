package co.sirdab.driver.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.sirdab.driver.MainActivity
import co.sirdab.driver.R
import co.sirdab.driver.shared.feature.notifications.api.PushTokens
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.inject

/**
 * Firebase's way into the app: a new token, and any message that arrives while the app is open.
 *
 * A message with a notification block that arrives in the background never reaches here — Android
 * shows it itself, on [CHANNEL_ID] (named in the manifest). So this only has to show the ones the
 * driver would otherwise miss because the app was in front of them.
 */
class DriverMessagingService : FirebaseMessagingService() {

    private val tokens: PushTokens by inject()

    override fun onNewToken(token: String) {
        tokens.update(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        show(
            context = this,
            id = message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(),
            title = notification.title,
            body = notification.body,
            data = message.data,
        )
    }

    companion object {
        const val CHANNEL_ID = "driver_updates"

        /** Created at launch: Android needs the channel before the first background message. */
        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.push_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.push_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun show(
            context: Context,
            id: Int,
            title: String?,
            body: String?,
            data: Map<String, String>,
        ) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            // The payload rides along, so a tap can route on its `type` once the app does that.
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { data.forEach { (key, value) -> putExtra(key, value) } }
            val pending = PendingIntent.getActivity(
                context,
                id,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val built = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(context.getColor(R.color.ic_launcher_background))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending)
                .build()
            NotificationManagerCompat.from(context).notify(id, built)
        }
    }
}
