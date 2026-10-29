package com.ffmathy.pooparazzi.wear.ongoing

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.wear.ongoing.OngoingActivity
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.wear.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Ongoing Activity for a running sleep (DESIGN_SPEC §3.8, §10.6); breast feeds are instants and have none. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RunningActivitiesTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val notifications: NotificationManager get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        RunningActivities.invalidate()
    }

    @After
    fun tearDown() = RunningActivities.invalidate()

    @Test
    fun aRunningSleepShowsAsleepSinceItsStartWithoutAStopwatch() {
        RunningActivities(app).sync(summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, SampleData.ZONE))
        val notification = shadowOf(notifications).getNotification(1001)
        assertNotNull(notification)
        assertEquals("Asleep since 02:05", notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(NotificationCompat.CATEGORY_STOPWATCH, notification.category)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(false, notification.extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER))
        val activity = OngoingActivity.recoverOngoingActivity(app, 1001)
        assertNotNull(activity)
        assertEquals(XDrawables.sleepRunning, activity?.staticIcon?.resId)
        assertEquals("Asleep since 02:05", activity?.status?.getText(app, 0L).toString())
        val touch = shadowOf(activity?.touchIntent).savedIntent
        assertEquals(MainActivity.OPEN_MAIN, touch.getStringExtra(MainActivity.EXTRA_OPEN))
        // Nothing else runs.
        assertNull(shadowOf(notifications).getNotification(1002))
    }

    @Test
    fun aBreastFeedHasNoActivityAndAnOldOneIsRemoved() {
        // The running-breastfeed activity of an older version (when breastfeeds were timed).
        notifications.notify(
            LEGACY_NOTIFICATION_FEED,
            NotificationCompat.Builder(app, "legacy").setSmallIcon(XDrawables.breast).setContentTitle("Breastfeeding since 14:32").build(),
        )
        RunningActivities(app).sync(summarize(SampleData.scenarioWithRecentFeed(), SampleData.NOW_1450, SampleData.ZONE))
        assertNull(shadowOf(notifications).getNotification(LEGACY_NOTIFICATION_FEED))
        assertNull("nothing runs: a breast feed is an instant", shadowOf(notifications).getNotification(1001))
    }

    @Test
    fun withoutPermissionNothingIsPostedAndNothingFails() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        RunningActivities(app).sync(summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, SampleData.ZONE))
        assertNull(shadowOf(notifications).getNotification(1001))
    }

    @Test
    fun withSeveralBabiesTheActivityNamesTheCurrentBabyAndFollowsASwitch() {
        val activities = RunningActivities(app)
        activities.sync(summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, SampleData.ZONE), baby = "Noa")
        fun title() = shadowOf(notifications).getNotification(1001)?.extras?.getString(NotificationCompat.EXTRA_TITLE)
        assertEquals("Noa · Asleep since 02:05", title())
        assertEquals("Noa · Asleep since 02:05", OngoingActivity.recoverOngoingActivity(app, 1001)?.status?.getText(app, 0L).toString())
        assertEquals("Noa, asleep since 02:05", OngoingActivity.recoverOngoingActivity(app, 1001)?.contentDescription)

        // Switched to Ida, asleep too: the activity is hers.
        val ida = Entry.sleep(SampleData.at(2, 30), null, id = "ida-sleep", babyId = SampleData.TWIN_ID)
        activities.sync(summarize(listOf(ida), SampleData.NOW_0312, SampleData.ZONE), baby = "Ida")
        assertEquals("Ida · Asleep since 02:30", title())

        // Renamed on the phone: posted again with the new name.
        activities.sync(summarize(listOf(ida), SampleData.NOW_0312, SampleData.ZONE), baby = "Ida Marie")
        assertEquals("Ida Marie · Asleep since 02:30", title())

        // Switched to an awake baby: no activity.
        activities.sync(summarize(emptyList(), SampleData.NOW_0312, SampleData.ZONE), baby = "Noa")
        assertNull(shadowOf(notifications).getNotification(1001))
    }
}
