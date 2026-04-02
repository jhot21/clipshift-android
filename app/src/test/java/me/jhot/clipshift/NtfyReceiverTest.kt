package me.jhot.clipshift

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34])
class NtfyReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NotificationHelper.createChannel(context)
        Crypto.keyDeriver = BcArgon2idDeriver()
        Config.save(context, topic = "test-topic", deviceName = "Android",
            encryptionEnabled = false, baseUrl = "https://ntfy.sh")
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("clipshift_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun makeIntent(
        topic: String = "test-topic",
        title: String = "Desktop",
        message: String = "synced text",
        tags: String = "v:1,did:other-device-uuid,type:text,ts:0",
        baseUrl: String? = null,
    ): Intent = Intent("io.heckel.ntfy.MESSAGE_RECEIVED").apply {
        putExtra("topic", topic)
        putExtra("title", title)
        putExtra("message", message)
        putExtra("tags", tags)
        if (baseUrl != null) putExtra("base_url", baseUrl)
    }

    @Test
    fun `posts notification with Set Clipboard action for valid MESSAGE_RECEIVED broadcast`() {
        val intent = makeIntent()
        NtfyReceiver().onReceive(context, intent)

        val nm = context.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(nm).allNotifications
        assertThat(notifications).isNotEmpty()

        val notification = notifications.last()
        val setClipAction = notification.actions?.firstOrNull {
            it.title?.toString() == context.getString(R.string.action_set_clipboard)
        }
        assertThat(setClipAction).isNotNull()

        val savedIntent = shadowOf(setClipAction!!.actionIntent).savedIntent
        assertThat(savedIntent.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("synced text")
    }

    @Test
    fun `skips message from own device`() {
        val cfg = Config.load(context)
        val intent = makeIntent(
            message = "own message",
            tags = "v:1,did:${cfg.deviceId},type:text,ts:0"
        )
        NtfyReceiver().onReceive(context, intent)

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()).isNotEqualTo("own message")
    }

    @Test
    fun `skips message on topic mismatch`() {
        val intent = makeIntent(topic = "other-topic", message = "wrong topic")
        NtfyReceiver().onReceive(context, intent)

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()).isNotEqualTo("wrong topic")
    }

    @Test
    fun `skips message with unsupported version`() {
        val intent = makeIntent(tags = "v:2,did:other,type:text,ts:0", message = "v2 content")
        NtfyReceiver().onReceive(context, intent)

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()).isNotEqualTo("v2 content")
    }

    @Test
    fun `skips message with unsupported content type`() {
        val intent = makeIntent(tags = "v:1,did:other,type:image,ts:0", message = "image content")
        NtfyReceiver().onReceive(context, intent)

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()).isNotEqualTo("image content")
    }

    @Test
    fun `skips message with mismatched baseUrl`() {
        val intent = makeIntent(
            message = "wrong server",
            baseUrl = "https://other.server.com"
        )
        NtfyReceiver().onReceive(context, intent)

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            .isNotEqualTo("wrong server")
    }

    @Test
    fun `decrypts encrypted message and posts Set Clipboard action`() {
        mockkObject(Config)
        try {
            every { Config.loadPassphrase(any()) } returns "s3cr3t"

            val plaintext = "encrypted clipboard content"
            val ciphertext = Crypto.encrypt(plaintext.toByteArray(Charsets.UTF_8), "s3cr3t")

            val intent = makeIntent(
                message = ciphertext,
                tags = "v:1,did:other-device,type:text,encrypted,ts:0",
            )
            // Use synchronous executor so decryption completes before assertions
            NtfyReceiver(executor = Runnable::run).onReceive(context, intent)

            val nm = context.getSystemService(NotificationManager::class.java)
            val notification = shadowOf(nm).allNotifications.last()
            val setClipAction = notification.actions?.firstOrNull {
                it.title?.toString() == context.getString(R.string.action_set_clipboard)
            }
            assertThat(setClipAction).isNotNull()
            val savedIntent = shadowOf(setClipAction!!.actionIntent).savedIntent
            assertThat(savedIntent.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(plaintext)
        } finally {
            unmockkObject(Config)
        }
    }

    @Test
    fun `posts error notification when passphrase missing for encrypted message`() {
        mockkObject(Config)
        try {
            every { Config.loadPassphrase(any()) } returns null

            val intent = makeIntent(
                message = "some-ciphertext",
                tags = "v:1,did:other-device,type:text,encrypted,ts:0",
            )
            NtfyReceiver(executor = Runnable::run).onReceive(context, intent)

            val nm = context.getSystemService(NotificationManager::class.java)
            val notification = shadowOf(nm).allNotifications.last()
            assertThat(notification.extras.getString("android.text"))
                .contains(context.getString(R.string.notification_error_passphrase))
        } finally {
            unmockkObject(Config)
        }
    }

    @Test
    fun `allows message with no baseUrl extra (older ntfy)`() {
        val intent = makeIntent(message = "no base url")
        NtfyReceiver().onReceive(context, intent)

        val nm = context.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(nm).allNotifications
        assertThat(notifications).isNotEmpty()
    }
}
