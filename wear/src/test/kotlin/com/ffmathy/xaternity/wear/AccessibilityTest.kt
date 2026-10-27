package com.ffmathy.xaternity.wear

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.wear.diaper.DiaperActions
import com.ffmathy.xaternity.wear.diaper.DiaperScreen
import com.ffmathy.xaternity.wear.logged.LOGGED_INERT_MILLIS
import com.ffmathy.xaternity.wear.logged.LoggedScreen
import com.ffmathy.xaternity.wear.main.MainActions
import com.ffmathy.xaternity.wear.main.MainScreen
import com.ffmathy.xaternity.wear.sample.WearSamples
import com.ffmathy.xaternity.wear.state.DiaperDraft
import com.ffmathy.xaternity.wear.theme.XWearTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack names, states and roles (DESIGN_SPEC §9.4–9.5): durations are spoken in words, never "48m". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { XWearTheme { AppScaffold { content() } } }
        compose.waitForIdle()
    }

    private fun hasStateDescription(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun idleMainReadsTheLastFeedAndTheSleepToggle() {
        show { MainScreen(WearSamples.mainIdle(), MainActions()) }
        compose.onNode(hasContentDescription("Last feed, formula, 2 hours 10 minutes ago, at 12:22")).assertExists()
        compose.onNodeWithContentDescription("Start sleep")
            .assert(hasStateDescription("Awake since 13:27"))
            .assertIsEnabled()
    }

    @Test
    fun asleepMainReadsTheLampOnceAndNamesWokeUp() {
        show { MainScreen(WearSamples.mainAsleep(), MainActions()) }
        compose.onNode(hasContentDescription("Asleep, 1 hour 7 minutes, since 02:05")).assertExists()
        compose.onNodeWithContentDescription("Woke up, end sleep").assert(hasStateDescription("Asleep since 02:05"))
        // The numeral is never read as "1h 07m".
        compose.onAllNodes(hasText("07")).fetchSemanticsNodes().let { assert(it.isEmpty()) { "numeral exposed as text" } }
    }

    @Test
    fun breastfeedingMainNamesEndFeed() {
        show { MainScreen(WearSamples.mainFeeding(), MainActions()) }
        compose.onNode(hasContentDescription("Breastfeeding, 18 minutes, since 14:32")).assertExists()
        compose.onNodeWithContentDescription("End feed").assertIsEnabled()
    }

    @Test
    fun diaperCheckboxesAreCheckboxesAndSaveWaitsForOne() {
        val empty = DiaperDraft(stamp = SampleData.NOW_1432, zone = WearSamples.zone)
        show { DiaperScreen(empty, DiaperActions()) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Pee")
            .assert(hasRole(Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun loggedUndoIsInertForASecondAndTheBackgroundDismisses() {
        compose.mainClock.autoAdvance = false
        show {
            LoggedScreen(WearSamples.receiptDiaper(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {})
        }
        compose.onNodeWithText("Undo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Dismiss").assertIsNotEnabled()
        compose.onNode(hasText("Pee + poop at 14:32")).assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
        )
        compose.mainClock.advanceTimeBy(LOGGED_INERT_MILLIS + 50L)
        compose.onNodeWithText("Undo").assertIsEnabled()
        compose.onNodeWithContentDescription("Dismiss").assertIsEnabled()
    }

    @Test
    fun aTileUndoIsActiveAtOnce() {
        compose.mainClock.autoAdvance = false
        show {
            LoggedScreen(
                WearSamples.receiptFormula().copy(fromTile = true),
                WearSamples.syncAt1431,
                onUndo = {},
                onDismiss = {},
                onTimeout = {},
            )
        }
        compose.onNodeWithText("Undo").assertIsEnabled()
    }
}

/** The same spoken forms in Danish (V1_ADDITIONS §5: "2 timer 10 minutter"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "da-w227dp-h227dp-round-watch-xhdpi")
class DanishAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun idleMainReadsTheLastFeedInDanish() {
        compose.setContent { XWearTheme { AppScaffold { MainScreen(WearSamples.mainIdle(), MainActions()) } } }
        compose.onNode(hasContentDescription("Sidste måltid, modermælkserstatning, 2 timer 10 minutter siden, kl. 12:22"))
            .assertExists()
        compose.onNodeWithContentDescription("Start søvn").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Vågen siden kl. 13:27"),
        )
    }
}
