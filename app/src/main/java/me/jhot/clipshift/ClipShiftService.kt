package me.jhot.clipshift

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors

class ClipShiftService : Service() {

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val ntfyReceiver = NtfyReceiver(executor = ioExecutor)

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
        ioExecutor.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, NotificationHelper.build(this, getString(R.string.notification_awaiting)))
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+): background sticky restart
            // was denied the FGS exemption. Stop rather than crash-loop.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }
}
