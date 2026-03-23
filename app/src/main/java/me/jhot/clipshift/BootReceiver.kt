package me.jhot.clipshift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val cfg = Config.load(context)
        if (cfg.topic.isBlank()) return
        context.startForegroundService(Intent(context, ClipShiftService::class.java))
    }
}
