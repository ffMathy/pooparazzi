package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.phone.ui.edit.EditActions
import com.ffmathy.pooparazzi.phone.ui.edit.EditSheet
import com.ffmathy.pooparazzi.phone.ui.edit.EditUiState
import com.ffmathy.pooparazzi.phone.ui.history.HistoryActions
import com.ffmathy.pooparazzi.phone.ui.history.HistoryScreen
import com.ffmathy.pooparazzi.phone.ui.home.HomeActions
import com.ffmathy.pooparazzi.phone.ui.home.HomeScreen
import com.ffmathy.pooparazzi.phone.ui.home.HomeUiState
import com.ffmathy.pooparazzi.phone.ui.preview.EditSamples
import com.ffmathy.pooparazzi.phone.ui.preview.HistorySamples
import com.ffmathy.pooparazzi.phone.ui.preview.HomeSamples
import com.ffmathy.pooparazzi.phone.ui.preview.SettingsSamples
import com.ffmathy.pooparazzi.phone.ui.preview.StatisticsSamples
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsActions
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsScreen
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsActions
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsScreen
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
 * Home, its sheets, the edit sheet, History, Settings and Statistics on real-phone configurations rather than the
 * 390 dp reference: a narrow 360 dp phone at 100 % and 130 % font, and a 411 dp phone at 115 % font (a larger "display
 * size" and font size, as set on many phones). Checks that nothing clips or overflows: offset chips share one row,
 * cards grow to fit and labels wrap or shrink. Written under `build/outputs/roborazzi/fit/<config>/` for review, not
 * part of the tour.
 */
abstract class DeviceFitScreenshots(private val config: String, private val fontScale: Float) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
        RuntimeEnvironment.setFontScale(fontScale)
    }

    @Test fun homeIdle() = home("01-home-idle", night = false, HomeSamples.idle())

    @Test fun homeAsleep() = home("02-home-asleep", night = true, HomeSamples.asleep())

    /** A first day with only a running sleep: the Today grid shows 0 · 0 · 0 · 47m (numbers on one baseline). */
    @Test fun homeFirstSleep() = home("02b-home-first-sleep", night = true, HomeSamples.firstSleep())

    @Test fun homeAdjustStart() = homeSheet("03-home-adjust-start", HomeSamples.adjustingStart())

    @Test fun homeReceipt() = homeSheet("04-home-receipt", HomeSamples.justLogged())

    @Test fun homeGuardNotice() = card("04b-home-guard-notice", HomeSamples.guardNotice())

    @Test fun homeLoggedDuringSleep() = homeSheet("05-home-logged-during-sleep", HomeSamples.loggedDuringSleep())

    @Test fun homeBreastLogged() = homeSheet("06-home-breast-logged", HomeSamples.breastLogged())

    @Test fun feedSheet() = homeSheet("07-feed-sheet", HomeSamples.feedSheet())

    @Test fun breastStep() = homeSheet("08-breast-step", HomeSamples.breastSheet(listOf(Breast.RIGHT, Breast.LEFT)))

    @Test fun diaperSheet() = homeSheet("09-diaper-sheet", HomeSamples.diaperSheet())

    @Test fun history() = screen("10-history") { HistoryScreen(HistorySamples.today(), HistoryActions.None, windowInsets = Insets) }

    @Test fun editSleep() = editSheet("11-edit-sleep", EditSamples.sleepNudged())

    @Test fun editBreastfeed() = editSheet("12-edit-breastfeed", EditSamples.breastfeed())

    @Test fun editDiaper() = editSheet("13-edit-diaper", EditSamples.diaperPoop())

    @Test fun addPastEntry() = editSheet("14-add-past-entry", EditSamples.addPastEntry())

    @Test fun settings() = screen("15-settings") { SettingsScreen(SettingsSamples.state(), SettingsActions.None, windowInsets = Insets) }

    /** Statistics: the columns shrink to 138 dp at 360 dp, the numbers autosize, the chip label may wrap. */
    @Test fun statistics() = screen("16-statistics") {
        StatisticsScreen(StatisticsSamples.lastThirtyDays(), StatisticsActions.None, windowInsets = Insets)
    }

    private fun home(name: String, night: Boolean, state: HomeUiState) {
        compose.setContent { NightLightTheme(night = night) { HomeScreen(state, HomeActions.None, windowInsets = Insets) } }
        compose.onRoot().captureRoboImage(path(name))
    }

    /** A card state: freeze the clock just after the 150 ms fade-in. */
    private fun card(name: String, state: HomeUiState) {
        compose.mainClock.autoAdvance = false
        compose.setContent { NightLightTheme(night = true) { HomeScreen(state, HomeActions.None, windowInsets = Insets) } }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage(path(name))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun homeSheet(name: String, state: HomeUiState) {
        compose.setContent { NightLightTheme(night = true) { HomeScreen(state, HomeActions.None, windowInsets = Insets) } }
        compose.waitForIdle()
        captureScreenRoboImage(path(name))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun editSheet(name: String, state: EditUiState) {
        compose.setContent {
            NightLightTheme(night = true) {
                Box {
                    HistoryScreen(HistorySamples.today(), HistoryActions.None, windowInsets = Insets)
                    EditSheet(state, EditActions.None, windowInsets = Insets)
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage(path(name))
    }

    private fun screen(name: String, content: @Composable () -> Unit) {
        compose.setContent { NightLightTheme(night = true, content = content) }
        compose.onRoot().captureRoboImage(path(name))
    }

    private fun path(name: String) = "build/outputs/roborazzi/fit/$config/$name.png"

    private companion object {
        /** A gesture-navigation phone: a 32 dp status bar and a 24 dp navigation bar. */
        val Insets = WindowInsets(top = 32.dp, bottom = 24.dp)
    }
}

private const val SDK = 36

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w360dp-h780dp-xxhdpi", application = Application::class)
class DeviceFitEnglish360 : DeviceFitScreenshots("en-w360-font100", 1f)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w360dp-h780dp-xxhdpi", application = Application::class)
class DeviceFitEnglish360Font130 : DeviceFitScreenshots("en-w360-font130", 1.3f)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w411dp-h914dp-xxhdpi", application = Application::class)
class DeviceFitEnglish411Font115 : DeviceFitScreenshots("en-w411-font115", 1.15f)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "da-w360dp-h780dp-xxhdpi", application = Application::class)
class DeviceFitDanish360 : DeviceFitScreenshots("da-w360-font100", 1f)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "da-w360dp-h780dp-xxhdpi", application = Application::class)
class DeviceFitDanish360Font130 : DeviceFitScreenshots("da-w360-font130", 1.3f)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "da-w411dp-h914dp-xxhdpi", application = Application::class)
class DeviceFitDanish411Font115 : DeviceFitScreenshots("da-w411-font115", 1.15f)
