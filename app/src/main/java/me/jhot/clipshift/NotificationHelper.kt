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
const val ACTION_TOGGLE_PAUSE = "me.jhot.clipshift.TOGGLE_PAUSE"

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

    fun build(context: Context, statusText: String): Notification {
        val cfg = Config.load(context)
        val paused = cfg.paused

        val settingsIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val pauseIntent = PendingIntent.getService(
            context, 1,
            Intent(context, ClipShiftService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val sendIntent = PendingIntent.getActivity(
            context, 2,
            Intent(context, ClipboardSendActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val pauseLabel = if (paused) R.string.action_resume else R.string.action_pause

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText)
            .setContentIntent(settingsIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, context.getString(pauseLabel), pauseIntent)
            .addAction(0, context.getString(R.string.action_send_clipboard), sendIntent)
            .build()
    }

    fun update(context: Context, statusText: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, build(context, statusText))
    }
}
