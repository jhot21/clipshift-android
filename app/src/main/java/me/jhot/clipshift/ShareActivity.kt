package me.jhot.clipshift

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast

class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            intent?.type?.startsWith("image/") == true -> handleImage()
            else -> handleText()
        }
    }

    private fun handleText() {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (text == null) {
            finish()
            return
        }

        if (text.toByteArray(Charsets.UTF_8).size > 4096) {
            Toast.makeText(this, R.string.toast_content_too_large, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        PublishHelper.publish(text, this)
        finish()
    }

    private fun handleImage() {
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (uri == null) {
            finish()
            return
        }

        // Resolve MIME type: ContentResolver first, fallback to intent.type, fallback to filename ext
        val mimeType = contentResolver.getType(uri)
            ?: intent.type
            ?: run {
                val cursor = contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )
                cursor?.use {
                    if (!it.moveToFirst()) return@use null
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx < 0) return@use null
                    val filename = it.getString(nameIdx) ?: return@use null
                    val ext = filename.substringAfterLast(".", "").lowercase()
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                }
            }

        val compressionTag = when (mimeType) {
            "image/png"  -> "png"
            "image/jpeg",
            "image/jpg"  -> "jpeg"
            "image/webp" -> "webp"
            "image/heic",
            "image/heif" -> "heic"
            else -> {
                Toast.makeText(this, R.string.toast_unsupported_image_format, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        PublishHelper.publishImage(uri, compressionTag, this)
        finish()
    }
}
