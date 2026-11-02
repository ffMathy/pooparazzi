package com.ffmathy.pooparazzi.phone

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.domain.ForecastKind
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.phone.notify.ForecastNotifications
import com.ffmathy.pooparazzi.phone.notify.TimerNotifications
import com.ffmathy.pooparazzi.phone.notify.TimerState
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
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

/**
 * The prediction notifications (DESIGN_SPEC §10.5): one silent, ongoing notification per predicted kind and baby, each
 * on its own channel (on until the parent turns it off), gone once the kind is no longer predicted, and not posted
 * again once swiped away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ForecastNotificationsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val zone = SampleData.ZONE

    @Before
    fun setUp() {
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** The sample week at 14:32, awake: the next feed and the next sleep are predicted, poop is not. */
    private fun awake(settings: com.ffmathy.pooparazzi.core.settings.Settings = SampleData.settings): TimerState =
        TimerState.of(SampleData.history(), settings, SampleData.NOW_1432, zone)

    private fun posted(kind: ForecastKind, babyId: String = Baby.FIRST_ID): Notification? =
        shadowOf(manager).getNotification(ForecastNotifications.tagFor(babyId), ForecastNotifications.notificationId(kind))

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    @Test
    fun eachPredictedKindHasASilentOngoingNotificationOnItsOwnChannel() {
        val state = awake()
        assertNotNull(state.forecasts[Baby.FIRST_ID]?.feed)
        assertTrue(ForecastNotifications(app).render(state))

        val feed = posted(ForecastKind.FEED)!!
        assertTrue(feed.title(), feed.title().startsWith("Next feed in "))
        assertTrue(feed.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("Around "))
        assertTrue(feed.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("forecast_feed", feed.channelId)
        assertNotNull(feed.deleteIntent)
        val sleep = posted(ForecastKind.SLEEP)!!
        assertTrue(sleep.title(), sleep.title().startsWith("Next sleep in "))
        assertEquals("forecast_sleep", sleep.channelId)
        assertNull("poop is not predicted in the sample week", posted(ForecastKind.POOP))

        for (kind in ForecastKind.entries) {
            val channel = manager.getNotificationChannel(ForecastNotifications.channelId(kind))
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
            assertNull(channel.sound)
            assertEquals(ForecastNotifications.GROUP_ID, channel.group)
        }
    }

    @Test
    fun aChannelTurnedOffShowsNothingAndTheOthersStay() {
        val notifications = ForecastNotifications(app)
        notifications.render(awake())
        val channel = manager.getNotificationChannel(ForecastNotifications.channelId(ForecastKind.FEED))
        channel.importance = NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(channel)

        notifications.render(awake())
        assertNull(posted(ForecastKind.FEED))
        assertNotNull(posted(ForecastKind.SLEEP))
        assertEquals(false, ForecastNotifications.channelStates(app)[ForecastKind.FEED])
        assertEquals(true, ForecastNotifications.channelStates(app)[ForecastKind.SLEEP])
    }

    @Test
    fun aSwipedAwayEstimateIsNotPostedAgainButTheNextOneIs() {
        val notifications = ForecastNotifications(app)
        val state = awake()
        notifications.render(state)
        val feed = state.forecasts.getValue(Baby.FIRST_ID).feed!!
        manager.cancel(ForecastNotifications.tagFor(Baby.FIRST_ID), ForecastNotifications.FEED_ID)
        SurfacePrefs(app).dismissedForecasts = setOf("${Baby.FIRST_ID}|FEED|${feed.atMillis}")
        notifications.render(state)
        assertNull(posted(ForecastKind.FEED))

        // A new estimate (here: none at all for a while, then one again) shows again.
        notifications.render(state.copy(forecasts = emptyMap()))
        assertTrue(SurfacePrefs(app).dismissedForecasts.isEmpty())
        notifications.render(state)
        assertNotNull(posted(ForecastKind.FEED))
    }

    @Test
    fun anEstimateNoLongerPredictedGoes() {
        val notifications = ForecastNotifications(app)
        notifications.render(awake())
        assertNotNull(posted(ForecastKind.FEED))
        assertFalse(notifications.render(awake().copy(forecasts = emptyMap())))
        assertNull(posted(ForecastKind.FEED))
        assertNull(posted(ForecastKind.SLEEP))
    }

    @Test
    fun aNewbornHasNoSleepPrediction() {
        val born = SampleData.TODAY.minusWeeks(3)
        val settings = SampleData.settings.withBabyBirthDate(Baby.FIRST_ID, born)
        ForecastNotifications(app).render(awake(settings))
        assertNull(posted(ForecastKind.SLEEP))
        assertNotNull(posted(ForecastKind.FEED))
    }

    @Test
    fun withoutPermissionNothingIsPosted() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(ForecastNotifications(app).render(awake()))
        assertNull(posted(ForecastKind.FEED))
    }

    @Test
    fun theSleepNotificationsRenderTheirPredictionsToo() {
        TimerNotifications(app).render(awake())
        assertNotNull(posted(ForecastKind.FEED))
    }

    @Test
    fun asleepTheSleepPredictionIsTheWakeUpInDanish() {
        val state = TimerState.of(SampleData.historyAt0312(), SampleData.settings.copy(language = AppLanguage.DA), SampleData.NOW_0312, zone)
        ForecastNotifications(app).render(state)
        val wake = posted(ForecastKind.SLEEP)!!
        assertTrue(wake.title(), wake.title().startsWith("Ventes at vågne om "))
    }
}
