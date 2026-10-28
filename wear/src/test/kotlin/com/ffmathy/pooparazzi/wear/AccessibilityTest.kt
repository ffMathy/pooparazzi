package com.ffmathy.pooparazzi.wear

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.diaper.DiaperActions
import com.ffmathy.pooparazzi.wear.diaper.DiaperScreen
import com.ffmathy.pooparazzi.wear.feed.SideActions
import com.ffmathy.pooparazzi.wear.feed.SideScreen
import com.ffmathy.pooparazzi.wear.logged.LOGGED_INERT_MILLIS
import com.ffmathy.pooparazzi.wear.logged.LoggedScreen
import com.ffmathy.pooparazzi.wear.main.MainActions
import com.ffmathy.pooparazzi.wear.main.MainScreen
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.DiaperDraft
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.today.TodayScreen
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
    fun anUntypedFeedIsNotReadAsLastFeedFeed() {
        // As the phone: "Last feed, 2 hours 10 minutes ago, at 12:22", not "Last feed, feed, …".
        val idle = WearSamples.mainIdle()
        show { MainScreen(idle.copy(lastFeed = idle.lastFeed!!.copy(feedType = null)), MainActions()) }
        compose.onNode(hasContentDescription("Last feed, 2 hours 10 minutes ago, at 12:22")).assertExists()
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
    fun afterABreastFeedMainStaysIdle() {
        show { MainScreen(WearSamples.mainAfterBreastfeed(), MainActions()) }
        compose.onNodeWithContentDescription("Start sleep").assertIsEnabled()
        compose.onAllNodes(hasContentDescription("End feed")).fetchSemanticsNodes().let { assert(it.isEmpty()) { "no End feed" } }
    }

    @Test
    fun diaperCheckboxesAreCheckboxesAndSaveIsAlwaysEnabled() {
        val empty = DiaperDraft(stamp = SampleData.NOW_1432, zone = WearSamples.zone)
        show { DiaperScreen(empty, DiaperActions()) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Pee")
            .assert(hasRole(Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        // Both are optional: Save with nothing ticked logs a plain change.
        compose.onNodeWithText("Save").assertIsEnabled()
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
    fun sidesAreCheckboxesReadWithTheirPlaceInTheOrder() {
        show { SideScreen(WearSamples.sideDraft().copy(ticks = listOf(Breast.RIGHT, Breast.LEFT)), SideActions()) }
        // Never "Right → Left" with an arrow (DESIGN_SPEC §9.4); the order matters.
        compose.onNodeWithContentDescription("Right, first").assert(hasRole(Role.Checkbox))
        compose.onNodeWithContentDescription("Left, second").assert(hasRole(Role.Checkbox))
        compose.onNodeWithContentDescription("Right then left").assertExists()
        compose.onNodeWithText("Save").assertIsEnabled()
        // What Save logs: the time only, no range and no duration.
        compose.onNodeWithContentDescription("Breast, 14:32").assertExists()
    }

    @Test
    fun aBreastReceiptReadsTheSideInWords() {
        compose.mainClock.autoAdvance = false
        show { LoggedScreen(WearSamples.receiptBreast(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {}) }
        compose.onNodeWithContentDescription("Breast at 14:32").assertExists()
        compose.onNodeWithContentDescription("Left then right").assertExists()
    }

    @Test
    fun aDiaperReceiptReadsPeeAndPoopInWords() {
        compose.mainClock.autoAdvance = false
        show { LoggedScreen(WearSamples.receiptDiaper(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {}) }
        compose.onNodeWithContentDescription("Pee and poop at 14:32").assertExists()
    }

    @Test
    fun theLastDiaperCardReadsBothInWords() {
        val idle = WearSamples.mainIdle()
        show { MainScreen(idle.copy(lastDiaper = idle.lastDiaper?.copy(poop = true)), MainActions()) }
        // Hero, Awake, then the last diaper.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.onNode(hasContentDescription("Last diaper, pee and poop, 48 minutes ago, at 13:44")).assertExists()
    }

    @Test
    fun todayRowsReadSidesAndRangesInWords() {
        show { TodayScreen(WearSamples.today(), phoneAvailable = false, onRow = {}, onMoreOnPhone = {}) }
        val rows = compose.onAllNodes(hasContentDescription("Adjust", substring = true)).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        // The newest rows are in view: a diaper and a sleep range.
        assert(rows.any { it == "Pee, 13:44. Adjust" }) { rows.toString() }
        assert(rows.any { it == "Sleep, 47 minutes, 12:40 to 13:27. Adjust" }) { rows.toString() }
        assert(rows.none { it.contains("–") || it.contains("→") || it.contains("+") }) { rows.toString() }
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
