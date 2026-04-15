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
const val EXTRA_ATTACHMENT_URL = "me.jhot.clipshift.ATTACHMENT_URL"
const val EXTRA_ENCRYPTED = "me.jhot.clipshift.ENCRYPTED"
const val EXTRA_COMPRESSION = "me.jhot.clipshift.COMPRESSION"

data class PendingImageData(
    val attachmentUrl: String,
    val encrypted: Boolean,
    val compressionTag: String,
)

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

    fun build(
        context: Context,
        statusText: String,
        pendingClipText: String? = null,
        pendingImageData: PendingImageData? = null,
    ): Notification {
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
                    .putExtra(Intent.EXTRA_TEXT, pendingClipText)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.action_set_clipboard), writeClipIntent)
        }

        if (pendingImageData != null) {
            val shareImageIntent = PendingIntent.getActivity(
                context, 4,
                Intent(context, ImageShareActivity::class.java)
                    .putExtra(EXTRA_ATTACHMENT_URL, pendingImageData.attachmentUrl)
                    .putExtra(EXTRA_ENCRYPTED, pendingImageData.encrypted)
                    .putExtra(EXTRA_COMPRESSION, pendingImageData.compressionTag)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.action_share_image), shareImageIntent)
        }

        return builder.build()
    }

    fun update(
        context: Context,
        statusText: String,
        pendingClipText: String? = null,
        pendingImageData: PendingImageData? = null,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, build(context, statusText, pendingClipText, pendingImageData))
    }
}
