package me.jhot.clipshift

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34])
class ClipShiftServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NotificationHelper.createChannel(context)
    }

    @Test
    fun `service posts foreground notification on normal start`() {
        val controller = Robolectric.buildService(ClipShiftService::class.java).create().startCommand(0, 1)
        val service = controller.get()

        val nm = service.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(nm).allNotifications
        assertThat(notifications).isNotEmpty()
    }

    @Test
    fun `service returns START_STICKY on normal start`() {
        val controller = Robolectric.buildService(ClipShiftService::class.java).create()
        val service = controller.get()
        val result = service.onStartCommand(null, 0, 1)
        assertThat(result).isEqualTo(android.app.Service.START_STICKY)
    }
}
