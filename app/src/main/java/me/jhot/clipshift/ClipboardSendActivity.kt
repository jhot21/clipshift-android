package me.jhot.clipshift

import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast

class ClipboardSendActivity : Activity() {

    private var paused = false
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load config once here; reused in onWindowFocusChanged to avoid redundant I/O.
        paused = Config.load(this).paused
        if (paused) {
            finish()
            return
        }
        // Clipboard access must wait for window focus (Android 12+ restriction).
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done || paused) return
        done = true

        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0 || !clip.description.hasMimeType("text/*")) {
            Toast.makeText(this, R.string.toast_text_only, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val text = clip.getItemAt(0).coerceToText(this).toString()

        if (text.toByteArray(Charsets.UTF_8).size > 4096) {
            Toast.makeText(this, R.string.toast_too_long, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        PublishHelper.publish(text, this)
        finish()
    }
}
