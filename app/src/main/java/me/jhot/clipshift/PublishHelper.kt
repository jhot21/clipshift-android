package me.jhot.clipshift

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast

object PublishHelper {

    fun publish(text: String, context: Context) {
        val cfg = Config.load(context)
        if (cfg.topic.isBlank()) {
            Toast.makeText(context, R.string.toast_topic_not_configured, Toast.LENGTH_SHORT).show()
            return
        }

        val ntfyPresent = try {
            context.packageManager.getPackageInfo("io.heckel.ntfy", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) { false }
        if (!ntfyPresent) {
            Toast.makeText(context, R.string.toast_ntfy_not_installed, Toast.LENGTH_SHORT).show()
            return
        }

        val (content, encrypted) = if (cfg.encryptionEnabled) {
            val passphrase = Config.loadPassphrase(context) ?: return
            Crypto.encrypt(text.toByteArray(Charsets.UTF_8), passphrase) to true
        } else {
            text to false
        }

        val tags = buildString {
            append("v:1,did:${cfg.deviceId},type:text,ts:${System.currentTimeMillis()}")
            if (encrypted) append(",encrypted")
        }

        val intent = Intent("io.heckel.ntfy.SEND_MESSAGE").apply {
            setPackage("io.heckel.ntfy")
            putExtra("topic", cfg.topic)
            putExtra("title", cfg.deviceName)
            putExtra("message", content)
            putExtra("tags", tags)
            putExtra("priority", 3)
            putExtra("base_url", cfg.baseUrl)
        }
        context.sendBroadcast(intent)
        Toast.makeText(context, R.string.toast_sent, Toast.LENGTH_SHORT).show()
    }
}
