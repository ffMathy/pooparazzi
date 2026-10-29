package com.ffmathy.pooparazzi.phone

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
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
 * waits for the appearance inputs, then Home opens History and Settings and comes back, and History opens Statistics.
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

    /** History's `chart` button opens Statistics (empty: nothing is logged in a new app); Back returns to History. */
    @Test
    fun historyOpensStatisticsAndBackReturnsToHistory() {
        openStatistics()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add entry"), timeoutMillis = 5_000)
    }

    /** A second Back tap while Statistics is still animating out must not pop History as well (back on Home). */
    @Test
    fun aDoubleTapOnBackFromStatisticsReturnsToHistoryOnce() {
        openStatistics()
        // Statistics' own Back, by node: History's Back is composed again during the exit transition.
        val id = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().id
        val back = SemanticsMatcher("node $id") { it.id == id }

        compose.mainClock.autoAdvance = false
        compose.onNode(back).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNode(back).performClick()
        compose.mainClock.autoAdvance = true

        compose.waitUntilAtLeastOneExists(hasText("Add entry"), timeoutMillis = 5_000)
        compose.waitForIdle()
        compose.onNodeWithText("Add entry").assertExists()
        compose.onNodeWithText("Start sleep").assertDoesNotExist()
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

    /** Home › History › Statistics, waiting until History has gone. */
    private fun openStatistics() {
        compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 10_000)
        compose.onNodeWithText("History").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add entry"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Statistics").performClick()
        compose.waitUntilAtLeastOneExists(hasText(STATISTICS_EMPTY), timeoutMillis = 5_000)
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Add entry")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun feedOpensTheFeedSheet() {
        compose.waitUntilAtLeastOneExists(hasText("Feed"), timeoutMillis = 10_000)
        compose.onNodeWithText("Feed").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Then the sides, if you like"), timeoutMillis = 5_000)
        compose.onNodeWithText("Formula").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Then the sides, if you like")).fetchSemanticsNodes().isEmpty() }
    }

    private companion object {
        /** Statistics with nothing logged (a new app). */
        const val STATISTICS_EMPTY = "Statistics start as soon as something is logged."
    }
}
