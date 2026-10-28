package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.ui.edit.EditActions
import com.ffmathy.pooparazzi.phone.ui.edit.EditSheet
import com.ffmathy.pooparazzi.phone.ui.edit.EditTags
import com.ffmathy.pooparazzi.phone.ui.edit.EditUiState
import com.ffmathy.pooparazzi.phone.ui.history.HistoryActions
import com.ffmathy.pooparazzi.phone.ui.history.HistoryScreen
import com.ffmathy.pooparazzi.phone.ui.history.HistoryUiState
import com.ffmathy.pooparazzi.phone.ui.preview.EditSamples
import com.ffmathy.pooparazzi.phone.ui.preview.HistorySamples
import com.ffmathy.pooparazzi.phone.ui.preview.SettingsSamples
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsActions
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsScreen
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History, the edit sheet and Settings on the reference phone (390 × 844 dp, xxhdpi) with the reference scenario and a
 * fixed clock (V1_ADDITIONS §6), in English and Danish: `app/screenshots/<lang>/10-…16-*.png`, continuing Home's tour.
 * The status and navigation bars are left empty (24 dp insets, as on the reference device).
 */
abstract class HistoryEditSettingsScreenshots(private val language: String) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test fun historyDay() = screen("10-history-day", night = false) { History(HistorySamples.today()) }

    @Test fun historyNight() = screen("11-history-night", night = true) { History(HistorySamples.night()) }

    @Test fun editSleep() = sheet("12-edit-sleep", night = true, EditSamples.sleepNudged())

    /** The breastfeed form: type, one time block and the sides group (it fits without scrolling). */
    @Test fun editBreastfeed() = sheet("13-edit-breastfeed", night = false, EditSamples.breastfeed())

    @Test fun editDiaper() = sheet("14-edit-diaper", night = true, EditSamples.diaperPoop())

    @Test fun addPastEntry() = sheet("15-add-past-entry", night = false, EditSamples.addPastEntry())

    @Test fun settings() = screen("16-settings", night = true) {
        SettingsScreen(SettingsSamples.state(), SettingsActions.None, windowInsets = ReferenceInsets)
    }

    /** Not part of the tour (written under build/): the blocking validation line, and a past day. */
    @Test fun editValidation() = sheet("edit-validation", night = true, EditSamples.sleepEndBeforeStart(), tour = false)

    @Test fun historyYesterday() = screen("history-yesterday", night = false, tour = false) { History(HistorySamples.yesterday()) }

    /** 200 % font scale (DESIGN_SPEC §9.3): labels wrap, nothing clips. Written under build/ for review. */
    @Test fun largeFontHistory() {
        RuntimeEnvironment.setFontScale(2f)
        screen("font200-history", night = true, tour = false) { History(HistorySamples.today()) }
    }

    /** Tapping a time field opens the M3 time picker on its clock dial (written under build/ for review). */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun editTimePicker() {
        compose.setContent {
            NightLightTheme(night = true) {
                Box {
                    History(HistorySamples.today())
                    EditSheet(EditSamples.sleepNudged(), EditActions.None, windowInsets = ReferenceInsets)
                }
            }
        }
        compose.waitForIdle()
        val changeTime = ApplicationProvider.getApplicationContext<Application>().getString(R.string.edit_change_time)
        compose.onAllNodes(SemanticsMatcher("click label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == changeTime })[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        captureScreenRoboImage(path("edit-time-picker", tour = false))
    }

    @Test fun largeFontEdit() {
        RuntimeEnvironment.setFontScale(2f)
        sheet("font200-edit-breastfeed", night = true, EditSamples.breastfeed(), tour = false, scrollTo = EditTags.SIDES)
    }

    @Test fun largeFontSettings() {
        RuntimeEnvironment.setFontScale(2f)
        screen("font200-settings", night = false, tour = false) {
            SettingsScreen(SettingsSamples.state(), SettingsActions.None, windowInsets = ReferenceInsets)
        }
    }

    @Composable
    private fun History(state: HistoryUiState) = HistoryScreen(state, HistoryActions.None, windowInsets = ReferenceInsets)

    private fun screen(name: String, night: Boolean, tour: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent { NightLightTheme(night = night, content = content) }
        compose.onRoot().captureRoboImage(path(name, tour))
    }

    /** The edit sheet over History (as in the mockup); sheets live in their own window, so capture the screen. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun sheet(name: String, night: Boolean, state: EditUiState, tour: Boolean = true, scrollTo: String? = null) {
        compose.setContent {
            NightLightTheme(night = night) {
                Box {
                    History(HistorySamples.today())
                    EditSheet(state, EditActions.None, windowInsets = ReferenceInsets)
                }
            }
        }
        compose.waitForIdle()
        if (scrollTo != null) {
            compose.onNodeWithTag(scrollTo).performScrollTo()
            compose.waitForIdle()
        }
        captureScreenRoboImage(path(name, tour))
    }

    private fun path(name: String, tour: Boolean) =
        if (tour) "screenshots/$language/$name.png" else "build/outputs/roborazzi/$language-$name.png"

    private companion object {
        /** The reference device's 24 dp status and navigation bars (DESIGN_SPEC §3.3.1). */
        val ReferenceInsets = WindowInsets(top = 24.dp, bottom = 24.dp)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class HistoryEditSettingsScreenshotsEnglish : HistoryEditSettingsScreenshots("en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "da-w390dp-h844dp-xxhdpi", application = Application::class)
class HistoryEditSettingsScreenshotsDanish : HistoryEditSettingsScreenshots("da")
