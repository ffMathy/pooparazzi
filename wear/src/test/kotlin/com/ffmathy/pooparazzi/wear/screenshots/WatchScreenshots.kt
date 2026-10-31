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
import com.ffmathy.pooparazzi.wear.diaper.DiaperActions
import com.ffmathy.pooparazzi.wear.diaper.DiaperScreen
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
import com.ffmathy.pooparazzi.wear.ui.LocalBabyTimeText
import com.ffmathy.pooparazzi.wear.ui.LocalFixedTimeText
import com.ffmathy.pooparazzi.wear.ui.rememberBabyTimeText
import com.ffmathy.pooparazzi.wear.ui.Option
import com.ffmathy.pooparazzi.wear.ui.OptionScreen
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

    private fun capture(
        name: String,
        now: Long,
        timeText: Boolean = true,
        babyLabel: String? = null,
        after: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val time = ClockFormatter.from(context).format(now, WearSamples.zone)
        // Drive the clock by hand, so timed UI (the Logged drain arc) is captured mid-way, not run to its end.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalFixedTimeText provides time) {
                XWearTheme {
                    CompositionLocalProvider(LocalBabyTimeText provides rememberBabyTimeText(babyLabel)) {
                        AppScaffold(timeText = { if (timeText) XTimeText() }) { content() }
                    }
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

    /** 18 minutes after the 14:32 breast feed: Main stays idle (a breast feed is an instant), Feed "18m ago". */
    @Test
    fun s03_mainAfterBreast() =
        capture("03-main-after-breast", SampleData.NOW_1450) { MainScreen(WearSamples.mainAfterBreastfeed(), MainActions()) }

    @Test
    fun s05_feedType() = capture("05-feed-type", SampleData.NOW_1432) { FeedTypeScreen(WearSamples.feedDraft(), FeedTypeActions()) }

    /** After Breast on Feed type: "Which side?", "Breast · 14:32", Left / Right and Save. */
    @Test
    fun s06_side() = capture("06-side", SampleData.NOW_1432) { SideScreen(WearSamples.sideDraft(), SideActions()) }

    /** Right ticked first, then Left: the "1" / "2" badges and "Right → Left". */
    @Test
    fun s06_sideTicked() = capture("06-side-ticked", SampleData.NOW_1432) {
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
        OptionScreen(stringResource(CoreR.string.poop_colour), colourOptions(PoopColor.YELLOW), onSelect = {})
    }

    @Test
    fun s09_logged() = capture("09-logged", SampleData.NOW_1432, timeText = false) {
        LoggedScreen(WearSamples.receiptBreast(), WearSamples.syncAt1431, onUndo = {}, onDismiss = {}, onTimeout = {})
    }

    // ---- Beyond the tour: states worth checking by eye -----------------------------------------

    /** A tap within 2 s of a toggle: "Already / asleep" on the EdgeButton, no state change (DESIGN_SPEC §3.5.2). */
    @Test
    fun s14b_mainGuard() = capture("14b-main-asleep-guard", SampleData.NOW_0312) {
        val asleep = WearSamples.mainAsleep()
        MainScreen(asleep.copy(guard = Guard(GuardKind.ALREADY_ASLEEP, GuardTarget.SLEEP_TOGGLE, asleep.now + 2_000)), MainActions())
    }

    // ---- Twins (BABIES_AND_BACKUP §2.5) --------------------------------------------------------

    /** Two babies: "Noa · 14:32" in the time text; otherwise Main is as with one. */
    @Test
    fun s22_mainTwins() = capture("22-main-twins", SampleData.NOW_1432) { MainScreen(WearSamples.mainIdleTwins(), MainActions()) }

    /** A long name at 200 %: cut with an ellipsis before the time, which stays whole; the button cuts it too. */
    @Test
    @Config(fontScale = 2.0f)
    fun s22c_mainTwinsLongNameHugeFont() = capture(
        "22c-main-twins-long-name-font-200",
        SampleData.NOW_1432,
    ) { MainScreen(WearSamples.mainIdleTwins().withFirstName(LONG_NAME), MainActions()) }

    /** Every other screen names the baby too, its title clear of the longer line. */
    @Test
    @Config(fontScale = 1.3f)
    fun s22g_feedTypeTwinsLargeFont() = capture("22g-feed-type-twins-large-font", SampleData.NOW_1432, babyLabel = SampleData.TWIN_NAME) {
        FeedTypeScreen(WearSamples.feedDraft(), FeedTypeActions())
    }

    /** The picker: one button per baby, the current one marked; the other screens name the baby too. */
    @Test
    fun s23_babyPicker() = capture("23-baby-picker", SampleData.NOW_1432, babyLabel = SampleData.BABY_NAME) {
        val twins = SampleData.twinSettings
        OptionScreen(
            stringResource(CoreR.string.baby_switch),
            twins.babies.map { Option(it.id, it.name, null, it.id == twins.selectedBaby.id) },
            onSelect = {},
        )
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

    /** The breast order of the last feed at 130 %: the label steps down inside the round edge. */
    @Test
    @Config(fontScale = 1.3f)
    fun s16c_mainAfterBreastLargeFont() =
        capture("16c-main-after-breast-large-font", SampleData.NOW_1450) { MainScreen(WearSamples.mainAfterBreastfeed(), MainActions()) }

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

/** A name as long as the phone lets the parent type (40 characters). */
internal const val LONG_NAME = "Christian Frederik Valdemar Oldenborg"

internal fun com.ffmathy.pooparazzi.wear.state.MainUiState.withFirstName(name: String) =
    copy(babies = babies.map { if (it.id == babies.first().id) it.copy(name = name) else it })

/** Screenshots of a round watch are round: the corners a round screen never shows are cropped away. */
internal val RoundWatch = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(applyDeviceCrop = true))

@Config(qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class EnglishScreenshots : WatchScreenshots("en")

@Config(qualifiers = "da-w227dp-h227dp-round-watch-xhdpi")
class DanishScreenshots : WatchScreenshots("da")
