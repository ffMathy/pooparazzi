package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.phone.ui.edit.EditActions
import com.ffmathy.pooparazzi.phone.ui.edit.EditSheet
import com.ffmathy.pooparazzi.phone.ui.edit.EditUiState
import com.ffmathy.pooparazzi.phone.ui.edit.TimeField
import com.ffmathy.pooparazzi.phone.ui.history.HistoryActions
import com.ffmathy.pooparazzi.phone.ui.history.HistoryScreen
import com.ffmathy.pooparazzi.phone.ui.preview.EditSamples
import com.ffmathy.pooparazzi.phone.ui.preview.HistorySamples
import com.ffmathy.pooparazzi.phone.ui.preview.SettingsSamples
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsActions
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsScreen
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack names, states, custom actions and click wiring on History, the edit sheet and Settings (DESIGN_SPEC §9.4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class HistoryEditSettingsSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val history = object : HistoryActions {
        override fun onEdit(entryId: String) { calls += "edit:$entryId" }
        override fun onDelete(entryId: String) { calls += "delete:$entryId" }
        override fun onPreviousDay() { calls += "previous" }
        override fun onNextDay() { calls += "next" }
        override fun onAddEntry() { calls += "add" }
        override fun onOpenStatistics() { calls += "statistics" }
    }

    private val edit = object : EditActions {
        override fun onNudge(field: TimeField, minutes: Int) { calls += "nudge:$field:$minutes" }
        override fun onSideTick(breast: Breast) { calls += "tick:$breast" }
        override fun onSave() { calls += "save" }
        override fun onDelete() { calls += "delete" }
        override fun onDismiss() { calls += "dismiss" }
    }

    private val settings = object : SettingsActions {
        override fun onAppearance(appearance: Appearance) { calls += "appearance:$appearance" }
        override fun onLanguage(language: AppLanguage) { calls += "language:$language" }
        override fun onBabyNameChange(id: String, name: String) { calls += "name:$id:$name" }
        override fun onAddBaby() { calls += "add-baby" }
    }

    @Before
    fun use24HourClock() {
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") { it.config[SemanticsActions.OnClick].label == label }

    @Test
    fun historyRowsHaveSpokenNamesAndEditAndDeleteActions() {
        compose.setContent { NightLightTheme(night = true) { HistoryScreen(HistorySamples.today(), history) } }
        val sleep = compose.onNodeWithContentDescription("Sleep, 47 minutes, 12:40 to 13:27")
        sleep.assert(clickLabel("Edit"))
        sleep.assert(
            SemanticsMatcher("custom actions Edit time and Delete") { node ->
                node.config[SemanticsActions.CustomActions].map { it.label } == listOf("Edit time", "Delete")
            },
        )
        sleep.performClick()
        val delete = sleep.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Delete" }
        compose.runOnUiThread { delete.action() }
        // Rows further down are composed once the list scrolls to them.
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasContentDescription("Pee and poop, 05:41, Yellow, Seedy"))
        list.performScrollToNode(hasContentDescription("Breast, left then right, 01:40"))
        assertEquals(listOf("edit:sample-sleep-1240", "delete:sample-sleep-1240"), calls)
    }

    @Test
    fun swipingARowTowardsTheStartRevealsDelete() {
        compose.setContent { NightLightTheme(night = true) { HistoryScreen(HistorySamples.today(), history) } }
        compose.onAllNodes(hasText("Delete")).assertCountEquals(0)
        compose.onNodeWithContentDescription("Pee, 13:44").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Delete").assertHasClickAction().performClick()
        assertEquals(listOf("delete:sample-pee-1344"), calls)
    }

    @Test
    fun theDaySwitcherAndRibbonSpeak() {
        compose.setContent { NightLightTheme(night = false) { HistoryScreen(HistorySamples.today(), history) } }
        compose.onNodeWithContentDescription("Next day").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous day").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Today: 5 feeds, 4 pee, 2 poop, 7 hours 57 minutes sleep").assertExists()
        compose.onNodeWithContentDescription("7 hours 57 minutes sleep").assertExists()
        // Rows sit under the FAB, so it is the 56 × 56 icon FAB, still named "Add entry" for TalkBack.
        compose.onNodeWithText("Add entry").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add entry").assertHasClickAction().performClick()
        assertEquals(listOf("previous", "add"), calls)
    }

    /** At the end of the list the bottom padding keeps the FAB clear of the rows: it shows its label again. */
    @Test
    fun theAddEntryFabShowsItsLabelAtTheEndOfTheList() {
        compose.setContent { NightLightTheme(night = false) { HistoryScreen(HistorySamples.today(), history) } }
        compose.onNode(hasScrollAction()).performScrollToIndex(HistorySamples.today().entries.size + 4)
        compose.waitForIdle()
        compose.onNodeWithText("Add entry").assertExists()
    }

    /** Under the log, the typical day: a heading, the plot read as one line, and each interval row read as one sentence. */
    @Test
    fun theTypicalDaySpeaks() {
        compose.setContent { NightLightTheme(night = false) { HistoryScreen(HistorySamples.today(), history) } }
        compose.onNode(hasScrollAction()).performScrollToIndex(HistorySamples.today().entries.size + 4)
        compose.waitForIdle()
        compose.onNodeWithText("Typical day").assertExists()
        compose.onNodeWithContentDescription("When sleep, feeds and diapers usually happen through the day").assertExists()
        compose.onNode(hasContentDescription("Between feeds. By day: about ", substring = true)).assertExists()
        compose.onNode(hasContentDescription("Sleep. By day: about ", substring = true)).assertExists()
    }

    /** The `chart` button at the end of the top bar opens Statistics; the list and Back stay single nodes. */
    @Test
    fun historyOpensStatistics() {
        compose.setContent { NightLightTheme(night = true) { HistoryScreen(HistorySamples.today(), history) } }
        compose.onNodeWithContentDescription("Statistics").assertHasClickAction().performClick()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(1)
        assertEquals(listOf("statistics"), calls)
    }

    private fun showSheet(state: EditUiState) {
        compose.setContent {
            NightLightTheme(night = true) {
                Box {
                    HistoryScreen(HistorySamples.today(), HistoryActions.None)
                    EditSheet(state, edit)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun timeFieldsDayChipsAndNudgesAreNamedWithTheirBlock() {
        showSheet(EditSamples.sleepNudged())
        compose.onNodeWithContentDescription("Fell asleep, 12:35, was 12:40").assert(clickLabel("Change time"))
        compose.onNodeWithContentDescription("Woke up, 13:27").assertExists()
        compose.onNodeWithContentDescription("Fell asleep day, Today").assert(clickLabel("Change day"))
        compose.onNodeWithContentDescription("Fell asleep, 5 minutes earlier").performClick()
        compose.onNodeWithContentDescription("Woke up, 1 minute later").performClick()
        compose.onNodeWithContentDescription("Duration 52 minutes, was 47 minutes").assertExists()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("nudge:START:-5", "nudge:END:1", "delete", "save", "dismiss"), calls)
    }

    @Test
    fun theSidesAreCheckboxesThatSpeakTheirTickOrder() {
        // The sample ended Right → Left: Right was ticked first, Left second.
        showSheet(EditSamples.breastfeed())
        compose.onNodeWithContentDescription("Right, first").performScrollTo().assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Checkbox))
        compose.onNodeWithContentDescription("Right then left").assertExists()
        compose.onNodeWithContentDescription("Left, second").assertIsOn().performClick()
        compose.onNodeWithText("Breast").assertIsSelected()
        assertEquals(listOf("tick:LEFT"), calls)
    }

    @Test
    fun theDiaperFormHasCheckboxTiles() {
        showSheet(EditSamples.diaperPoop())
        compose.onNodeWithText("Poop").assertIsOn()
        compose.onNodeWithText("Brown").performScrollTo().assertIsSelected()
    }

    @Test
    fun aBlockingRuleDisablesSave() {
        showSheet(EditSamples.sleepEndBeforeStart())
        compose.onNodeWithText("Waking up must come after falling asleep.").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun settingsAppearanceAndLanguageAreSelectable() {
        compose.setContent { NightLightTheme(night = true) { SettingsScreen(SettingsSamples.state(), settings) } }
        compose.onNodeWithText("Auto").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Night").performScrollTo().assertIsNotSelected().performClick()
        compose.onNodeWithText("System default").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Dansk").performScrollTo().performClick()
        assertEquals(listOf("appearance:NIGHT", "language:DA"), calls)
    }

    /** Settings › Baby with one baby: one "Name" field, "Add a baby" and the hint (BABIES_AND_BACKUP §2.4). */
    @Test
    fun settingsBabyHasANameFieldAndAddABaby() {
        compose.setContent { NightLightTheme(night = true) { SettingsScreen(SettingsSamples.state(), settings) } }
        compose.onNodeWithText("Baby").assertExists()
        compose.onNode(hasSetTextAction()).assert(hasText("Noa")).assert(hasText("Name"))
        // Signed in, the names go to the family's backup too (BABIES_AND_BACKUP §1).
        compose.onNodeWithText("Names are shared with your family and kept in your backup.").assertExists()
        compose.onNodeWithText("Add a baby").performScrollTo().assertHasClickAction().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Maja")
        assertEquals(listOf("add-baby", "name:default:Maja"), calls)
    }

    @Test
    fun settingsBabiesHasAFieldPerBabyWithItsLabelAsPlaceholder() {
        compose.setContent { NightLightTheme(night = true) { SettingsScreen(SettingsSamples.babies(), settings) } }
        compose.onNodeWithText("Babies").assertExists()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields.assertCountEquals(3)
        fields[0].assert(hasText("Noa"))
        fields[1].assert(hasText("Ida"))
        // The placeholder shows once the empty field has the focus (the "Name" label moves up).
        fields[2].performScrollTo().performClick()
        fields[2].assert(hasText("Baby 3"))
        fields[1].performTextReplacement("Idun")
        assertEquals(listOf("name:${SampleData.TWIN_ID}:Idun"), calls)
    }

    @Test
    fun addABabyIsHiddenAtTheCap() {
        val full = SettingsSamples.state(babies = (1..com.ffmathy.pooparazzi.core.settings.Settings.MAX_BABIES).map { Baby("baby-$it") })
        compose.setContent { NightLightTheme(night = true) { SettingsScreen(full, settings) } }
        compose.onNodeWithText("Add a baby").assertDoesNotExist()
    }

    @Test
    fun historyNamesTheBabyOnlyWithSeveral() {
        compose.setContent { NightLightTheme(night = false) { HistoryScreen(HistorySamples.twins(), history) } }
        compose.onNodeWithText("History").assertExists()
        compose.onNodeWithText("Noa").assertExists()
    }

    @Test
    fun historyWithOneBabyHasNoName() {
        compose.setContent { NightLightTheme(night = false) { HistoryScreen(HistorySamples.today(), history) } }
        compose.onNodeWithText("Baby").assertDoesNotExist()
        compose.onNodeWithText("Noa").assertDoesNotExist()
    }
}
