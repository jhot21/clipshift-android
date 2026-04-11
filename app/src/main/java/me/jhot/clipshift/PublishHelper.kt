package me.jhot.clipshift

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

private const val COMPRESS_THRESHOLD = 3072

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

        val bytes = text.toByteArray(Charsets.UTF_8)

        if (bytes.size <= COMPRESS_THRESHOLD) {
            sendBodyMessage(bytes, text, cfg, context)
        } else {
            sendAttachmentMessage(bytes, cfg, context)
        }
    }

    private fun sendBodyMessage(bytes: ByteArray, text: String, cfg: AppConfig, context: Context) {
        val (content, encrypted) = if (cfg.encryptionEnabled) {
            val passphrase = Config.loadPassphrase(context) ?: return
            Crypto.encrypt(bytes, passphrase) to true
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

    private fun sendAttachmentMessage(bytes: ByteArray, cfg: AppConfig, context: Context) {
        val now = System.currentTimeMillis()

        val compressed = try {
            Compression.compress(bytes)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.toast_send_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val payload = if (cfg.encryptionEnabled) {
            val passphrase = Config.loadPassphrase(context) ?: return
            try {
                Crypto.encryptRaw(compressed, passphrase)
            } catch (e: Exception) {
                Toast.makeText(context, R.string.toast_send_failed, Toast.LENGTH_SHORT).show()
                return
            }
        } else compressed

        // Clean up stale temp files (> 60 seconds old) — age determined by timestamp in filename
        val staleThreshold = now - 60_000
        context.cacheDir.listFiles { f -> f.name.startsWith("clipshift_attach_") }
            ?.filter { f ->
                val ts = f.name.removePrefix("clipshift_attach_").removeSuffix(".bin").toLongOrNull()
                ts != null && ts < staleThreshold
            }
            ?.forEach { it.delete() }

        val tempFile = File(context.cacheDir, "clipshift_attach_${now}.bin")
        try {
            tempFile.writeBytes(payload)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.toast_send_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        val tags = buildString {
            append("v:1,did:${cfg.deviceId},type:text,ts:${now}")
            if (cfg.encryptionEnabled) append(",encrypted")
            append(",compression:gzip")
        }

        val intent = Intent("io.heckel.ntfy.SEND_MESSAGE").apply {
            setPackage("io.heckel.ntfy")
            putExtra("topic", cfg.topic)
            putExtra("title", cfg.deviceName)
            putExtra("file", fileUri.toString())
            putExtra("filename", "clipshift.txt")
            putExtra("tags", tags)
            putExtra("priority", 3)
            putExtra("base_url", cfg.baseUrl)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.sendBroadcast(intent)
        Toast.makeText(context, R.string.toast_sent, Toast.LENGTH_SHORT).show()
    }
}
