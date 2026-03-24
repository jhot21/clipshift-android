package me.jhot.clipshift

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

const val NOTIFICATION_ID = 1
const val CHANNEL_ID = "clipshift_service"

object NotificationHelper {

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun build(context: Context, statusText: String, pendingClipText: String? = null): Notification {
        val settingsIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val sendIntent = PendingIntent.getActivity(
            context, 2,
            Intent(context, ClipboardSendActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText)
            .setContentIntent(settingsIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, context.getString(R.string.action_send_clipboard), sendIntent)

        if (pendingClipText != null) {
            val writeClipIntent = PendingIntent.getActivity(
                context, 3,
                Intent(context, ClipboardWriteActivity::class.java)
                    .putExtra(Intent.EXTRA_TEXT, pendingClipText),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.action_set_clipboard), writeClipIntent)
        }

        return builder.build()
    }

    fun update(context: Context, statusText: String, pendingClipText: String? = null) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, build(context, statusText, pendingClipText))
    }
}
