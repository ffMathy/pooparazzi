package com.ffmathy.pooparazzi.phone

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.data.EndResult
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.phone.notify.RunningTimer
import com.ffmathy.pooparazzi.phone.notify.TimerActionReceiver
import com.ffmathy.pooparazzi.phone.notify.TimerNotifications
import com.ffmathy.pooparazzi.phone.notify.TimerState
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
import com.ffmathy.pooparazzi.phone.system.SystemSurfaces
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * The ongoing notification of a running sleep (DESIGN_SPEC §3.3.7, §10.5): content, the silent channel,
 * the lock-screen "Woke up" action, cancelling when the entry ends (also from sync), dismissal and denied permission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TimerNotificationsTest : CoreTestBase() {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val zone = SampleData.ZONE

    @Before
    fun setUp() {
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted(id: Int): Notification? = shadowOf(manager).getNotification(id)

    private fun asleep(language: AppLanguage = AppLanguage.SYSTEM, name: String = "Noa") = TimerState(
        sleep = RunningTimer("sleep-1", SampleData.at(2, 5)),
        lastFeedStartMillis = SampleData.at(1, 40),
        babyName = name,
        language = language,
        zone = zone,
    )

    @Test
    fun aRunningSleepShowsASilentOngoingNotificationWithWokeUp() {
        TimerNotifications(app).render(asleep())
        val notification = posted(TimerNotifications.SLEEP_ID)!!
        assertEquals("Noa is asleep", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Since 02:05", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertFalse("no ticking time", notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertEquals("Asleep", notification.shortCriticalText)
        val action = notification.actions.single()
        assertEquals("Woke up", action.title.toString())
        assertFalse("works from the lock screen", action.isAuthenticationRequired)
        assertNotNull(notification.deleteIntent)

        val channel = manager.getNotificationChannel(TimerNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
    }

    @Test
    fun theTextFollowsTheAppLanguageAndTheMissingName() {
        TimerNotifications(app).render(asleep(language = AppLanguage.DA))
        val danish = posted(TimerNotifications.SLEEP_ID)!!
        assertEquals("Noa sover", danish.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Siden kl. 02:05", danish.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Vågnede", danish.actions.single().title.toString())

        TimerNotifications(app).render(asleep(name = ""))
        assertEquals("Asleep", posted(TimerNotifications.SLEEP_ID)!!.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun breastFeedsNeverPostANotificationAndAnOldOneIsCleared() {
        // A breastfeed notification left by an older version (when breastfeeds were timed).
        manager.notify(
            TimerNotifications.LEGACY_FEED_ID,
            Notification.Builder(app, "legacy").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Noa is breastfeeding").build(),
        )
        val entries = SampleData.scenarioWithRecentFeed()
        val state = TimerState.of(entries, SampleData.settings, SampleData.NOW_1450, zone)
        assertNull("a breast feed is an instant", state.sleep)
        TimerNotifications(app).render(state)
        assertNull(posted(TimerNotifications.LEGACY_FEED_ID))
        assertNull(posted(TimerNotifications.SLEEP_ID))
    }

    @Test
    fun aDismissedNotificationIsNotPostedAgainForThatSleep() {
        val notifications = TimerNotifications(app)
        notifications.render(asleep())
        manager.cancel(TimerNotifications.SLEEP_ID)
        SurfacePrefs(app).dismissedTimers = setOf("sleep-1")
        notifications.render(asleep(name = "Noa B."))
        assertNull(posted(TimerNotifications.SLEEP_ID))

        // The next sleep gets its notification again, and the old dismissal is forgotten.
        notifications.render(asleep().copy(sleep = RunningTimer("sleep-2", SampleData.at(4, 0))))
        assertNotNull(posted(TimerNotifications.SLEEP_ID))
        assertTrue(SurfacePrefs(app).dismissedTimers.isEmpty())
    }

    @Test
    fun withoutThePermissionNothingIsPosted() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        TimerNotifications(app).render(asleep())
        assertNull(posted(TimerNotifications.SLEEP_ID))
    }

    @Test
    fun wokeUpEndsTheSleepWithoutOpeningTheApp() = coreTest(now = SampleData.NOW_0312, seed = SampleData.scenarioAt0312(SampleData.ZONE)) { rig ->
        TimerNotifications(app).render(asleep().copy(sleep = RunningTimer("sample-sleep-0205", SampleData.at(2, 5))))
        val result = TimerActionReceiver.endSleep(app, "sample-sleep-0205")
        runCurrent()
        assertTrue(result is EndResult.Ended)
        assertEquals(SampleData.NOW_0312, rig.entry("sample-sleep-0205")!!.endMillis)
        assertNull(posted(TimerNotifications.SLEEP_ID))

        // A second tap (or one after the watch ended it) changes nothing.
        assertNull(TimerActionReceiver.endSleep(app, "sample-sleep-0205"))
    }

    @Test
    fun theNotificationFollowsTheEntriesIncludingEndsFromTheWatch() = coreTest { rig ->
        // The app-scope observer formats in the device time zone.
        val deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(SampleData.ZONE))
        try {
            followTheEntries(rig)
        } finally {
            TimeZone.setDefault(deviceZone)
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.followTheEntries(rig: CoreTestRig) {
        SystemSurfaces.start(app, Core.graph)
        runCurrent()
        assertNull(posted(TimerNotifications.SLEEP_ID))

        val sleep = rig.repository.startSleep(SampleData.NOW_1432).getOrThrow()
        runCurrent()
        assertEquals("Since 14:32", posted(TimerNotifications.SLEEP_ID)!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())

        // The watch ends the sleep: the entry arrives through sync.
        rig.clock.advanceBy(20 * MINUTE)
        rig.repository.applyRemote(sleep.copy(endMillis = rig.clock.now(), updatedAtMillis = rig.clock.now()))
        runCurrent()
        assertFalse(rig.entry(sleep.id)!!.isRunning)
        assertNull(posted(TimerNotifications.SLEEP_ID))
    }

    /**
     * The first timer asks for the permission (Android 13+); once it is granted, that timer's notification is posted at
     * once, without waiting for the next entry change.
     */
    @Test
    fun grantingThePermissionPostsTheRunningTimer() = coreTest { rig ->
        val deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(SampleData.ZONE))
        try {
            shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            SystemSurfaces.start(app, Core.graph)
            rig.repository.startSleep(SampleData.NOW_1432).getOrThrow()
            runCurrent()
            assertNull("no permission yet", posted(TimerNotifications.SLEEP_ID))

            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            SystemSurfaces.refreshNotifications()
            runCurrent()
            assertNotNull("posted as soon as it is allowed", posted(TimerNotifications.SLEEP_ID))
        } finally {
            TimeZone.setDefault(deviceZone)
        }
    }

    /** Notifications turned on in system settings while the app was away: the running timer is posted on return. */
    @Test
    fun turningNotificationsOnInSystemSettingsPostsTheRunningTimer() = coreTest { rig ->
        val deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(SampleData.ZONE))
        try {
            shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            SystemSurfaces.start(app, Core.graph)
            rig.repository.startSleep(SampleData.NOW_1432).getOrThrow()
            SystemSurfaces.onAppResumed(app)
            runCurrent()
            assertNull(posted(TimerNotifications.SLEEP_ID))

            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            SystemSurfaces.onAppResumed(app)
            runCurrent()
            assertNotNull(posted(TimerNotifications.SLEEP_ID))
        } finally {
            TimeZone.setDefault(deviceZone)
        }
    }
}
