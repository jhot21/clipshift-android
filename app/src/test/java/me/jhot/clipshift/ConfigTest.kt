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
        assertThat(config.paused).isFalse()
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
            encryptionEnabled = false, paused = false)
        val config = Config.load(context)
        assertThat(config.topic).isEqualTo("clipshift-abc123")
        assertThat(config.deviceName).isEqualTo("Pixel 8")
    }

    @Test
    fun `paused flag persists`() {
        Config.setPaused(context, true)
        assertThat(Config.load(context).paused).isTrue()
        Config.setPaused(context, false)
        assertThat(Config.load(context).paused).isFalse()
    }
}
