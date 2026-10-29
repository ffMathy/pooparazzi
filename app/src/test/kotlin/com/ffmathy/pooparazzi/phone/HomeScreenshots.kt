package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.phone.ui.home.HomeActions
import com.ffmathy.pooparazzi.phone.ui.home.HomeScreen
import com.ffmathy.pooparazzi.phone.ui.home.HomeUiState
import com.ffmathy.pooparazzi.phone.ui.preview.HomeSamples
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
 * Home and its sheets on the reference phone (390 × 844 dp, xxhdpi) with the reference scenario and a fixed clock
 * (V1_ADDITIONS §6), in English and Danish. `./gradlew :app:recordRoborazziDebug` writes
 * `app/screenshots/<lang>/<NN>-<name>.png`, numbered like a tour of the app.
 *
 * The status and navigation bars are left empty: the screen is laid out edge to edge from 24 dp insets, as on the
 * reference device.
 */
abstract class HomeScreenshots(private val language: String) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        // The reference scenario reads "14:32"; Danish always uses 24 h, English follows this setting.
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test fun homeNight() = home("01-home-night", night = true, HomeSamples.idle())

    @Test fun homeDay() = home("02-home-day", night = false, HomeSamples.idle())

    @Test fun homeAsleepNight() = home("03-home-asleep-night", night = true, HomeSamples.asleep())

    /** "Adjust start" on the running Lamp: the Adjust time sheet, with no timeout. */
    @Test fun adjustStartSheet() = sheet("03-home-adjust-start", night = true, HomeSamples.adjustingStart())

    @Test fun homeBreastLogged() = home("04-home-breast-logged", night = false, HomeSamples.breastLogged())

    @Test fun homeUndoCard() {
        // Freeze the clock just after the card's 150 ms fade-in so the drain line is still almost full.
        compose.mainClock.autoAdvance = false
        compose.setContent { Themed(night = true) { HomeScreen(HomeSamples.justLogged(), HomeActions.None, windowInsets = ReferenceInsets) } }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage(path("05-home-undo-card"))
    }

    /** "Adjust time" on the undo card: the sheet over the paused card. */
    @Test fun adjustTimeSheet() = sheet("05-home-adjust-time", night = false, HomeSamples.adjustingFeed())

    @Test fun feedSheet() = sheet("06-feed-sheet", night = true, HomeSamples.feedSheet())

    @Test fun breastSheet() = sheet("07-breast-sheet", night = false, HomeSamples.breastSheet())

    @Test fun breastSheetTicked() = sheet("07-breast-sheet-ticked", night = true, HomeSamples.breastSheet(listOf(Breast.RIGHT, Breast.LEFT)))

    @Test fun diaperSheet() = sheet("08-diaper-sheet", night = true, HomeSamples.diaperSheet())

    @Test fun diaperSheetHint() = sheet("09-diaper-sheet-hint", night = true, HomeSamples.diaperSheetHint())

    /**
     * Not part of the tour: checks that Home survives a 200 % font scale (DESIGN_SPEC §9.3): labels wrap, the area
     * above the buttons scrolls and nothing clips. Written under build/ for review.
     */
    @Test fun largeFont() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { Themed(night = true) { HomeScreen(HomeSamples.breastLogged(), HomeActions.None, windowInsets = ReferenceInsets) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/font200-$language-home.png")
    }

    @Test fun largeFontCard() {
        RuntimeEnvironment.setFontScale(2f)
        compose.mainClock.autoAdvance = false
        compose.setContent { Themed(night = true) { HomeScreen(HomeSamples.justLogged(), HomeActions.None, windowInsets = ReferenceInsets) } }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/font200-$language-card.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun largeFontSheets() {
        RuntimeEnvironment.setFontScale(2f)
        val state = mutableStateOf(HomeSamples.diaperSheet())
        compose.setContent { Themed(night = true) { HomeScreen(state.value, HomeActions.None, windowInsets = ReferenceInsets) } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/font200-$language-diaper.png")
    }

    private fun home(name: String, night: Boolean, state: HomeUiState) {
        compose.setContent { Themed(night) { HomeScreen(state, HomeActions.None, windowInsets = ReferenceInsets) } }
        compose.onRoot().captureRoboImage(path(name))
    }

    /** Sheets live in their own dialog window: capture the whole screen once the sheet has settled. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun sheet(name: String, night: Boolean, state: HomeUiState) {
        compose.setContent { Themed(night) { HomeScreen(state, HomeActions.None, windowInsets = ReferenceInsets) } }
        compose.waitForIdle()
        captureScreenRoboImage(path(name))
    }

    private fun path(name: String) = "screenshots/$language/$name.png"

    @Composable
    private fun Themed(night: Boolean, content: @Composable () -> Unit) = NightLightTheme(night = night, content = content)

    private companion object {
        /** The reference device's 24 dp status and navigation bars (DESIGN_SPEC §3.3.1). */
        val ReferenceInsets = WindowInsets(top = 24.dp, bottom = 24.dp)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class HomeScreenshotsEnglish : HomeScreenshots("en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "da-w390dp-h844dp-xxhdpi", application = Application::class)
class HomeScreenshotsDanish : HomeScreenshots("da")
