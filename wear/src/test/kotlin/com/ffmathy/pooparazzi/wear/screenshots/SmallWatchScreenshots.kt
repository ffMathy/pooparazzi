package com.ffmathy.pooparazzi.wear.screenshots

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.feed.SideActions
import com.ffmathy.pooparazzi.wear.feed.SideScreen
import com.ffmathy.pooparazzi.wear.main.MainActions
import com.ffmathy.pooparazzi.wear.main.MainScreen
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.ui.LocalFixedTimeText
import com.ffmathy.pooparazzi.wear.ui.XTimeText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Main on a 192 dp round watch (DESIGN_SPEC §3.5.10): Small "Start sleep", 30 sp numeral, 83 × 56 buttons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
abstract class SmallWatchScreenshots(private val lang: String) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun capture(name: String, now: Long, state: com.ffmathy.pooparazzi.wear.state.MainUiState) =
        captureScreen(name, now) { MainScreen(state, MainActions()) }

    private fun captureScreen(name: String, now: Long, after: () -> Unit = {}, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val time = ClockFormatter.from(context).format(now, WearSamples.zone)
        compose.setContent {
            CompositionLocalProvider(LocalFixedTimeText provides time) {
                XWearTheme { AppScaffold(timeText = { XTimeText() }) { content() } }
            }
        }
        compose.waitForIdle()
        after()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "screenshots/$lang/$name.png", roborazziOptions = RoundWatch)
    }

    @Test
    fun s19_mainIdleSmall() = capture("19-main-idle-small", SampleData.NOW_1432, WearSamples.mainIdle())

    @Test
    fun s20_mainAsleepSmall() = capture("20-main-asleep-small", SampleData.NOW_0312, WearSamples.mainAsleep())

    /** Side on a 192 dp watch: Right sits partly behind "End feed" at rest, and scrolls clear of it. */
    @Test
    fun s21_sideSmall() = captureScreen("21-side-small", SampleData.NOW_1450) { SideScreen(WearSamples.sideDraft(), SideActions()) }

    @Test
    fun s21b_sideSmallScrolled() = captureScreen(
        "21b-side-small-scrolled",
        SampleData.NOW_1450,
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3) },
    ) { SideScreen(WearSamples.sideDraft().copy(ticks = listOf(Breast.LEFT)), SideActions()) }
}

@Config(qualifiers = "w192dp-h192dp-round-watch-xhdpi")
class EnglishSmallWatchScreenshots : SmallWatchScreenshots("en")

@Config(qualifiers = "da-w192dp-h192dp-round-watch-xhdpi")
class DanishSmallWatchScreenshots : SmallWatchScreenshots("da")
