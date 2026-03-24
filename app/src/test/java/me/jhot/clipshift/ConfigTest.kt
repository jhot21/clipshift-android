package me.jhot.clipshift

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34])
class ConfigTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("clipshift_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `defaults are correct on first load`() {
        val config = Config.load(context)
        assertThat(config.deviceName).isNotEmpty()  // defaults to Build.MODEL
        assertThat(config.topic).isEmpty()
        assertThat(config.encryptionEnabled).isFalse()
        assertThat(config.deviceId).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }

    @Test
    fun `device_id is stable across loads`() {
        val id1 = Config.load(context).deviceId
        val id2 = Config.load(context).deviceId
        assertThat(id1).isEqualTo(id2)
    }

    @Test
    fun `save and reload round-trips all fields`() {
        Config.save(context, topic = "clipshift-abc123", deviceName = "Pixel 8",
            encryptionEnabled = false, baseUrl = "https://ntfy.sh")
        val config = Config.load(context)
        assertThat(config.topic).isEqualTo("clipshift-abc123")
        assertThat(config.deviceName).isEqualTo("Pixel 8")
    }

    @Test
    fun `baseUrl defaults to https ntfy sh when not saved`() {
        val config = Config.load(context)
        assertThat(config.baseUrl).isEqualTo("https://ntfy.sh")
    }

    @Test
    fun `baseUrl round-trips through save and load`() {
        Config.save(context, topic = "t", deviceName = "d",
            encryptionEnabled = false, baseUrl = "https://my.server.com")
        assertThat(Config.load(context).baseUrl).isEqualTo("https://my.server.com")
    }

    @Test
    fun `baseUrl trailing slash is stripped on save`() {
        Config.save(context, topic = "t", deviceName = "d",
            encryptionEnabled = false, baseUrl = "https://ntfy.sh/")
        assertThat(Config.load(context).baseUrl).isEqualTo("https://ntfy.sh")
    }

    @Test
    fun `baseUrl multiple trailing slashes are stripped on save`() {
        Config.save(context, topic = "t", deviceName = "d",
            encryptionEnabled = false, baseUrl = "https://ntfy.sh///")
        assertThat(Config.load(context).baseUrl).isEqualTo("https://ntfy.sh")
    }

    @Test
    fun `baseUrl blank value falls back to https ntfy sh`() {
        Config.save(context, topic = "t", deviceName = "d",
            encryptionEnabled = false, baseUrl = "   ")
        assertThat(Config.load(context).baseUrl).isEqualTo("https://ntfy.sh")
    }
}
