package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.ui.preview.StatisticsSamples
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsActions
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsRangeDialog
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsScreen
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsUiState
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
import java.time.LocalDate

/**
 * Statistics (P8) on the reference phone (390 × 844 dp, xxhdpi) with the sample history and a fixed clock (V1_ADDITIONS
 * §6, §8), in English and Danish: `app/screenshots/<lang>/21-…22-*.png`, continuing the tour after the notifications. The
 * other states are written under `build/outputs/roborazzi/` for review. The status and navigation bars are left empty
 * (24 dp insets, as on the reference device).
 */
abstract class StatisticsScreenshots(private val language: String) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test fun statisticsDay() = screen("21-statistics", night = false) { Statistics(StatisticsSamples.lastThirtyDays()) }

    @Test fun statisticsNight() = screen("22-statistics-night", night = true) { Statistics(StatisticsSamples.night()) }

    /** Not part of the tour (written under build/): the other rows, ranges and amounts of logging. */
    @Test fun statisticsSleep() = screen("statistics-sleep", night = false, tour = false) { Statistics(StatisticsSamples.sleep()) }

    @Test fun statisticsGaps() = screen("statistics-gaps", night = true, tour = false) { Statistics(StatisticsSamples.gaps()) }

    @Test fun statisticsCustom() = screen("statistics-custom", night = false, tour = false) { Statistics(StatisticsSamples.custom()) }

    @Test fun statisticsFirstDays() = screen("statistics-first-days", night = true, tour = false) { Statistics(StatisticsSamples.firstDays()) }

    @Test fun statisticsHalfYear() = screen("statistics-half-year", night = false, tour = false) { Statistics(StatisticsSamples.halfYear()) }

    @Test fun statisticsEmpty() = screen("statistics-empty", night = true, tour = false) { Statistics(StatisticsSamples.empty()) }

    /** 200 % font scale (DESIGN_SPEC §9.3): labels wrap, the numbers shrink, nothing clips. Written under build/. */
    @Test fun largeFontStatistics() {
        RuntimeEnvironment.setFontScale(2f)
        screen("font200-statistics", night = true, tour = false) { Statistics(StatisticsSamples.lastThirtyDays()) }
    }

    /** "Choose dates…": the M3 date range picker in its dialog (its own window, so capture the screen). */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun statisticsDatePicker() {
        compose.setContent {
            NightLightTheme(night = true) {
                StatisticsRangeDialog(
                    initialFrom = LocalDate.of(2026, 9, 26),
                    initialTo = LocalDate.of(2026, 10, 25),
                    firstDay = LocalDate.of(2026, 9, 23),
                    lastDay = LocalDate.of(2026, 10, 25),
                    onConfirm = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage(path("statistics-date-picker", tour = false))
    }

    @Composable
    private fun Statistics(state: StatisticsUiState) = StatisticsScreen(state, StatisticsActions.None, windowInsets = ReferenceInsets)

    private fun screen(name: String, night: Boolean, tour: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent { NightLightTheme(night = night, content = content) }
        compose.onRoot().captureRoboImage(path(name, tour))
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
class StatisticsScreenshotsEnglish : StatisticsScreenshots("en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "da-w390dp-h844dp-xxhdpi", application = Application::class)
class StatisticsScreenshotsDanish : StatisticsScreenshots("da")
