package com.ffmathy.xaternity.phone.smoke

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.model.isSleep
import com.ffmathy.xaternity.phone.MainActivity
import com.ffmathy.xaternity.phone.XaternityApp
import com.ffmathy.xaternity.phone.notify.TimerNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Platform smoke tests of the phone app on every API level from minSdk 30 to Android 16 (36): a cold start of the real
 * [XaternityApp] and [MainActivity] (splash, edge to edge, Core.init with Room on disk and no Play Services, the system
 * surfaces), a recreation, and the first sleep's ongoing notification end to end, including its lock-screen
 * "Woke up" action delivered as a real broadcast. Android 17 is in [Android17SmokeTest] (Espresso cannot inject input
 * on API 37 under Robolectric: `InputManager.getInstance()` is gone).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 31, 33, 34, 35, 36], qualifiers = "w390dp-h844dp-xxhdpi")
class PhonePlatformSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notifications get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        assertTrue("The manifest application is XaternityApp", app is XaternityApp)
    }

    @After
    fun tearDown() = Core.reset()

    @Test
    fun coldStartShowsHomeAndSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 30_000)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.recreate()
            compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 30_000)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun theFirstSleepPostsTheTimerNotificationAndItsWokeUpActionEndsIt() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 30_000)
            compose.onNodeWithText("Start sleep").performClick()
            compose.waitUntilAtLeastOneExists(hasText("Woke up"), timeoutMillis = 30_000)
            compose.waitUntil(timeoutMillis = 30_000) { shadowOf(notifications).getNotification(TimerNotifications.SLEEP_ID) != null }

            val posted = shadowOf(notifications).getNotification(TimerNotifications.SLEEP_ID)!!
            assertTrue("ongoing", posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNotNull("channel", notifications.getNotificationChannel(posted.channelId))
            val wokeUp = posted.actions.single { it.title.toString() == "Woke up" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) assertTrue("lock-screen action", !wokeUp.isAuthenticationRequired)

            // The lock-screen action: a broadcast to the (unexported) receiver, without opening the app.
            wokeUp.actionIntent.send()
            compose.waitUntil(timeoutMillis = 30_000) {
                shadowOf(Looper.getMainLooper()).idle()
                runBlocking { Core.repository.observeEntries().first().none { it.isSleep && it.isRunning } }
            }
            compose.waitUntil(timeoutMillis = 30_000) { shadowOf(notifications).getNotification(TimerNotifications.SLEEP_ID) == null }
            assertNull(shadowOf(notifications).getNotification(TimerNotifications.SLEEP_ID))
        }
    }
}
