package me.jhot.clipshift

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ImageShareActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val attachmentUrl = intent.getStringExtra(EXTRA_ATTACHMENT_URL)
        val encrypted = intent.getBooleanExtra(EXTRA_ENCRYPTED, false)
        val compressionTag = intent.getStringExtra(EXTRA_COMPRESSION) ?: "png"

        if (attachmentUrl == null) {
            finish()
            return
        }

        Toast.makeText(this, R.string.toast_downloading_image, Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                // Download
                val bytes = withContext(Dispatchers.IO) {
                    val conn = URL(attachmentUrl).openConnection() as HttpURLConnection
                    try {
                        conn.connect()
                        if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                        conn.inputStream.readBytes()
                    } finally {
                        conn.disconnect()
                    }
                }

                // Decrypt if needed
                val imageBytes = if (encrypted) {
                    val passphrase = Config.loadPassphrase(this@ImageShareActivity)
                    if (passphrase == null) {
                        Toast.makeText(
                            this@ImageShareActivity,
                            R.string.notification_error_passphrase,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                    try {
                        Crypto.decryptRaw(bytes, passphrase)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ImageShareActivity,
                            R.string.toast_image_decrypt_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                } else bytes

                // Clean up stale cached image files (> 60 seconds old)
                val now = System.currentTimeMillis()
                val staleThreshold = now - 60_000
                cacheDir.listFiles { f -> f.name.startsWith("clipshift_image_") }
                    ?.filter { f ->
                        val ts = f.name.removePrefix("clipshift_image_")
                            .substringBefore(".").toLongOrNull()
                        ts != null && ts < staleThreshold
                    }
                    ?.forEach { it.delete() }

                // Write to cache
                val ext = compressionTagToExt(compressionTag)
                val mimeType = compressionTagToMimeType(compressionTag)
                val file = File(cacheDir, "clipshift_image_${now}.${ext}")
                withContext(Dispatchers.IO) { file.writeBytes(imageBytes) }

                val fileUri = FileProvider.getUriForFile(
                    this@ImageShareActivity,
                    "${packageName}.fileprovider",
                    file,
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, null))
            } catch (e: IOException) {
                Toast.makeText(
                    this@ImageShareActivity,
                    R.string.toast_image_download_failed,
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

private fun compressionTagToMimeType(tag: String): String = when (tag) {
    "jpeg" -> "image/jpeg"
    "heic" -> "image/heic"
    else   -> "image/$tag"  // "image/png", "image/webp"
}
