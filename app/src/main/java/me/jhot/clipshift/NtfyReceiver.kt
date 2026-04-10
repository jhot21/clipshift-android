package me.jhot.clipshift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class NtfyReceiver(
    private val executor: Executor = Executors.newSingleThreadExecutor(),
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cfg = Config.load(context)

        // 1. Topic must be configured and match
        if (cfg.topic.isBlank()) return
        val msgTopic = intent.getStringExtra("topic") ?: return
        if (msgTopic != cfg.topic) return

        // Base URL must match if present (null means older ntfy — allow)
        val msgBaseUrl = intent.getStringExtra("base_url")
        if (msgBaseUrl != null && msgBaseUrl != cfg.baseUrl) return

        // 2. Parse tags
        val tagString = intent.getStringExtra("tags") ?: ""
        val tags = TagParser.parse(tagString)

        // 3. Version check
        if (tags.version != null && tags.version > 1) return

        // 4. Content type check
        if (tags.contentType != null && tags.contentType != "text") return

        // 5. Dedup — skip own messages
        if (tags.deviceId == cfg.deviceId) return

        // 6. Get content
        val rawContent = intent.getStringExtra("message") ?: return
        val senderName = intent.getStringExtra("title") ?: "unknown"

        // 7. Decrypt if needed — Argon2id is expensive; dispatch off the main thread
        if (tags.encrypted) {
            val pendingResult = goAsync()
            executor.execute {
                try {
                    val passphrase = Config.loadPassphrase(context)
                    if (passphrase == null) {
                        NotificationHelper.update(context, context.getString(R.string.notification_error_passphrase))
                        return@execute
                    }
                    val decrypted = try {
                        String(Crypto.decrypt(rawContent, passphrase), Charsets.UTF_8)
                    } catch (e: Exception) {
                        NotificationHelper.update(context, context.getString(R.string.notification_error_passphrase))
                        return@execute
                    }
                    postNotification(context, senderName, decrypted)
                } finally {
                    pendingResult?.finish()
                }
            }
            return
        }

        // 8. Non-encrypted path — synchronous, fast
        postNotification(context, senderName, rawContent)
    }

    private fun postNotification(context: Context, senderName: String, content: String) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val statusText = context.getString(R.string.notification_received_from, senderName, time)
        NotificationHelper.update(context, statusText, pendingClipText = content)
    }
}
