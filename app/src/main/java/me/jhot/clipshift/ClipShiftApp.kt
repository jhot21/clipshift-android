package me.jhot.clipshift

import android.app.Application

class ClipShiftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
