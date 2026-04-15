package me.jhot.clipshift

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
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

        if (!ntfyInstalled(context)) {
            Toast.makeText(context, R.string.toast_ntfy_not_installed, Toast.LENGTH_SHORT).show()
            return
        }

        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= COMPRESS_THRESHOLD) {
            sendBodyMessage(bytes, text, cfg, context)
        } else {
            sendTextAttachmentMessage(bytes, cfg, context)
        }
    }

    /**
     * Sends an image from [uri] as an ntfy attachment.
     *
     * @param compressionTag The validated image format tag ("png", "jpeg", "webp", "heic").
     *   The caller (ShareActivity) is responsible for resolving and validating the MIME type
     *   before calling this function.
     */
    fun publishImage(uri: Uri, compressionTag: String, context: Context) {
        val cfg = Config.load(context)
        if (cfg.topic.isBlank()) {
            Toast.makeText(context, R.string.toast_topic_not_configured, Toast.LENGTH_SHORT).show()
            return
        }

        if (!ntfyInstalled(context)) {
            Toast.makeText(context, R.string.toast_ntfy_not_installed, Toast.LENGTH_SHORT).show()
            return
        }

        // Check size before reading bytes
        val sizeBytes = queryFileSize(uri, context)
        val maxBytes = cfg.maxAttachmentSizeMb.toLong() * 1024 * 1024
        if (sizeBytes != null && sizeBytes > maxBytes) {
            val sizeMb = sizeBytes / (1024 * 1024)
            Toast.makeText(
                context,
                context.getString(R.string.toast_image_too_large, sizeMb, cfg.maxAttachmentSizeMb),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Read image bytes
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: run {
                Toast.makeText(context, R.string.toast_image_read_failed, Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            Toast.makeText(context, R.string.toast_image_read_failed, Toast.LENGTH_SHORT).show()
            return
        }

        if (bytes.size.toLong() > maxBytes) {
            val sizeMb = bytes.size.toLong() / (1024 * 1024)
            Toast.makeText(
                context,
                context.getString(R.string.toast_image_too_large, sizeMb, cfg.maxAttachmentSizeMb),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        sendImageAttachmentMessage(bytes, compressionTag, cfg, context)
    }

    private fun ntfyInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("io.heckel.ntfy", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) { false }

    private fun queryFileSize(uri: Uri, context: Context): Long? {
        val cursor = context.contentResolver.query(
            uri, arrayOf(OpenableColumns.SIZE), null, null, null
        )
        return cursor?.use {
            if (!it.moveToFirst()) return@use null
            val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx < 0 || it.isNull(sizeIdx)) null else it.getLong(sizeIdx)
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

    private fun sendTextAttachmentMessage(bytes: ByteArray, cfg: AppConfig, context: Context) {
        val maxBytes = cfg.maxAttachmentSizeMb.toLong() * 1024 * 1024
        if (bytes.size.toLong() > maxBytes) {
            Toast.makeText(context, R.string.toast_content_too_large, Toast.LENGTH_SHORT).show()
            return
        }

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
            context, "${context.packageName}.fileprovider", tempFile
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

    private fun sendImageAttachmentMessage(
        bytes: ByteArray,
        compressionTag: String,
        cfg: AppConfig,
        context: Context,
    ) {
        val now = System.currentTimeMillis()

        val payload = if (cfg.encryptionEnabled) {
            val passphrase = Config.loadPassphrase(context) ?: return
            try {
                Crypto.encryptRaw(bytes, passphrase)
            } catch (e: Exception) {
                Toast.makeText(context, R.string.toast_send_failed, Toast.LENGTH_SHORT).show()
                return
            }
        } else bytes

        val staleThreshold = now - 60_000
        context.cacheDir.listFiles { f -> f.name.startsWith("clipshift_image_") }
            ?.filter { f ->
                val ts = f.name.removePrefix("clipshift_image_").substringBefore(".").toLongOrNull()
                ts != null && ts < staleThreshold
            }
            ?.forEach { it.delete() }

        val ext = compressionTagToExt(compressionTag)
        val tempFile = File(context.cacheDir, "clipshift_image_${now}.${ext}")
        try {
            tempFile.writeBytes(payload)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.toast_send_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", tempFile
        )

        val tags = buildString {
            append("v:1,did:${cfg.deviceId},type:image,ts:${now}")
            if (cfg.encryptionEnabled) append(",encrypted")
            append(",compression:${compressionTag}")
        }

        val intent = Intent("io.heckel.ntfy.SEND_MESSAGE").apply {
            setPackage("io.heckel.ntfy")
            putExtra("topic", cfg.topic)
            putExtra("title", cfg.deviceName)
            putExtra("message", "clipshift")
            putExtra("file_uri", fileUri.toString())
            putExtra("filename", "clipshift.${ext}")
            putExtra("tags", tags)
            putExtra("priority", 3)
            putExtra("base_url", cfg.baseUrl)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.sendBroadcast(intent)
        Toast.makeText(context, R.string.toast_sent, Toast.LENGTH_SHORT).show()
    }
}

internal fun compressionTagToExt(tag: String): String = when (tag) {
    "jpeg" -> "jpg"
    else   -> tag  // "png", "webp", "heic"
}
