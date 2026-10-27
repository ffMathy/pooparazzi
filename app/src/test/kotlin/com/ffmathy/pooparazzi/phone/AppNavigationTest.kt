package com.ffmathy.pooparazzi.phone

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ffmathy.pooparazzi.core.Core
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real app: [PooparazziApp] builds the core graph (Room, DataStore, sync without Play Services), [MainActivity]
 * waits for the appearance inputs, then Home opens History and Settings and comes back.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class AppNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() = Core.reset()

    @Test
    fun homeOpensHistoryAndSettingsAndComesBack() {
        compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 10_000)
        compose.onNodeWithText("Diaper").assertExists()
        compose.onNodeWithText("Start sleep").assertExists()

        compose.onNodeWithText("History").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Back"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 5_000)

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Back"), timeoutMillis = 5_000)
        compose.onNodeWithText("Settings").assertExists()
    }

    /** A second Back tap while History is still animating out must not pop Home as well (an empty screen). */
    @Test
    fun aDoubleTapOnBackReturnsToHomeOnce() {
        compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 10_000)
        compose.onNodeWithText("History").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add entry"), timeoutMillis = 5_000)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.autoAdvance = true

        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Add entry")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Feed").assertExists()
    }

    @Test
    fun historyAddsAPastEntryAndSettingsShowTheFontLicences() {
        compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 10_000)
        compose.onNodeWithText("History").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add entry"), timeoutMillis = 5_000)
        compose.onNodeWithText("Add entry").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add past entry"), timeoutMillis = 5_000)
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Add past entry")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()

        compose.waitUntilAtLeastOneExists(hasContentDescription("Settings"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Font licences"), timeoutMillis = 5_000)
        compose.onNodeWithText("Font licences").performScrollTo().performClick()
        // The OFL texts are packaged from third_party/fonts.
        compose.waitUntilAtLeastOneExists(hasText("SIL OPEN FONT LICENSE", substring = true), timeoutMillis = 5_000)
        compose.onNodeWithText("Google Sans Flex").assertExists()
    }

    @Test
    fun feedOpensTheFeedSheet() {
        compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 10_000)
        compose.onNodeWithText("Feed").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Starts a timer"), timeoutMillis = 5_000)
        compose.onNodeWithText("Formula").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Starts a timer")).fetchSemanticsNodes().isEmpty() }
    }
}
