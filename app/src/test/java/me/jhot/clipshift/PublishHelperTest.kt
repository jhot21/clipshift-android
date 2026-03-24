package me.jhot.clipshift

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
        assertThat(broadcast?.getStringExtra("baseUrl")).isEqualTo("https://my.server.com")
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
}
