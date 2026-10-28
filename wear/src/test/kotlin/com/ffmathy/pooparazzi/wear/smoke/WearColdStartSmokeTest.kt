package com.ffmathy.pooparazzi.wear.smoke

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.wear.MainActivity
import com.ffmathy.pooparazzi.wear.WearApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Cold start of the real [WearApp] and [MainActivity] (Room on disk, the Data Layer without Play Services), the
 * entry points the tile, complications and Ongoing Activity use, and the first timer: the in-context notification
 * permission request and the Ongoing Activity notification.
 *
 * MainActivity composes `rememberAmbientModeManager()`, which links against the Wear SDK shared library
 * (`com.google.wear.services.ambient`) with no fallback. Robolectric has no Wear SDK, so these tests run on API 36
 * (Wear OS 6, where the library exists) with the test-only stand-in in `com.google.wear`. On a watch without that
 * library (Wear OS 3, API 30) the same launch throws NoClassDefFoundError: see the platform review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class WearColdStartSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notifications get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        assertTrue("The manifest application is WearApp", app is WearApp)
    }

    @After
    fun tearDown() = Core.reset()

    @Test
    fun coldStartShowsMain() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntilAtLeastOneExists(hasContentDescription(START_SLEEP), timeoutMillis = 30_000)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun tileAndComplicationEntryPointsOpenTheApp() {
        // "end_feed" is an older tile's End feed (when breastfeeds were timed): it now opens Main.
        val opens = listOf(MainActivity.OPEN_MAIN, MainActivity.OPEN_FEED_TYPE, "end_feed", MainActivity.OPEN_TODAY)
        for (open in opens) {
            ActivityScenario.launch<MainActivity>(MainActivity.intent(app, open)).use { scenario ->
                compose.waitForIdle()
                assertEquals(open, Lifecycle.State.RESUMED, scenario.state)
            }
        }
        // A Logged link for an entry that no longer exists (undone elsewhere) must not crash.
        ActivityScenario.launch<MainActivity>(MainActivity.intent(app, MainActivity.OPEN_LOGGED, "missing-entry")).use { scenario ->
            compose.waitForIdle()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /** The first Start sleep asks for POST_NOTIFICATIONS in context (API 33+). */
    @Test
    fun theFirstTimerAsksForTheNotificationPermission() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntilAtLeastOneExists(hasContentDescription(START_SLEEP), timeoutMillis = 30_000)
            compose.onNodeWithContentDescription(START_SLEEP).performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription(WOKE_UP, substring = true), timeoutMillis = 30_000)
            compose.waitForIdle()
            scenario.onActivity { activity ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val request = shadowOf(activity).lastRequestedPermission
                    assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request?.requestedPermissions?.toList())
                }
            }
        }
    }

    /** With the permission, a running sleep posts its Ongoing Activity notification through the SurfaceUpdater. */
    @Test
    fun aRunningSleepPostsTheOngoingActivity() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntilAtLeastOneExists(hasContentDescription(START_SLEEP), timeoutMillis = 30_000)
            compose.onNodeWithContentDescription(START_SLEEP).performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription(WOKE_UP, substring = true), timeoutMillis = 30_000)
            compose.waitUntil(timeoutMillis = 30_000) { shadowOf(notifications).getNotification(SLEEP_NOTIFICATION) != null }
            val posted = shadowOf(notifications).getNotification(SLEEP_NOTIFICATION)
            assertNotNull(posted)
            assertTrue("ongoing", posted!!.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNotNull("channel exists", notifications.getNotificationChannel(posted.channelId))
        }
    }

    private companion object {
        const val START_SLEEP = "Start sleep"
        const val WOKE_UP = "Woke up"
        const val SLEEP_NOTIFICATION = 1001
    }
}
