package me.jhot.clipshift

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.robolectric.fakes.RoboCursor
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config as RobolectricConfig
import org.robolectric.shadows.ShadowPackageManager
import android.content.pm.PackageInfo
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34])
class PublishHelperTest {

    private lateinit var context: Context
    private lateinit var shadowPackageManager: ShadowPackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowPackageManager = Shadows.shadowOf(context.packageManager)
        Config.save(context, topic = "test-topic", deviceName = "Test Device",
            encryptionEnabled = false, baseUrl = "https://ntfy.sh")

        // Initialize FileProvider so getUriForFile works under Robolectric
        val providerInfo = ProviderInfo().apply {
            authority = "${context.packageName}.fileprovider"
            grantUriPermissions = true
        }
        Robolectric.buildContentProvider(FileProvider::class.java)
            .create(providerInfo)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("clipshift_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `sends broadcast when ntfy is installed`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        val shadows = Shadows.shadowOf(context as Application)
        PublishHelper.publish("hello", context)

        val broadcast = shadows.broadcastIntents.lastOrNull()
        assertThat(broadcast?.action).isEqualTo("io.heckel.ntfy.SEND_MESSAGE")
        assertThat(broadcast?.getStringExtra("message")).isEqualTo("hello")
        assertThat(broadcast?.getStringExtra("topic")).isEqualTo("test-topic")
    }

    @Test
    fun `does not send broadcast when ntfy is not installed`() {
        val shadows = Shadows.shadowOf(context as Application)
        PublishHelper.publish("hello", context)
        assertThat(shadows.broadcastIntents).isEmpty()
        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(context.getString(R.string.toast_ntfy_not_installed))
    }

    @Test
    fun `send intent includes baseUrl extra`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        Config.save(context, topic = "test-topic", deviceName = "Test Device",
            encryptionEnabled = false, baseUrl = "https://my.server.com")
        val shadows = Shadows.shadowOf(context as Application)
        PublishHelper.publish("hello", context)

