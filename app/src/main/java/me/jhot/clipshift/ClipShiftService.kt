package me.jhot.clipshift

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder

class ClipShiftService : Service() {

    private val ntfyReceiver = NtfyReceiver()

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("io.heckel.ntfy.MESSAGE_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ntfyReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(ntfyReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ntfyReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, NotificationHelper.build(this, getString(R.string.notification_awaiting)))
        return START_STICKY
    }
}
