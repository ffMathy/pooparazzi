package com.ffmathy.pooparazzi.wear.screenshots

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.diaper.DiaperActions
import com.ffmathy.pooparazzi.wear.diaper.DiaperScreen
import com.ffmathy.pooparazzi.wear.feed.BottleActions
import com.ffmathy.pooparazzi.wear.feed.BottleScreen
import com.ffmathy.pooparazzi.wear.feed.SideActions
import com.ffmathy.pooparazzi.wear.feed.SideScreen
import com.ffmathy.pooparazzi.wear.main.MainActions
import com.ffmathy.pooparazzi.wear.main.MainScreen
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.ui.LocalBabyTimeText
import com.ffmathy.pooparazzi.wear.ui.LocalFixedTimeText
import com.ffmathy.pooparazzi.wear.ui.rememberBabyTimeText
import com.ffmathy.pooparazzi.wear.ui.Option
import com.ffmathy.pooparazzi.wear.ui.OptionScreen
import com.ffmathy.pooparazzi.wear.ui.XTimeText
import com.github.takahirom.roborazzi.captureRoboImage
import com.ffmathy.pooparazzi.wear.time.TimeScreen
import com.ffmathy.pooparazzi.core.R as CoreR
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A 192 dp round watch (DESIGN_SPEC §3.5.10): Main with its Small "Start sleep", 30 sp numeral and 83 × 56 buttons; the
 * pickers with their Small Save.
 */
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

    private fun captureScreen(name: String, now: Long, babyLabel: String? = null, after: () -> Unit = {}, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val time = ClockFormatter.from(context).format(now, WearSamples.zone)
        compose.setContent {
            CompositionLocalProvider(LocalFixedTimeText provides time) {
                XWearTheme {
                    CompositionLocalProvider(LocalBabyTimeText provides rememberBabyTimeText(babyLabel)) {
                        AppScaffold(timeText = { XTimeText() }) { content() }
                    }
                }
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
    fun s19b_mainAfterBreastSmall() = capture("19b-main-after-breast-small", SampleData.NOW_1450, WearSamples.mainAfterBreastfeed())

    @Test
    fun s20_mainAsleepSmall() = capture("20-main-asleep-small", SampleData.NOW_0312, WearSamples.mainAsleep())

    /** Side on a 192 dp watch: Right sits partly behind the Small "Save" at rest, and scrolls clear of it. */
    @Test
    fun s21_sideSmall() = captureScreen("21-side-small", SampleData.NOW_1432) { SideScreen(WearSamples.sideDraft(), SideActions()) }

    /** Scrolled to the end: the order line and the inline time clear of Save. */
    @Test
    fun s21b_sideSmallScrolled() = captureScreen(
        "21b-side-small-scrolled",
        SampleData.NOW_1432,
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5) },
    ) { SideScreen(WearSamples.sideDraft().copy(ticks = listOf(Breast.LEFT)), SideActions()) }

    /** Time (from Logged) on a 192 dp watch: the ±5m buttons stay clear of Done. */
    @Test
    fun s21c_timeSmall() = captureScreen("21c-time-small", SampleData.NOW_1432) {
        TimeScreen(WearSamples.timeDraft(), onShift = {}, onDone = {})
    }

    /** Bottle as it opens on a 192 dp watch: Milk, and Formula partly behind the Small Save. */
    @Test
    fun s21d_bottleSmall() = captureScreen("21d-bottle-small", SampleData.NOW_1432) {
        BottleScreen(WearSamples.emptyBottleDraft(), BottleActions())
    }

    /** Scrolled to the amount: the stepper (or the value over its steps, where it does not fit) and the chips. */
    @Test
    fun s21e_bottleSmallAmount() = captureScreen(
        "21e-bottle-small-amount",
        SampleData.NOW_1432,
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4) },
    ) { BottleScreen(WearSamples.bottleDraft(), BottleActions()) }

    /** As 21e with nothing picked and a 120 ml last bottle: "No amount" (its tightest fit) and the history glyph. */
    @Test
    fun s21i_bottleSmallNoAmount() = captureScreen(
        "21i-bottle-small-no-amount",
        SampleData.NOW_1432,
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4) },
    ) { BottleScreen(WearSamples.emptyBottleDraft().copy(lastAmountMl = 120), BottleActions()) }

    /** Diaper with Poop on a 192 dp watch: the tiles, then "Colour" above Save, showing what Poop revealed. */
    @Test
    fun s21f_diaperSmall() = captureScreen("21f-diaper-small", SampleData.NOW_1432) {
        DiaperScreen(WearSamples.diaperDraft(), DiaperActions())
    }

    /** Scrolled to the colours: three across, as on the larger watch. */
    @Test
    fun s21g_diaperSmallColours() = captureScreen(
        "21g-diaper-small-colours",
        SampleData.NOW_1432,
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3) },
    ) { DiaperScreen(WearSamples.diaperDraft(), DiaperActions()) }

    /** The worst case: Diaper with Poop at 200 % on a 192 dp watch. Nothing clips; the list grows, Save stays pinned. */
    @Test
    @Config(fontScale = 2.0f)
    fun s21h_diaperSmallHugeFont() = captureScreen("21h-diaper-small-font-200", SampleData.NOW_1432) {
        DiaperScreen(WearSamples.diaperDraft(), DiaperActions())
    }

    /** Twins on a 192 dp watch with a long name: the time text cuts the name, never the time. */
    @Test
    fun s22d_mainTwinsSmallLongName() =
        capture("22d-main-twins-small-long-name", SampleData.NOW_1432, WearSamples.mainIdleTwins().withFirstName(LONG_NAME))

    /** Twins on a 192 dp watch: "Noa · 14:32" above the hero, which moves down a little to stay clear of it. */
    @Test
    fun s22e_mainTwinsSmall() = capture("22e-main-twins-small", SampleData.NOW_1432, WearSamples.mainIdleTwins())

    /** A sleep runs for Noa: the Lamp under "Noa · 03:12". */
    @Test
    fun s22f_mainTwinsAsleepSmall() =
        capture("22f-main-twins-asleep-small", SampleData.NOW_0312, WearSamples.mainAsleep().copy(babies = SampleData.twinSettings.babies))

    /** The baby picker on a 192 dp watch, a long name wrapping on two lines. */
    @Test
    fun s23b_babyPickerSmall() = captureScreen("23b-baby-picker-small", SampleData.NOW_1432, babyLabel = "Ida") {
        OptionScreen(
            stringResource(CoreR.string.baby_switch),
            listOf(Option(Baby.FIRST_ID, LONG_NAME, null, false), Option(SampleData.TWIN_ID, SampleData.TWIN_NAME, null, true)),
            onSelect = {},
        )
    }
}

@Config(qualifiers = "w192dp-h192dp-round-watch-xhdpi")
class EnglishSmallWatchScreenshots : SmallWatchScreenshots("en")

@Config(qualifiers = "da-w192dp-h192dp-round-watch-xhdpi")
class DanishSmallWatchScreenshots : SmallWatchScreenshots("da")
