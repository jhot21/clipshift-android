package me.jhot.clipshift

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
        Config.save(context, topic = "test-topic", deviceName = "Android",
            encryptionEnabled = false, paused = false, baseUrl = "https://ntfy.sh")
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
        tags: String = "v:1,did:other-device-uuid,type:text,ts:0"
    ): Intent = Intent("io.heckel.ntfy.MESSAGE_RECEIVED").apply {
        putExtra("topic", topic)
        putExtra("title", title)
        putExtra("message", message)
        putExtra("tags", tags)
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
    fun `skips message when paused`() {
        Config.setPaused(context, true)
        val intent = makeIntent(message = "paused content")
        NtfyReceiver().onReceive(context, intent)

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()).isNotEqualTo("paused content")
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
}
