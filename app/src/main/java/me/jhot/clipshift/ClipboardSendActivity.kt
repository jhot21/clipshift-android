package me.jhot.clipshift

import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast

class ClipboardSendActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cfg = Config.load(this)

        // 1. Pause check
        if (cfg.paused) {
            finish()
            return
        }

        // 2. Read clipboard — must be text
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0 || !clip.description.hasMimeType("text/*")) {
            Toast.makeText(this, R.string.toast_text_only, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val text = clip.getItemAt(0).coerceToText(this).toString()

        // 3. Size check
        if (text.toByteArray(Charsets.UTF_8).size > 4096) {
            Toast.makeText(this, R.string.toast_too_long, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 4. Publish
        PublishHelper.publish(text, this)
        finish()
    }
}
