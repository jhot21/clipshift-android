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

        if (cfg.topic.isBlank()) return
        val msgTopic = intent.getStringExtra("topic") ?: return
        if (msgTopic != cfg.topic) return

        val msgBaseUrl = intent.getStringExtra("base_url")
        if (msgBaseUrl != null && msgBaseUrl != cfg.baseUrl) return

        val tagString = intent.getStringExtra("tags") ?: ""
        val tags = TagParser.parse(tagString)

        if (tags.version != null && tags.version > 1) return
        if (tags.deviceId == cfg.deviceId) return

        val senderName = intent.getStringExtra("title") ?: "unknown"
        val attachmentUrl = intent.getStringExtra("attachment_url")?.takeIf { it.isNotBlank() }

        // Image content type: never download here — post notification for on-demand download
        if (tags.contentType == "image") {
            if (attachmentUrl == null) {
                NotificationHelper.update(context, context.getString(R.string.notification_error_no_attachment))
                return
            }
            postImageNotification(context, senderName, attachmentUrl, tags.encrypted, tags.compression)
            return
        }

        if (attachmentUrl != null) {
            // Text attachment path — download, decrypt, decompress
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
                        CompressionAlgorithm.Gzip -> Compression.decompress(decrypted)
                        CompressionAlgorithm.Png,
                        CompressionAlgorithm.Jpeg,
                        CompressionAlgorithm.WebP,
                        CompressionAlgorithm.Heic -> {
                            Log.d(TAG, "Image compression on text path; skipping")
                            return@execute
                        }
                        is CompressionAlgorithm.Unknown -> {
                            Log.d(TAG, "Unsupported compression algorithm: ${comp.name}")
                            return@execute
                        }
                        null -> decrypted
                    }

                    when (tags.contentType) {
                        "text" -> postTextNotification(context, senderName, String(decompressed, Charsets.UTF_8))
                        else   -> Log.d(TAG, "Unsupported content type: ${tags.contentType}")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Attachment download failed: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Attachment processing failed: ${e.message}")
                    NotificationHelper.update(context, context.getString(R.string.notification_error_passphrase))
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // Body path (text only)
        if (tags.contentType != null && tags.contentType != "text") return
        val rawContent = intent.getStringExtra("message") ?: return

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
                    postTextNotification(context, senderName, decrypted)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        postTextNotification(context, senderName, rawContent)
    }

    private fun postTextNotification(context: Context, senderName: String, content: String) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val statusText = context.getString(R.string.notification_received_from, senderName, time)
        NotificationHelper.update(context, statusText, pendingClipText = content)
    }

    private fun postImageNotification(
        context: Context,
        senderName: String,
        attachmentUrl: String,
        encrypted: Boolean,
        compression: CompressionAlgorithm?,
    ) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val statusText = context.getString(R.string.notification_received_image_from, senderName, time)
        val compressionTag = when (compression) {
            CompressionAlgorithm.Png  -> "png"
            CompressionAlgorithm.Jpeg -> "jpeg"
            CompressionAlgorithm.WebP -> "webp"
            CompressionAlgorithm.Heic -> "heic"
            else -> "png"
        }
        NotificationHelper.update(
            context,
            statusText,
            pendingImageData = PendingImageData(attachmentUrl, encrypted, compressionTag),
        )
    }
}