        val broadcast = shadows.broadcastIntents.lastOrNull()
        assertThat(broadcast?.getStringExtra("base_url")).isEqualTo("https://my.server.com")
    }

    @Test
    fun `tags contain required metadata fields`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        val shadows = Shadows.shadowOf(context as Application)
        PublishHelper.publish("hello", context)

        val tags = shadows.broadcastIntents.last().getStringExtra("tags") ?: ""
        assertThat(tags).contains("v:1")
        assertThat(tags).contains("did:")
        assertThat(tags).contains("type:text")
        assertThat(tags).contains("ts:")
        assertThat(tags).doesNotContain("encrypted")
    }

    @Test
    fun `text at threshold boundary uses body path`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        val shadows = Shadows.shadowOf(context as Application)
        val text = "a".repeat(3072)  // exactly at threshold — body path

        PublishHelper.publish(text, context)

        val broadcast = shadows.broadcastIntents.lastOrNull()
        assertThat(broadcast?.getStringExtra("message")).isEqualTo(text)
        assertThat(broadcast?.getStringExtra("file")).isNull()
    }

    @Test
    fun `text over threshold uses attachment path with compression tag`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        val shadows = Shadows.shadowOf(context as Application)
        val text = "a".repeat(3073)  // one byte over threshold

        PublishHelper.publish(text, context)

        val broadcast = shadows.broadcastIntents.lastOrNull()
        assertThat(broadcast?.getStringExtra("file")).isNotNull()
        assertThat(broadcast?.getStringExtra("filename")).isEqualTo("clipshift.txt")
        val tags = broadcast?.getStringExtra("tags") ?: ""
        assertThat(tags).contains("compression:gzip")
        assertThat(tags).contains("type:text")
        assertThat(broadcast?.getStringExtra("message")).isNull()
    }

    @Test
    fun `large text attachment path does not include encrypted tag when encryption disabled`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        val shadows = Shadows.shadowOf(context as Application)

        PublishHelper.publish("b".repeat(4000), context)

        val tags = shadows.broadcastIntents.last().getStringExtra("tags") ?: ""
        assertThat(tags).doesNotContain("encrypted")
    }

    @Test
    fun `stale temp files older than 60 seconds are cleaned up on large send`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })

        // Plant a stale file
        val stale = java.io.File(context.cacheDir, "clipshift_attach_${System.currentTimeMillis() - 90_000}.bin")
        stale.writeBytes(ByteArray(10))
        assertThat(stale.exists()).isTrue()

        PublishHelper.publish("c".repeat(4000), context)

        assertThat(stale.exists()).isFalse()
    }

    @Test
    fun `large text attachment path includes encrypted tag when encryption enabled`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        io.mockk.mockkObject(Config)
        try {
            io.mockk.every { Config.loadPassphrase(any()) } returns "s3cr3t"
            Config.save(context, topic = "test-topic", deviceName = "Test Device",
                encryptionEnabled = true, baseUrl = "https://ntfy.sh")
            Crypto.keyDeriver = BcArgon2idDeriver()

            val shadows = Shadows.shadowOf(context as Application)
            PublishHelper.publish("d".repeat(4000), context)

            val tags = shadows.broadcastIntents.last().getStringExtra("tags") ?: ""
            assertThat(tags).contains("encrypted")
            assertThat(tags).contains("compression:gzip")
        } finally {
            io.mockk.unmockkObject(Config)
            Config.save(context, topic = "test-topic", deviceName = "Test Device",
                encryptionEnabled = false, baseUrl = "https://ntfy.sh")
        }
    }

    @Test
    fun `publishImage sends broadcast with image tags for png`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        val shadows = Shadows.shadowOf(context as Application)

        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)  // PNG magic bytes
        val uri = android.net.Uri.parse("content://me.jhot.clipshift.test/image.png")
        val shadowResolver = Shadows.shadowOf(context.contentResolver)
        shadowResolver.registerInputStream(uri, pngBytes.inputStream())
        // Size cursor
        val cursor = RoboCursor().apply {
            setColumnNames(listOf(OpenableColumns.SIZE))
            setResults(arrayOf(arrayOf(pngBytes.size.toLong())))
        }
        shadowResolver.setCursor(uri, cursor)

        PublishHelper.publishImage(uri, "png", context)

        val broadcast = shadows.broadcastIntents.lastOrNull()
        assertThat(broadcast?.action).isEqualTo("io.heckel.ntfy.SEND_MESSAGE")
        val tags = broadcast?.getStringExtra("tags") ?: ""
        assertThat(tags).contains("type:image")
        assertThat(tags).contains("compression:png")
        assertThat(tags).doesNotContain("type:text")
        assertThat(broadcast?.getStringExtra("filename")).isEqualTo("clipshift.png")
        assertThat(broadcast?.getStringExtra("file_uri")).startsWith("content://")
        assertThat(broadcast?.getStringExtra("message")).isEqualTo("clipshift")
    }

    @Test
    fun `publishImage uses jpg extension for jpeg compression tag`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        val shadows = Shadows.shadowOf(context as Application)

        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val uri = android.net.Uri.parse("content://me.jhot.clipshift.test/photo.jpg")
        val shadowResolver = Shadows.shadowOf(context.contentResolver)
        shadowResolver.registerInputStream(uri, jpegBytes.inputStream())
        val cursor = RoboCursor().apply {
            setColumnNames(listOf(OpenableColumns.SIZE))
            setResults(arrayOf(arrayOf(jpegBytes.size.toLong())))
        }
        shadowResolver.setCursor(uri, cursor)

        PublishHelper.publishImage(uri, "jpeg", context)

        val broadcast = shadows.broadcastIntents.lastOrNull()
        val tags = broadcast?.getStringExtra("tags") ?: ""
        assertThat(tags).contains("compression:jpeg")
        assertThat(broadcast?.getStringExtra("filename")).isEqualTo("clipshift.jpg")
    }

    @Test
    fun `publishImage toasts when image is too large`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        val shadows = Shadows.shadowOf(context as Application)

        Config.save(context, topic = "test-topic", deviceName = "Test Device",
            encryptionEnabled = false, baseUrl = "https://ntfy.sh", maxAttachmentSizeMb = 1)

        val uri = android.net.Uri.parse("content://me.jhot.clipshift.test/big.png")
        val cursor = RoboCursor().apply {
            setColumnNames(listOf(OpenableColumns.SIZE))
            setResults(arrayOf(arrayOf(2L * 1024 * 1024)))  // 2 MB, over the 1 MB limit
        }
        Shadows.shadowOf(context.contentResolver).setCursor(uri, cursor)

        PublishHelper.publishImage(uri, "png", context)

        assertThat(shadows.broadcastIntents).isEmpty()
        assertThat(ShadowToast.getTextOfLatestToast()).contains("too large")
        val imageFiles = context.cacheDir.listFiles { f -> f.name.startsWith("clipshift_image_") }
        assertThat(imageFiles).isEmpty()
    }

    @Test
    fun `text attachment path blocks send when content exceeds max attachment size`() {
        shadowPackageManager.installPackage(PackageInfo().apply { packageName = "io.heckel.ntfy" })
        Config.save(context, topic = "test-topic", deviceName = "Test Device",
            encryptionEnabled = false, baseUrl = "https://ntfy.sh", maxAttachmentSizeMb = 1)
        val shadows = Shadows.shadowOf(context as Application)

        // 2 MB of text — over the 1 MB limit
        PublishHelper.publish("x".repeat(2 * 1024 * 1024), context)

        assertThat(shadows.broadcastIntents).isEmpty()
        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(context.getString(R.string.toast_content_too_large))
    }
}
