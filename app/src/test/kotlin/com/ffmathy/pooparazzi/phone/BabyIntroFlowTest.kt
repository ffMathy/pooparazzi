package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.WelcomePrefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first run's "Your baby" screen in the real app (a fresh install of a build without accounts, so there is no
 * Welcome before it): it says both are optional, Continue saves the name given, Skip saves nothing, and neither is
 * asked again. The introduction follows it, and is stepped through to Get started or skipped.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class BabyIntroFlowTest {
    /**
     * Before the activity starts: a fresh install's baby, with neither a name nor a birth date. The settings outlive a
     * test in the same process, so a name saved by an earlier test is cleared here.
     */
    @get:Rule(order = 0)
    val freshBaby = object : ExternalResource() {
        override fun before() = runBlocking {
            val settings = Core.ensureInitialized(ApplicationProvider.getApplicationContext<Application>()).settings
            settings.renameBaby(Baby.FIRST_ID, "")
            settings.setBabyBirthDate(Baby.FIRST_ID, null)
            Unit
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        Core.reset()
        AppAccounts.reset()
    }

    @Test
    fun continueSavesTheNameAndOpensHome() {
        compose.waitUntilAtLeastOneExists(hasText("Your baby"), timeoutMillis = 10_000)
        compose.onNodeWithText("Both are optional", substring = true).assertExists()
        compose.onNodeWithText("Add birth date").assertExists()
        compose.onNodeWithText("The birth date is optional, but it improves the predictions and estimates", substring = true).assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Maja")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        // The introduction comes next: Next through its pages, and Get started on the last one.
        compose.waitUntilAtLeastOneExists(hasText("Welcome to Pooparazzi"), timeoutMillis = 5_000)
        repeat(5) {
            compose.onNodeWithText("Next").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Always at hand").assertExists()
        compose.onNodeWithText("Get started").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        assertTrue(WelcomePrefs(app).introSeen)
        compose.waitUntil(timeoutMillis = 5_000) { runBlocking { Core.settings.current().babyName } == "Maja" }
        assertNull("no birth date was given", runBlocking { Core.settings.current().selectedBaby.born })
        assertTrue(WelcomePrefs(app).babyAsked)
    }

    @Test
    fun skipSavesNothing() {
        compose.waitUntilAtLeastOneExists(hasText("Your baby"), timeoutMillis = 10_000)
        compose.onNode(hasSetTextAction()).performTextInput("Maja")
        compose.onNodeWithText("Skip for now").performScrollTo().performClick()
        // The introduction can be skipped from its first page.
        compose.waitUntilAtLeastOneExists(hasText("Welcome to Pooparazzi"), timeoutMillis = 5_000)
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        assertTrue(WelcomePrefs(app).introSeen)
        assertEquals("", runBlocking { Core.settings.current().babyName })
        assertTrue(WelcomePrefs(app).babyAsked)
    }
}
