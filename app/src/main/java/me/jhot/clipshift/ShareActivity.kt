package me.jhot.clipshift

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Extract text
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (text == null) {
            finish()
            return
        }

        // 2. Size check
        if (text.toByteArray(Charsets.UTF_8).size > 4096) {
            Toast.makeText(this, R.string.toast_content_too_large, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 3. Pause check
        if (Config.load(this).paused) {
            Toast.makeText(this, R.string.toast_paused, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 4. Publish
        PublishHelper.publish(text, this)
        finish()
    }
}
