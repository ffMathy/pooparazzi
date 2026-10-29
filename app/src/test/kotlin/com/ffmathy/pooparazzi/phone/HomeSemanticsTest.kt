package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.phone.ui.home.ReceiptSpec
import com.ffmathy.pooparazzi.phone.ui.home.HomeActions
import com.ffmathy.pooparazzi.phone.ui.home.HomeScreen
import com.ffmathy.pooparazzi.phone.ui.home.HomeStateBuilder
import com.ffmathy.pooparazzi.phone.ui.home.HomeUiState
import com.ffmathy.pooparazzi.phone.ui.home.SheetModel
import com.ffmathy.pooparazzi.phone.ui.preview.HomeSamples
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack names, states and click wiring on Home (DESIGN_SPEC §9.4, V1_ADDITIONS §3.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class HomeSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : HomeActions {
        override fun onEditEntry(entryId: String) { calls += "edit:$entryId" }
        override fun onDiaperPee(checked: Boolean) { calls += "pee:$checked" }
        override fun onSleepToggle(shownRunning: Boolean) { calls += "toggle:$shownRunning" }
        override fun onOpenHistory() { calls += "history" }
        override fun onPoopColor(color: PoopColor?) { calls += "colour:$color" }
        override fun onSideTick(breast: Breast) { calls += "tick:$breast" }
        override fun onLogBreastfeed() { calls += "log-breast" }
        override fun onFeedType(type: FeedType?) { calls += "type:$type" }
        override fun onUndo() { calls += "undo" }
        override fun onReceiptDone() { calls += "done" }
        override fun onSheetEdit(entryId: String) { calls += "sheet-edit:$entryId" }
        override fun onSwitchBaby() { calls += "switch" }
        override fun onSelectBaby(babyId: String) { calls += "select:$babyId" }
        override fun onRetrySync() { calls += "retry" }
    }

    @Before
    fun use24HourClock() {
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun show(state: HomeUiState, night: Boolean = true) {
        compose.setContent { NightLightTheme(night = night) { HomeScreen(state, actions) } }
        compose.waitForIdle()
    }

    @Test
    fun theLampAndSinceRowsHaveSpokenForms() {
        show(HomeSamples.asleep())
        compose.onNodeWithContentDescription("Asleep, 1 hour 7 minutes, since 02:05").assertExists()
        compose.onNodeWithContentDescription("Last feed, breast, left then right, 1 hour 32 minutes ago, at 01:40")
            .assert(SemanticsMatcher("click label Edit") { it.config[SemanticsActions.OnClick].label == "Edit" })
            .performClick()
        compose.onNodeWithContentDescription("Last diaper, pee, 1 hour 20 minutes ago, at 01:52").assertExists()
        assertEquals(listOf("edit:sample-breast-0140"), calls)
    }

    @Test
    fun theLastFeedShowsTheBreastOrder() {
        // Asleep: since-row 1 carries the order after the time.
        show(HomeSamples.asleep())
        compose.onNodeWithText("01:40 · L\u00A0→\u00A0R", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theLampShowsTheBreastOrderOfTheLastFeed() {
        show(HomeSamples.breastLogged())
        compose.onNodeWithText("ago · 14:32 · Left → Right", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Last feed, breast, left then right, 18 minutes ago, at 14:32").assertExists()
    }

    @Test
    fun theSleepToggleNamesItsActionAndState() {
        show(HomeSamples.asleep())
        compose.onNodeWithContentDescription("Woke up, end sleep")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Asleep since 02:05"))
            .assert(SemanticsMatcher("click label End sleep") { it.config[SemanticsActions.OnClick].label == "End sleep" })
            .performClick()
        assertEquals(listOf("toggle:true"), calls)
    }

    @Test
    fun theTodayGridIsALinkWithATextSummary() {
        show(HomeSamples.idle())
        compose.onNodeWithContentDescription("Today: 5 feeds, 4 pee, 2 poop, 7 hours 57 minutes sleep").performClick()
        assertEquals(listOf("history"), calls)
    }

    @Test
    fun diaperTilesAreCheckboxesAndSaveIsAlwaysEnabled() {
        show(diaperSheet(SheetModel.Diaper(stamp = SampleData.NOW_1432)))
        compose.onNodeWithText("Pee")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOff()
            .performClick()
        compose.onNodeWithText("Poop").assertIsOff()
        // Both are optional: Save with nothing ticked logs a plain change.
        compose.onNodeWithText("Save").assertIsEnabled()
        assertEquals(listOf("pee:true"), calls)
    }

    @Test
    fun colourChipsExposeTheirSelection() {
        show(diaperSheet(SheetModel.Diaper(stamp = SampleData.NOW_1432, pee = true, poop = true, color = PoopColor.YELLOW)))
        compose.onNodeWithText("Poop").assertIsOn()
        compose.onNodeWithText("Yellow").assertIsSelected().performClick()
        compose.onNodeWithText("Pale, red or black poop can be a reason to call your doctor or health visitor.").assertDoesNotExist()
        assertEquals("tapping the selected colour clears it", listOf("colour:null"), calls)
        // The width-holding twin of a texture chip is invisible to TalkBack: the label is read once.
        compose.onNodeWithText("Watery").assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString("Watery"))))
    }

    @Test
    fun theReceiptShowsTheLogWithItsTimeAndItsChipsSpeakTheirMinutes() {
        show(HomeSamples.justLogged())
        compose.onNodeWithText("Formula logged").assertExists()
        compose.onNodeWithText("14:32").assertExists()
        compose.onNodeWithContentDescription("5 minutes earlier").assertExists()
        compose.onNodeWithContentDescription("15 minutes earlier").assertExists()
        compose.waitUntil(2_000) { runCatching { compose.onNodeWithText("Undo").assertIsEnabled() }.isSuccess }
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("undo", "sheet-edit:${HomeSamples.formula1432.id}", "done"), calls)
    }

    @Test
    fun theHealthHintShowsForPale() {
        show(diaperSheet(SheetModel.Diaper(stamp = SampleData.NOW_1432, poop = true, color = PoopColor.PALE)))
        compose.onNodeWithText("Pale, red or black poop can be a reason to call your doctor or health visitor.").assertExists()
    }

    /** The receipt sub-lines have spoken forms: no "→", "m" or "<1m" read out (DESIGN_SPEC §9.5). */
    @Test
    fun receiptSubLinesHaveSpokenForms() {
        val feed = Entry.breastfeed(SampleData.NOW_1432, BreastSide.LEFT_THEN_RIGHT, id = "breast-feed")
        show(receipt(feed, ReceiptSpec.FeedLogged(feed.id, FeedType.BREAST, BreastSide.LEFT_THEN_RIGHT)))
        compose.onNodeWithText("Breast logged").assertExists()
        compose.onNodeWithText("14:32 · Left → Right").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("14:32, Left then right")))
    }

    @Test
    fun wokeUpSubLineHasASpokenForm() {
        val sleep = Entry.sleep(SampleData.at(13, 25), SampleData.NOW_1432, id = "ended-sleep")
        show(receipt(sleep, ReceiptSpec.WokeUp(sleep.id)))
        compose.onNodeWithText("14:32 · Slept 1h 07m").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("14:32, Slept 1 hour 7 minutes")))
    }

    @Test
    fun breastInTheFeedSheetOffersTheSidesNext() {
        show(HomeSamples.feedSheet())
        compose.onNodeWithText("Then the sides, if you like").assertExists()
        compose.onNodeWithText("Starts a timer").assertDoesNotExist()
        compose.onNodeWithText("Breast").performClick()
        assertTrue(calls.contains("type:BREAST"))
    }

    @Test
    fun theBreastStepLogsAtTheSheetsTime() {
        show(HomeSamples.breastSheet(), night = false)
        compose.onNodeWithText("Breast · 14:32").assertExists()
        compose.onNodeWithText("Tick the sides in the order you fed. Optional.").assertExists()
        compose.onNodeWithText("Log feed").performClick()
        assertTrue(calls.contains("log-breast"))
    }

    @Test
    fun aPeeAndPoopDiaperIsSpokenWithoutThePlus() {
        val both = Entry.diaper(SampleData.at(14, 0), pee = true, poop = true, id = "both-1400")
        show(
            HomeStateBuilder.build(
                entries = SampleData.scenario() + both,
                babyName = SampleData.BABY_NAME,
                sync = SyncStatus.Synced(SampleData.SYNCED_1431),
                now = SampleData.NOW_1432,
                zone = SampleData.ZONE,
            ),
        )
        compose.onNodeWithContentDescription("Last diaper, pee and poop, 32 minutes ago, at 14:00").assertExists()
    }

    private fun receipt(entry: Entry?, spec: ReceiptSpec, now: Long = SampleData.NOW_1432) = HomeStateBuilder.build(
        entries = SampleData.scenario() + listOfNotNull(entry),
        babyName = SampleData.BABY_NAME,
        sync = SyncStatus.Synced(SampleData.SYNCED_1431),
        now = now,
        zone = SampleData.ZONE,
        sheet = SheetModel.Receipt(stamp = now, key = 1, spec = spec),
    )

    private fun diaperSheet(sheet: SheetModel.Diaper) = HomeStateBuilder.build(
        entries = SampleData.scenario(),
        babyName = SampleData.BABY_NAME,
        sync = SyncStatus.Synced(SampleData.SYNCED_1431),
        now = SampleData.NOW_1432,
        zone = SampleData.ZONE,
        sheet = sheet,
    )

    /** Left and Right are checkboxes; the ticked order is spoken ("Right, first") and summarised without the arrow. */
    @Test
    fun sideCheckboxesSpeakTheirTickOrder() {
        show(HomeSamples.breastSheet(), night = false)
        compose.onNodeWithContentDescription("No side").assertExists()
        compose.onNodeWithContentDescription("Left").assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .performClick()
        assertTrue(calls.contains("tick:LEFT"))
    }

    @Test
    fun tickedSidesSpeakTheirPlace() {
        show(HomeSamples.breastSheet(listOf(Breast.RIGHT, Breast.LEFT)), night = false)
        compose.onNodeWithContentDescription("Right, first").assertIsOn()
        compose.onNodeWithContentDescription("Left, second").assertIsOn()
        compose.onNodeWithContentDescription("Right then left").assertExists()
    }

    /** BABIES_AND_BACKUP §2.4: with one baby the name is a plain heading, as before. */
    @Test
    fun withOneBabyTheNameIsNoButton() {
        show(HomeSamples.idle())
        compose.onNodeWithText("Noa").assertExists()
        compose.onNodeWithContentDescription("Noa. Switch baby").assertDoesNotExist()
    }

    @Test
    fun withTwinsTheNameSwitchesTheBaby() {
        show(HomeSamples.twins())
        compose.onNodeWithContentDescription("Noa. Switch baby")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf("switch"), calls)
    }

    /** In the error state the retry target sits under the switcher, so the two never overlap. */
    @Test
    fun theSwitcherAndTheRetryTargetDoNotOverlap() {
        show(HomeSamples.twins(sync = SyncStatus.Error))
        val switcher = compose.onNodeWithContentDescription("Noa. Switch baby").fetchSemanticsNode().boundsInRoot
        val retry = compose.onNodeWithContentDescription("Couldn't save · Tap to retry").fetchSemanticsNode().boundsInRoot
        assertTrue("$switcher vs $retry", retry.top >= switcher.bottom)
        assertTrue(retry.height >= switcher.height)
        compose.onNodeWithContentDescription("Couldn't save · Tap to retry").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun theSwitchBabySheetHasOneRadioRowPerBaby() {
        show(HomeSamples.twinsSwitching())
        compose.onNodeWithText("Switch baby").assertExists()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(2)
        compose.onNode(hasText("Noa") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        compose.onNode(hasText("Ida") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsNotSelected()
            .performClick()
        assertEquals(listOf("select:${SampleData.TWIN_ID}"), calls)
    }
}
