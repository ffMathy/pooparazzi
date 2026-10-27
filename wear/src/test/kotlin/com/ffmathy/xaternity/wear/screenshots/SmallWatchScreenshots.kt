package com.ffmathy.xaternity.wear.screenshots

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.xaternity.core.format.ClockFormatter
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.wear.main.MainActions
import com.ffmathy.xaternity.wear.main.MainScreen
import com.ffmathy.xaternity.wear.sample.WearSamples
import com.ffmathy.xaternity.wear.theme.XWearTheme
import com.ffmathy.xaternity.wear.ui.LocalFixedTimeText
import com.ffmathy.xaternity.wear.ui.XTimeText
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

    private fun capture(name: String, now: Long, state: com.ffmathy.xaternity.wear.state.MainUiState) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val time = ClockFormatter.from(context).format(now, WearSamples.zone)
        compose.setContent {
            CompositionLocalProvider(LocalFixedTimeText provides time) {
                XWearTheme { AppScaffold(timeText = { XTimeText() }) { MainScreen(state, MainActions()) } }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "screenshots/$lang/$name.png")
    }

    @Test
    fun s19_mainIdleSmall() = capture("19-main-idle-small", SampleData.NOW_1432, WearSamples.mainIdle())

    @Test
    fun s20_mainAsleepSmall() = capture("20-main-asleep-small", SampleData.NOW_0312, WearSamples.mainAsleep())
}

@Config(qualifiers = "w192dp-h192dp-round-watch-xhdpi")
class EnglishSmallWatchScreenshots : SmallWatchScreenshots("en")

@Config(qualifiers = "da-w192dp-h192dp-round-watch-xhdpi")
class DanishSmallWatchScreenshots : SmallWatchScreenshots("da")
