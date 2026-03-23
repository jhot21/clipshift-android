package me.jhot.clipshift

import android.content.Context
import android.content.Intent

object PublishHelper {

    fun publish(text: String, context: Context) {
        val cfg = Config.load(context)
        if (cfg.topic.isBlank()) return

        val ntfyPresent = context.packageManager
            .queryBroadcastReceivers(Intent("io.heckel.ntfy.SEND_MESSAGE"), 0)
            .isNotEmpty()
        if (!ntfyPresent) return

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
            putExtra("topic", cfg.topic)
            putExtra("title", cfg.deviceName)
            putExtra("message", content)
            putExtra("tags", tags)
            putExtra("priority", 3)
        }
        context.sendBroadcast(intent)
    }
}
