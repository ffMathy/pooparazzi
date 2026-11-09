package com.ffmathy.pooparazzi.wear

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import com.ffmathy.pooparazzi.core.format.VolumeFormatter
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.diaper.DiaperActions
import com.ffmathy.pooparazzi.wear.diaper.DiaperScreen
import com.ffmathy.pooparazzi.wear.feed.BottleActions
import com.ffmathy.pooparazzi.wear.feed.BottleScreen
import com.ffmathy.pooparazzi.wear.feed.SideActions
import com.ffmathy.pooparazzi.wear.feed.SideScreen
import com.ffmathy.pooparazzi.wear.logged.LOGGED_INERT_MILLIS
import com.ffmathy.pooparazzi.wear.logged.LoggedScreen
import com.ffmathy.pooparazzi.wear.main.MainActions
import com.ffmathy.pooparazzi.wear.main.MainScreen
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.DiaperDraft
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.time.TimeScreen
import org.junit.Assert.assertEquals
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

    private fun isSelected(selected: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected)

    private fun hasClickLabel(label: String?) = SemanticsMatcher("click label $label") {
        SemanticsActions.OnClick in it.config && it.config[SemanticsActions.OnClick].label == label
    }

    private fun hasCustomActions(vararg labels: String) = SemanticsMatcher("custom actions ${labels.toList()}") {
        it.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { action -> action.label } == labels.toList()
    }

    private val isLiveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    /** Scrolls the screen's list until a node matching [matcher] is composed and in view. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
        compose.waitForIdle()
    }

    /** The volume formatter of the test's region (fl oz in Robolectric's US default, ml elsewhere). */
    private val volume: VolumeFormatter get() = VolumeFormatter.from(ApplicationProvider.getApplicationContext())

    @Test
    fun idleMainReadsTheLastFeedAndTheSleepToggle() {
        show { MainScreen(WearSamples.mainIdle(), MainActions()) }
        compose.onNode(hasContentDescription("Feed. Last feed, bottle, 2 hours 10 minutes ago, at 12:22, next feed expected around 15:25")).assertExists()
        compose.onNodeWithContentDescription("Start sleep, expected to sleep again around 15:15")
            .assert(hasStateDescription("Awake since 13:27"))
            .assertIsEnabled()
    }

    @Test
    fun theBabyButtonNamesTheBabyAndWhatItDoes() {
        show { MainScreen(WearSamples.mainIdleTwins(), MainActions()) }
        compose.onNodeWithContentDescription("Noa. Switch baby")
            .assert(hasRole(Role.Button))
            .assertIsEnabled()
    }

    @Test
    fun withOneBabyThereIsNoBabyButton() {
        show { MainScreen(WearSamples.mainIdle(), MainActions()) }
        compose.onNode(hasContentDescription("Switch baby", substring = true)).assertDoesNotExist()
    }

    @Test
    fun anUntypedFeedIsNotReadAsLastFeedFeed() {
        // As the phone: "Last feed, 2 hours 10 minutes ago, at 12:22", not "Last feed, feed, …".
        val idle = WearSamples.mainIdle()
        show { MainScreen(idle.copy(lastFeed = idle.lastFeed!!.copy(feedType = null)), MainActions()) }
        compose.onNode(hasContentDescription("Feed. Last feed, 2 hours 10 minutes ago, at 12:22, next feed expected around 15:25")).assertExists()
    }

    @Test
    fun asleepMainReadsTheSleepOnceAndNamesWokeUp() {
        show { MainScreen(WearSamples.mainAsleep(), MainActions()) }
        compose.onNode(hasContentDescription("Asleep, 1 hour 7 minutes, since 02:05")).assertExists()
        compose.onNodeWithContentDescription("Woke up, end sleep, expected to wake around 05:25").assert(hasStateDescription("Asleep since 02:05"))
        // The numeral is never read as "1h 07m".
        compose.onAllNodes(hasText("07")).fetchSemanticsNodes().let { assert(it.isEmpty()) { "numeral exposed as text" } }
    }

    @Test
    fun afterABreastFeedMainStaysIdle() {
        show { MainScreen(WearSamples.mainAfterBreastfeed(), MainActions()) }
        compose.onNodeWithContentDescription("Start sleep, expected to sleep again around 15:15").assertIsEnabled()
        // The breast order of the last feed, read.
        compose.onNode(hasContentDescription("Feed. Last feed, breast, left then right, 18 minutes ago, at 14:32, next feed expected around 17:45")).assertExists()
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
        compose.onNodeWithText("Poop")
            .assert(hasRole(Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        compose.onNodeWithText("Diaper · 14:32").assert(isHeading())
        // Both are optional: Save with nothing ticked logs a plain change.
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    /** Where "Poop" no longer fits a tile, the screen shows full-width checkbox rows instead: checkboxes too. */
    @Test
    fun theFallbackRowsAreCheckboxesToo() {
        show {
            val events = com.ffmathy.pooparazzi.wear.ui.events()
            val spec = androidx.wear.compose.material3.lazy.rememberTransformationSpec()
            androidx.wear.compose.foundation.lazy.TransformingLazyColumn {
                item {
                    com.ffmathy.pooparazzi.wear.diaper.DiaperCheckbox(
                        label = "Poop",
                        icon = com.ffmathy.pooparazzi.core.ui.icons.XIcons.poop,
                        checked = true,
                        onCheckedChange = {},
                        colors = events.poop,
                        lit = events.diaper.containerLit,
                        outline = events.diaper.strong,
                        enabled = true,
                        transformation = SurfaceTransformation(spec),
                        modifier = androidx.compose.ui.Modifier,
                    )
                }
            }
        }
        compose.onNodeWithText("Poop")
            .assert(hasRole(Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun theColourAndTextureChipsAreRadioButtonsUnderHeadings() {
        show { DiaperScreen(WearSamples.diaperDraft(), DiaperActions()) }
        compose.mainClock.advanceTimeBy(600)
        scrollTo(hasText("Colour"))
        compose.onNodeWithText("Colour").assert(isHeading())
        scrollTo(hasText("Yellow"))
        compose.onNodeWithText("Yellow").assert(hasRole(Role.RadioButton)).assert(isSelected(true)).assert(hasClickLabel("Clear"))
        compose.onNodeWithText("Green").assert(hasRole(Role.RadioButton)).assert(isSelected(false)).assert(hasClickLabel(null))
        scrollTo(hasText("Texture"))
        compose.onNodeWithText("Texture").assert(isHeading())
        scrollTo(hasText("Seedy"))
        compose.onNodeWithText("Seedy").assert(hasRole(Role.RadioButton)).assert(isSelected(true)).assert(hasClickLabel("Clear"))
        compose.onNodeWithText("Hard").assert(isSelected(false))
    }

    @Test
    fun theBottlesContentsAndAmountChipsAreRadioButtons() {
        var cleared = false
        show { BottleScreen(WearSamples.bottleDraft(), BottleActions(onContents = { if (it == null) cleared = true })) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Bottle · 14:32").assert(isHeading())
        compose.onNodeWithText("Formula").assert(hasRole(Role.RadioButton)).assert(isSelected(true)).assert(hasClickLabel("Clear"))
        compose.onNodeWithText("Milk").assert(hasRole(Role.RadioButton)).assert(isSelected(false))
        compose.onNodeWithText("Formula").performClick()
        assertEquals("tapping the chosen one clears it", true, cleared)

        // The last bottle was 120 ml, so its chip says so; it is the one chosen.
        val chosen = volume.format(120)
        scrollTo(hasContentDescription("$chosen, as last time"))
        compose.onNodeWithContentDescription("$chosen, as last time")
            .assert(hasRole(Role.RadioButton))
            .assert(isSelected(true))
            .assert(hasClickLabel("Clear"))
        val other = volume.quickAmounts(120).first()
        compose.onNodeWithContentDescription(volume.format(other)).assert(isSelected(false))
    }

    @Test
    fun theAmountIsReadAsOneValueAndItsStepsInWords() {
        val step = volume.format(checkNotNull(volume.amountAt(1)))
        show { BottleScreen(WearSamples.emptyBottleDraft(), BottleActions()) }
        compose.mainClock.advanceTimeBy(600)
        scrollTo(hasContentDescription("Amount, No amount"))
        compose.onNodeWithContentDescription("Amount, No amount").assert(isLiveRegion)
        // At the bottom of the scale there is nothing less.
        compose.onNodeWithContentDescription("$step less").assertIsNotEnabled()
        compose.onNodeWithContentDescription("$step more").assertIsEnabled()
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun theTopOfTheAmountScaleHasNothingMore() {
        val step = volume.format(checkNotNull(volume.amountAt(1)))
        val most = checkNotNull(volume.amountAt(volume.steps))
        show { BottleScreen(WearSamples.emptyBottleDraft().copy(amountMl = most), BottleActions()) }
        compose.mainClock.advanceTimeBy(600)
        scrollTo(hasContentDescription("Amount, ${volume.format(most)}"))
        compose.onNodeWithContentDescription("$step more").assertIsNotEnabled()
        compose.onNodeWithContentDescription("$step less").assertIsEnabled()
    }

    @Test
    fun thePickersHaveNoInlineTimeTheTimeMovesFromLoggedOnly() {
        show { DiaperScreen(WearSamples.emptyDiaperDraft(), DiaperActions()) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Diaper · 14:32").assertExists()
        compose.onNodeWithText("Earlier?").assertDoesNotExist()
        compose.onNodeWithContentDescription("5 minutes earlier").assertDoesNotExist()
        compose.onNodeWithContentDescription("15 minutes earlier").assertDoesNotExist()
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

    /** The sync status is a phone icon with no text; TalkBack reads the sentence (DESIGN_SPEC §4.5). */
    @Test
    fun loggedSyncIsAPhoneIconReadAsASentence() {
        compose.mainClock.autoAdvance = false
        show { LoggedScreen(WearSamples.receiptDiaper(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {}) }
        compose.onNodeWithContentDescription("Synced with phone · 14:31")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNode(hasText("Synced with phone", substring = true)).assertDoesNotExist()
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
    fun theDiaperRowReadsBothInWords() {
        val idle = WearSamples.mainIdle()
        show { MainScreen(idle.copy(lastDiaper = idle.lastDiaper?.copy(poop = true)), MainActions()) }
        compose.onNode(hasContentDescription("Diaper. Last diaper, pee and poop, 48 minutes ago, at 13:44")).assertExists()
    }

    @Test
    fun theTimeOnLoggedChangesTheTimeAfterTheInertSecond() {
        compose.mainClock.autoAdvance = false
        var opened = 0
        show {
            LoggedScreen(
                WearSamples.receiptFormula(),
                WearSamples.syncAt1431,
                onUndo = {},
                onDismiss = {},
                onTimeout = {},
                onChangeTime = { opened++ },
            )
        }
        val time = compose.onNode(hasContentDescription("Bottle at 14:32"))
        time.assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(LOGGED_INERT_MILLIS + 50L)
        time.assertIsEnabled().assert(
            SemanticsMatcher("click label Change time") {
                SemanticsActions.OnClick in it.config && it.config[SemanticsActions.OnClick].label == "Change time"
            },
        )
        time.performClick()
        assertEquals(1, opened)
    }

    @Test
    fun timeReadsTheTimeWithItsOffsetAndTheButtonsInWords() {
        show { TimeScreen(WearSamples.timeDraft(), onShift = {}, onDone = {}) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(hasContentDescription("Feed, 14:25, 7 minutes earlier")).assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
        )
        // Never "−5m" (TalkBack reads "m" as metres, DESIGN_SPEC §9.5).
        compose.onNodeWithContentDescription("5 minutes earlier").assertIsEnabled()
        compose.onNodeWithContentDescription("5 minutes later").assertIsEnabled()
        compose.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun timeDisablesAButtonAtItsLimit() {
        show { TimeScreen(WearSamples.timeDraft().copy(offsetMinutes = 0), onShift = {}, onDone = {}) }
        compose.mainClock.advanceTimeBy(600)
        // Never later than the Feed tap.
        compose.onNodeWithContentDescription("5 minutes later").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Feed, 14:32").assertExists()
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
        compose.onNode(hasContentDescription("Måltid. Sidste måltid, flaske, 2 timer 10 minutter siden, kl. 12:22, næste måltid forventes omkring kl. 15:25"))
            .assertExists()
        compose.onNodeWithContentDescription("Start søvn, forventes at sove igen omkring kl. 15:15").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Vågen siden kl. 13:27"),
        )
    }

    @Test
    fun theSyncIconSpeaksDanish() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            XWearTheme {
                AppScaffold {
                    LoggedScreen(WearSamples.receiptDiaper(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Synkroniseret med telefon · 14:31").assertExists()
    }

    @Test
    fun theDiaperScreenSpeaksDanish() {
        compose.setContent { XWearTheme { AppScaffold { DiaperScreen(WearSamples.diaperDraft(), DiaperActions()) } } }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Tis").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Gul"))
        compose.onNodeWithText("Gul").assert(
            SemanticsMatcher("click label Ryd") {
                SemanticsActions.OnClick in it.config && it.config[SemanticsActions.OnClick].label == "Ryd"
            },
        )
        // No inline time: the time moves afterwards, from Logged.
        compose.onNodeWithText("Tidligere?").assertDoesNotExist()
        compose.onNodeWithContentDescription("15 minutter tidligere").assertDoesNotExist()
    }
}
