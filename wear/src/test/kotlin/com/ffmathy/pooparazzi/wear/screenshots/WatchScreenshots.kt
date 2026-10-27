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
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.format.ClockFormatter
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.adjust.AdjustActions
import com.ffmathy.pooparazzi.wear.adjust.AdjustScreen
import com.ffmathy.pooparazzi.wear.diaper.DiaperActions
import com.ffmathy.pooparazzi.wear.diaper.DiaperScreen
import com.ffmathy.pooparazzi.wear.diaper.PoopOptionScreen
import com.ffmathy.pooparazzi.wear.diaper.colourOptions
import com.ffmathy.pooparazzi.wear.feed.FeedTypeActions
import com.ffmathy.pooparazzi.wear.feed.FeedTypeScreen
import com.ffmathy.pooparazzi.wear.feed.SideActions
import com.ffmathy.pooparazzi.wear.feed.SideScreen
import com.ffmathy.pooparazzi.wear.logged.LoggedScreen
import com.ffmathy.pooparazzi.wear.logged.UndoneScreen
import com.ffmathy.pooparazzi.wear.main.MainActions
import com.ffmathy.pooparazzi.wear.main.MainScreen
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.Guard
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.GuardTarget
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import com.ffmathy.pooparazzi.wear.today.TodayScreen
import com.ffmathy.pooparazzi.wear.ui.LocalFixedTimeText
import com.ffmathy.pooparazzi.wear.ui.XTimeText
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * A tour of the watch app with the reference scenario (V1_ADDITIONS §6) and a fixed clock, written to
 * `wear/screenshots/<lang>/<NN>-<name>.png`. Each language is a subclass with its own Robolectric qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
