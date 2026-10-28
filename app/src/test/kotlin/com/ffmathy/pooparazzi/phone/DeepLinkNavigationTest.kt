package com.ffmathy.pooparazzi.phone

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.phone.launch.DeepLinks
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
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
        compose.waitUntilAtLeastOneExists(hasText("Then the sides, if you like"), timeoutMillis = 5_000)
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

    /**
     * The watch's "More on phone" sends `pooparazzi://history` (VIEW + BROWSABLE) through RemoteActivityHelper: the
     * phone resolves it to the app and opens History, also when the app is already running on Home.
     */
    @Test
    fun theWatchsMoreOnPhoneLinkOpensHistory() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("pooparazzi://history")).addCategory(Intent.CATEGORY_BROWSABLE)
        val resolved = app.packageManager.queryIntentActivities(link, 0)
        assertEquals(listOf(MainActivity::class.java.name), resolved.map { it.activityInfo.name })
        // Only History is exposed to other apps and the browser; the logging sheets are not.
        val sheet = Intent(Intent.ACTION_VIEW, Uri.parse("pooparazzi://home/feed")).addCategory(Intent.CATEGORY_BROWSABLE)
        assertTrue(app.packageManager.queryIntentActivities(sheet, 0).isEmpty())

        val activity = Robolectric.buildActivity(MainActivity::class.java, Intent(link).setClass(app, MainActivity::class.java)).setup()
        compose.waitUntilAtLeastOneExists(hasText("Add entry"), timeoutMillis = 10_000)

        // Back to Home, then the link again while the app runs.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Add entry")).fetchSemanticsNodes().isEmpty() }
        activity.newIntent(Intent(link).setClass(app, MainActivity::class.java))
        compose.waitUntilAtLeastOneExists(hasText("Add entry"), timeoutMillis = 5_000)
    }
}
