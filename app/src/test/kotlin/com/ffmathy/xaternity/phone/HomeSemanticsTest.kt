package com.ffmathy.xaternity.phone

import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertHasClickAction
import com.ffmathy.xaternity.core.model.BreastSide
import android.app.Application
import android.provider.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.phone.ui.home.HomeActions
import com.ffmathy.xaternity.phone.ui.home.HomeScreen
import com.ffmathy.xaternity.phone.ui.home.HomeStateBuilder
import com.ffmathy.xaternity.phone.ui.home.HomeUiState
import com.ffmathy.xaternity.phone.ui.home.SheetModel
import com.ffmathy.xaternity.phone.ui.preview.HomeSamples
import com.ffmathy.xaternity.phone.ui.theme.NightLightTheme
import com.ffmathy.xaternity.core.sync.SyncStatus
import org.junit.Assert.assertEquals
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
        override fun onSleepToggle() { calls += "toggle" }
        override fun onOpenHistory() { calls += "history" }
        override fun onPoopColor(color: PoopColor?) { calls += "colour:$color" }
        override fun onSide(side: BreastSide) { calls += "side:$side" }
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
        compose.onNodeWithContentDescription("Last feed, breast, 1 hour 32 minutes ago, at 01:40")
            .assert(SemanticsMatcher("click label Edit") { it.config[SemanticsActions.OnClick].label == "Edit" })
            .performClick()
        compose.onNodeWithContentDescription("Last diaper, pee, 1 hour 20 minutes ago, at 01:52").assertExists()
        assertEquals(listOf("edit:sample-breast-0140"), calls)
    }

    @Test
    fun theSleepToggleNamesItsActionAndState() {
        show(HomeSamples.asleep())
        compose.onNodeWithContentDescription("Woke up, end sleep")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Asleep since 02:05"))
            .assert(SemanticsMatcher("click label End sleep") { it.config[SemanticsActions.OnClick].label == "End sleep" })
            .performClick()
        assertEquals(listOf("toggle"), calls)
    }

    @Test
    fun theTodayGridIsALinkWithATextSummary() {
        show(HomeSamples.idle())
        compose.onNodeWithContentDescription("Today: 5 feeds, 4 pee, 2 poop, 7 hours 57 minutes sleep").performClick()
        assertEquals(listOf("history"), calls)
    }

    @Test
    fun diaperTilesAreCheckboxesAndSaveWaitsForOne() {
        show(diaperSheet(SheetModel.Diaper(stamp = SampleData.NOW_1432)))
        compose.onNodeWithText("Pee")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOff()
            .performClick()
        compose.onNodeWithText("Poop").assertIsOff()
        compose.onNodeWithText("Save").assertIsNotEnabled()
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
    fun offsetChipsSpeakTheirMinutes() {
        show(HomeSamples.justLogged())
        compose.onNodeWithContentDescription("5 minutes earlier").assertExists()
        compose.onNodeWithContentDescription("15 minutes earlier").assertExists()
        compose.onNodeWithText("Undo").assertExists()
    }

    @Test
    fun theHealthHintShowsForPale() {
        show(diaperSheet(SheetModel.Diaper(stamp = SampleData.NOW_1432, poop = true, color = PoopColor.PALE)))
        compose.onNodeWithText("Pale, red or black poop can be a reason to call your doctor or health visitor.").assertExists()
    }

    private fun diaperSheet(sheet: SheetModel.Diaper) = HomeStateBuilder.build(
        entries = SampleData.scenario(),
        babyName = SampleData.BABY_NAME,
        sync = SyncStatus.Synced(SampleData.SYNCED_1431),
        now = SampleData.NOW_1432,
        zone = SampleData.ZONE,
        sheet = sheet,
    )

    /** The side tiles speak "Left then right", not the arrow, and still end the feed with that side. */
    @Test
    fun sideTilesSpeakTheSideWithoutTheArrow() {
        show(HomeSamples.sideSheet(), night = false)
        compose.onNodeWithContentDescription("Left then right").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Right").assertHasClickAction()
        assertTrue(calls.contains("side:LEFT_THEN_RIGHT"))
    }
}
