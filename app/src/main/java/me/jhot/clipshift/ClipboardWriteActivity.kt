package me.jhot.clipshift

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle

class ClipboardWriteActivity : Activity() {

    private var text: String? = null
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text == null) finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done) return
        done = true
        val t = text ?: return
        // Post via decorView to give the window one extra message-queue cycle after gaining focus.
        // Calling setPrimaryClip() directly inside onWindowFocusChanged() is unreliable on some
        // OEM firmware and Android 13+ with strict clipboard policy enforcement.
        window.decorView.post {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("ClipSHIFT", t))
            finish()
        }
    }
}