abstract class WatchScreenshots(private val lang: String) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        // The reference device shows 24 h times ("14:32"); Danish is 24 h regardless.
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun capture(name: String, now: Long, timeText: Boolean = true, after: () -> Unit = {}, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val time = ClockFormatter.from(context).format(now, WearSamples.zone)
        // Drive the clock by hand, so timed UI (the Logged drain arc) is captured mid-way, not run to its end.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalFixedTimeText provides time) {
                XWearTheme {
                    AppScaffold(timeText = { if (timeText) XTimeText() }) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(FRAME)
        after()
        // Past the 500 ms picker cooldowns and the 1 s Logged inert period.
        compose.mainClock.advanceTimeBy(1_200)
        compose.onRoot().captureRoboImage(filePath = "screenshots/$lang/$name.png", roborazziOptions = RoundWatch)
    }

    @Test
    fun s01_mainIdle() = capture("01-main-idle", SampleData.NOW_1432) { MainScreen(WearSamples.mainIdle(), MainActions()) }

    @Test
    fun s02_mainAsleep() = capture("02-main-asleep", SampleData.NOW_0312) { MainScreen(WearSamples.mainAsleep(), MainActions()) }

    @Test
    fun s03_mainBreastfeeding() =
        capture("03-main-breastfeeding", SampleData.NOW_1450) { MainScreen(WearSamples.mainFeeding(), MainActions()) }

    @Test
    fun s04_mainScrolled() = capture(
        "04-main-scrolled",
        SampleData.NOW_1432,
        // The end of the list: status cards, History and the sync footer above the pinned EdgeButton.
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4) },
    ) { MainScreen(WearSamples.mainIdle(), MainActions()) }

    @Test
    fun s05_feedType() = capture("05-feed-type", SampleData.NOW_1432) { FeedTypeScreen(WearSamples.feedDraft(), FeedTypeActions()) }

    @Test
    fun s06_side() = capture("06-side", SampleData.NOW_1450) { SideScreen(WearSamples.sideDraft(), SideActions()) }

    /** Right ticked first, then Left: the "1" / "2" badges and "Right → Left". */
    @Test
    fun s06_sideTicked() = capture("06-side-ticked", SampleData.NOW_1450) {
        SideScreen(WearSamples.sideDraft().copy(ticks = listOf(Breast.RIGHT, Breast.LEFT)), SideActions())
    }

    /** As it opens: the title and the Pee and Poop checkboxes (V1_ADDITIONS §3.2), Poop on. */
    @Test
    fun s07_diaper() = capture("07-diaper", SampleData.NOW_1432) { DiaperScreen(WearSamples.diaperDraft(), DiaperActions()) }

    /** Scrolled to Poop's options: Colour and Texture with their values (the list centres the item it scrolls to). */
    @Test
    fun s07b_diaperOptions() = capture(
        "07b-diaper-options",
        SampleData.NOW_1432,
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3) },
    ) { DiaperScreen(WearSamples.diaperDraft(), DiaperActions()) }

    @Test
    fun s08_diaperColourList() = capture("08-diaper-colour-list", SampleData.NOW_1432) {
        PoopOptionScreen(stringResource(CoreR.string.poop_colour), colourOptions(PoopColor.YELLOW), onSelect = {})
    }

    @Test
    fun s09_logged() = capture("09-logged", SampleData.NOW_1450, timeText = false) {
        LoggedScreen(WearSamples.receiptFeedEnded(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {})
    }

    @Test
    fun s10_adjust() = capture("10-adjust", SampleData.NOW_0312) { AdjustScreen(WearSamples.adjustRunningSleep(), AdjustActions()) }

    @Test
    fun s11_today() = capture("11-today", SampleData.NOW_1432) {
        TodayScreen(WearSamples.today(), phoneAvailable = true, onRow = {}, onMoreOnPhone = {})
    }

    // ---- Beyond the tour: states worth checking by eye -----------------------------------------

    /** The end of Today, where the collapsing "More on phone" EdgeButton has expanded. */
    @Test
    fun s11b_todayEnd() = capture(
        "11b-today-end",
        SampleData.NOW_1432,
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(WearSamples.today().entries.size) },
    ) { TodayScreen(WearSamples.today(), phoneAvailable = true, onRow = {}, onMoreOnPhone = {}) }

    /** While a breastfeed runs, the sleep toggle moves into the list (V1_ADDITIONS §2.2). */
    @Test
    fun s14_mainBreastfeedingList() = capture(
        "14-main-breastfeeding-list",
        SampleData.NOW_1450,
        after = { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3) },
    ) { MainScreen(WearSamples.mainFeeding(), MainActions()) }

    /** A tap within 2 s of a toggle: "Already / asleep" on the EdgeButton, no state change (DESIGN_SPEC §3.5.2). */
    @Test
    fun s14b_mainGuard() = capture("14b-main-asleep-guard", SampleData.NOW_0312) {
        val asleep = WearSamples.mainAsleep()
        MainScreen(asleep.copy(guard = Guard(GuardKind.ALREADY_ASLEEP, GuardTarget.SLEEP_TOGGLE, asleep.now + 2_000)), MainActions())
    }

    @Test
    fun s15_undone() = capture("15-undone", SampleData.NOW_1432, timeText = false) { UndoneScreen(onDone = {}) }

    /**
     * Above 1.15 font scale Feed | Diaper stay side by side while their labels fit (they stack once they do not, see
     * 16b); long words wrap instead of clipping.
     */
    @Test
    @Config(fontScale = 1.3f)
    fun s16_mainIdleLargeFont() =
        capture("16-main-idle-large-font", SampleData.NOW_1432) { MainScreen(WearSamples.mainIdle(), MainActions()) }

    /** At 200 % the labels no longer fit side by side, so Feed and Diaper stack (and the hero scrolls). */
    @Test
    @Config(fontScale = 2.0f)
    fun s16b_mainIdleHugeFont() =
        capture("16b-main-idle-font-200", SampleData.NOW_1432) { MainScreen(WearSamples.mainIdle(), MainActions()) }

    @Test
    @Config(fontScale = 1.3f)
    fun s17_feedTypeLargeFont() =
        capture("17-feed-type-large-font", SampleData.NOW_1432) { FeedTypeScreen(WearSamples.feedDraft(), FeedTypeActions()) }

    @Test
    @Config(fontScale = 1.3f)
    fun s18_loggedLargeFont() = capture("18-logged-large-font", SampleData.NOW_1432, timeText = false) {
        LoggedScreen(WearSamples.receiptFormula(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {})
    }
}

private const val FRAME = 16L

/** Screenshots of a round watch are round: the corners a round screen never shows are cropped away. */
internal val RoundWatch = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(applyDeviceCrop = true))

@Config(qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class EnglishScreenshots : WatchScreenshots("en")

@Config(qualifiers = "da-w227dp-h227dp-round-watch-xhdpi")
class DanishScreenshots : WatchScreenshots("da")
