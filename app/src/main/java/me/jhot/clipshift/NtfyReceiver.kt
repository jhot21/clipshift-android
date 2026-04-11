package me.jhot.clipshift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val TAG = "ClipShift"

class NtfyReceiver(
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val httpFetcher: (String) -> ByteArray = { url ->
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connect()
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.readBytes()
        } finally {
            conn.disconnect()
        }
    },
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cfg = Config.load(context)

        // 1. Topic must be configured and match
        if (cfg.topic.isBlank()) return
        val msgTopic = intent.getStringExtra("topic") ?: return
        if (msgTopic != cfg.topic) return

        // 2. Base URL must match if present (null means older ntfy — allow)
        val msgBaseUrl = intent.getStringExtra("base_url")
        if (msgBaseUrl != null && msgBaseUrl != cfg.baseUrl) return

        // 3. Parse tags
        val tagString = intent.getStringExtra("tags") ?: ""
        val tags = TagParser.parse(tagString)

        // 4. Version check
        if (tags.version != null && tags.version > 1) return

        // 5. Dedup — skip own messages
        if (tags.deviceId == cfg.deviceId) return

        val senderName = intent.getStringExtra("title") ?: "unknown"
        val attachmentUrl = intent.getStringExtra("attachment_url")

        if (attachmentUrl != null) {
            // Attachment path — always async (download may be slow)
            val pendingResult = goAsync()
            executor.execute {
                try {
                    val bytes = httpFetcher(attachmentUrl)

                    val decrypted = if (tags.encrypted) {
                        val passphrase = Config.loadPassphrase(context)
                        if (passphrase == null) {
                            NotificationHelper.update(context, context.getString(R.string.notification_error_passphrase))
                            return@execute
                        }
                        Crypto.decryptRaw(bytes, passphrase)
                    } else bytes

                    val decompressed = when (val comp = tags.compression) {
                        CompressionAlgorithm.Zstd -> Compression.decompress(decrypted)
                        is CompressionAlgorithm.Unknown -> {
                            Log.d(TAG, "Unsupported compression algorithm: ${comp.name}")
                            return@execute
                        }
                        null -> decrypted
                    }

                    // Content-type dispatch — extension point for future types (image, etc.)
                    when (tags.contentType) {
                        "text" -> postNotification(context, senderName, String(decompressed, Charsets.UTF_8))
                        else   -> Log.d(TAG, "Unsupported content type: ${tags.contentType}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Attachment receive failed: ${e.message}")
                } finally {
                    pendingResult?.finish()
                }
            }
            return
        }

        // Body path
        // 6. Content type check (body path only)
        if (tags.contentType != null && tags.contentType != "text") return

        // 7. Get content
        val rawContent = intent.getStringExtra("message") ?: return

        // 8. Decrypt if needed — Argon2id is expensive; dispatch off the main thread
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

        // 9. Non-encrypted body path — synchronous, fast
        postNotification(context, senderName, rawContent)
    }

    private fun postNotification(context: Context, senderName: String, content: String) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val statusText = context.getString(R.string.notification_received_from, senderName, time)
        NotificationHelper.update(context, statusText, pendingClipText = content)
    }
}
