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
import org.junit.After
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

    private val deviceZone = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(deviceZone)

    @Before
    fun setUp() {
        // The receiver formats in the device time zone, as the app-scope observer does.
        TimeZone.setDefault(TimeZone.getTimeZone(SampleData.ZONE))
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted(id: Int): Notification? = shadowOf(manager).getNotification(id)

    private fun asleep(language: AppLanguage = AppLanguage.SYSTEM, name: String = "Noa") = TimerState(
        sleep = RunningTimer("sleep-1", SampleData.at(2, 5)),
        now = SampleData.NOW_0312,
        babyName = name,
        language = language,
        zone = zone,
    )

    private fun text(notification: Notification): String = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun bigText(notification: Notification): String = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()

    @Test
    fun aRunningSleepShowsASilentOngoingNotificationWithWokeUp() {
        TimerNotifications(app).render(asleep())
        val notification = posted(TimerNotifications.SLEEP_ID)!!
        assertEquals("Noa is asleep", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("1h 07m · since 02:05", text(notification))
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

    /** Expanded: the last feed with its order, the last diaper and today's counts, each relative and absolute. */
    @Test
    fun expandedItShowsTheLastFeedTheLastDiaperAndToday() {
        val state = TimerState.of(SampleData.scenarioAt0312(zone), SampleData.settings, SampleData.NOW_0312 + 30_000, zone)
        assertEquals("the time is whole minutes", SampleData.NOW_0312, state.now)
        TimerNotifications(app).render(state)
        val lines = bigText(posted(TimerNotifications.SLEEP_ID)!!).lines()
        assertEquals("1h 07m · since 02:05", lines.first())
        assertTrue(lines.toString(), lines.any { it.startsWith("Fed ") && it.contains(" ago · ") })
        assertTrue(lines.toString(), lines.last().startsWith("Today · 1 feed · "))
    }

    /** A sleep started seconds ago runs, though the shown time is whole minutes. */
    @Test
    fun aSleepStartedWithinTheMinuteIsRunning() {
        val start = SampleData.NOW_1432 + 40_000
        val sleep = com.ffmathy.pooparazzi.core.model.Entry(
            id = "fresh",
            kind = com.ffmathy.pooparazzi.core.model.EntryKind.SLEEP,
            startMillis = start,
            updatedAtMillis = start,
        )
        val state = TimerState.of(listOf(sleep), SampleData.settings, start + 5_000, zone)
        assertEquals(RunningTimer("fresh", start), state.sleep)
        assertEquals(SampleData.NOW_1432, state.now)
    }

    /** The duration follows the clock: a non-waking alarm re-renders at the next minute while the sleep shows. */
    @Test
    fun aMinuteAlarmKeepsTheDurationCurrent() {
        val alarms = shadowOf(app.getSystemService(android.app.AlarmManager::class.java))
        TimerNotifications(app).render(asleep())
        val alarm = alarms.nextScheduledAlarm!!
        assertEquals(android.app.AlarmManager.RTC, alarm.type)
        assertEquals(0L, alarm.triggerAtTime % 60_000L)

        TimerNotifications(app).render(asleep().copy(sleep = null))
        assertNull("no sleep, no alarm", alarms.nextScheduledAlarm)
    }

    @Test
    fun theTextFollowsTheAppLanguageAndTheMissingName() {
        TimerNotifications(app).render(asleep(language = AppLanguage.DA))
        val danish = posted(TimerNotifications.SLEEP_ID)!!
        assertEquals("Noa sover", danish.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("1t 07m · siden kl. 02:05", text(danish))
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

        // It leaves "woke up at 03:12" with the Earlier offsets, as the undo card in the app.
        val wokeUp = posted(TimerNotifications.WOKE_UP_ID)!!
        assertEquals("Noa woke up at 03:12", wokeUp.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Slept 1h 07m · 02:05 – 03:12", text(wokeUp))
        assertEquals(listOf("−5m", "−10m", "−15m"), wokeUp.actions.map { it.title.toString() })
        assertTrue(wokeUp.actions.none { it.isAuthenticationRequired })

        // A second tap (or one after the watch ended it) changes nothing.
        assertNull(TimerActionReceiver.endSleep(app, "sample-sleep-0205"))
    }

    /** The offsets move the wake-up time from the tap, never cumulatively; tapping the selected one moves it back. */
    @Test
    fun earlierOffsetsMoveTheWakeUpTime() = coreTest(now = SampleData.NOW_0312, seed = SampleData.scenarioAt0312(SampleData.ZONE)) { rig ->
        val id = "sample-sleep-0205"
        TimerActionReceiver.endSleep(app, id)
        TimerActionReceiver.moveWakeUp(app, id, SampleData.NOW_0312, minutes = -10, current = 0)
        assertEquals(SampleData.at(3, 2), rig.entry(id)!!.endMillis)
        val moved = posted(TimerNotifications.WOKE_UP_ID)!!
        assertEquals("Noa woke up at 03:02", moved.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(listOf("−5m", "✓ −10m", "−15m"), moved.actions.map { it.title.toString() })

        TimerActionReceiver.moveWakeUp(app, id, SampleData.NOW_0312, minutes = -15, current = -10)
        assertEquals("not cumulative", SampleData.at(2, 57), rig.entry(id)!!.endMillis)

        TimerActionReceiver.moveWakeUp(app, id, SampleData.NOW_0312, minutes = -15, current = -15)
        assertEquals("back to the tap", SampleData.NOW_0312, rig.entry(id)!!.endMillis)
        assertEquals(listOf("−5m", "−10m", "−15m"), posted(TimerNotifications.WOKE_UP_ID)!!.actions.map { it.title.toString() })
    }

    /** An offset that would end the sleep before it started is left out. */
    @Test
    fun offsetsBeforeTheStartAreLeftOut() {
        val state = asleep().copy(sleep = null)
        val sleep = com.ffmathy.pooparazzi.core.model.Entry(
            id = "short",
            kind = com.ffmathy.pooparazzi.core.model.EntryKind.SLEEP,
            startMillis = SampleData.at(3, 0),
            endMillis = SampleData.NOW_0312,
            updatedAtMillis = SampleData.NOW_0312,
        )
        val notification = TimerNotifications(app).buildWokeUp(state, sleep, SampleData.NOW_0312, 0)
        assertEquals(listOf("−5m", "−10m"), notification.actions.map { it.title.toString() })
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
        assertEquals("<1m · since 14:32", text(posted(TimerNotifications.SLEEP_ID)!!))

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
