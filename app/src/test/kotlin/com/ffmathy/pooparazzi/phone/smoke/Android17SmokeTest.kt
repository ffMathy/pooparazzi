package com.ffmathy.pooparazzi.phone.smoke

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.phone.MainActivity
import com.ffmathy.pooparazzi.phone.notify.RunningTimer
import com.ffmathy.pooparazzi.phone.notify.TimerNotifications
import com.ffmathy.pooparazzi.phone.notify.TimerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Android 17 (API 37, above the app's targetSdk 36): the real app cold-starts, and the timer notifications take the
 * API 37 path (`Notification.MetricStyle` with fixed-time metrics, the Live Update request). Without Compose test
 * rules: Espresso cannot inject input on API 37 under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
class Android17SmokeTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = Core.reset()

    @Test
    fun coldStartResumesAndRecreates() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        assertEquals(Lifecycle.State.RESUMED, controller.get().lifecycle.currentState)
        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Lifecycle.State.RESUMED, controller.get().lifecycle.currentState)
        controller.pause().stop().destroy()
    }

    @Test
    fun timerNotificationsUseTheMetricStyle() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val state = TimerState(
            sleep = RunningTimer("sleep-1", SampleData.at(2, 5)),
            lastFeedStartMillis = SampleData.at(1, 40),
            babyName = "Noa",
            language = AppLanguage.DA,
            zone = SampleData.ZONE,
        )
        TimerNotifications(app).render(state)

        val manager = app.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(manager).getNotification(TimerNotifications.SLEEP_ID)
        assertNotNull("the sleep notification", posted)
        assertEquals(Notification.MetricStyle::class.java.name, posted!!.extras.getString(Notification.EXTRA_TEMPLATE))
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
