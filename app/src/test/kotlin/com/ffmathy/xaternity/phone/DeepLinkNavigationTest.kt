package com.ffmathy.xaternity.phone

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.phone.launch.DeepLinks
import com.ffmathy.xaternity.phone.launch.HomeLaunch
import com.ffmathy.xaternity.phone.system.SurfacePrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The real app opened by a widget or notification link (DESIGN_SPEC §3.1): a cold start lands on Home with the sheet
 * open, and a link that arrives while History is open returns to Home first. Also the in-context notification
 * permission request on the first timer.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class DeepLinkNavigationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = Core.reset()

    @Test
    fun aLinkOpensHomeWithItsSheetAndALaterLinkLeavesHistory() {
        val activity = Robolectric.buildActivity(MainActivity::class.java, DeepLinks.intent(app, HomeLaunch.Diaper)).setup()
        compose.waitUntilAtLeastOneExists(hasText("Save"), timeoutMillis = 10_000)
        compose.onNodeWithText("Pee").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Save")).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithText("History").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add entry"), timeoutMillis = 5_000)

        // The widget's Feed button while the app shows History.
        activity.newIntent(DeepLinks.intent(app, HomeLaunch.Feed))
        compose.waitUntilAtLeastOneExists(hasText("Starts a timer"), timeoutMillis = 5_000)
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Add entry")).fetchSemanticsNodes().isEmpty() }
    }

    /** The first Start sleep asks for the notification permission in context (DESIGN_SPEC §10.5); once only. */
    @Test
    fun theFirstTimerAsksForTheNotificationPermission() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 10_000)
        compose.onNodeWithText("Start sleep").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Woke up"), timeoutMillis = 5_000)
        compose.waitForIdle()

        val request = shadowOf(controller.get()).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request?.requestedPermissions?.toList())
        assertTrue(SurfacePrefs(app).notificationPermissionAsked)
    }
}
