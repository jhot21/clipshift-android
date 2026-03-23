package me.jhot.clipshift

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ClipShiftService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cfg = Config.load(this)
        val statusText = if (cfg.paused) {
            getString(R.string.notification_paused)
        } else {
            getString(R.string.notification_active)
        }
        startForeground(NOTIFICATION_ID, NotificationHelper.build(this, statusText))

        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                val nowPaused = !cfg.paused
                Config.setPaused(this, nowPaused)
                val newStatus = if (nowPaused) getString(R.string.notification_paused)
                               else getString(R.string.notification_active)
                NotificationHelper.update(this, newStatus)
            }
        }

        return START_STICKY
    }
}
