package com.ffmathy.pooparazzi.wear.tile

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.expression.VersionBuilders.VersionInfo
import androidx.wear.protolayout.material3.materialScopeWithResources
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.MainActivity
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.surface.SurfaceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tile's content and behaviour as built (DESIGN_SPEC §3.6, §10.9; V1_ADDITIONS §2.2): titles, clickables, the
 * launch targets of Row A and of the background, the 48 dp edge target, and the timeline of the confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class TileLayoutTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun device(sizeDp: Int = 227, schemaMinor: Int = 500) = DeviceParameters.Builder()
        .setScreenWidthDp(sizeDp)
        .setScreenHeightDp(sizeDp)
        .setScreenDensity(2f)
        .setScreenShape(SCREEN_SHAPE_ROUND)
        .setDevicePlatform(DEVICE_PLATFORM_WEAR_OS)
        .setRendererSchemaVersion(VersionInfo.Builder().setMajor(1).setMinor(schemaMinor).build())
        .build()

    private fun build(state: TileUiState, sizeDp: Int = 227, dynamic: Boolean = false): String {
        val root: LayoutElement = materialScopeWithResources(context, ProtoLayoutScope(), device(sizeDp), false, TileTheme.colorScheme) {
            pooparazziTile(state, SurfaceText.from(context, state.zone), token = 5, dynamic = dynamic)
        }
        return root.toString()
    }

    /** A text or content description with exactly this value. */
    private fun String.has(value: String): Boolean = contains("value=$value,")

    @Test
    fun idleShowsTheNameFeedWithTimeSinceAndTheOneTapButtons() {
        val proto = build(WearSamples.tileIdle())
        assertTrue(proto.has("Noa"))
        assertTrue(proto.has("Feed"))
        assertTrue(proto.has("2h 10m ago"))
        assertTrue(proto.has("Feed. Last feed 2 hours 10 minutes ago"))
        // "Start sleep" says what it showed, so a stale tap cannot end a sleep started elsewhere.
        for (id in listOf("log_pee#5", "log_poop#5", "log_both#5", "sleep_start#5")) assertTrue(id, proto.contains(id))
        assertFalse(proto.contains("sleep_end"))
        assertTrue(proto.has("Start sleep"))
        // Feed opens the app on Feed type; it never logs from the tile.
        assertTrue(proto.contains(MainActivity.OPEN_FEED_TYPE))
        assertFalse(proto.contains("log_feed"))
        // No background action outside the confirmation.
        assertFalse(proto.contains(MainActivity.OPEN_LOGGED))
    }

    @Test
    fun withoutANameTheTitleIsTheLastDiaper() {
        val proto = build(WearSamples.tileIdle(babyName = ""))
        assertTrue(proto.has("Pee 48m ago"))
        assertTrue(proto.has("Last diaper, pee, 48 minutes ago"))
    }

    @Test
    fun whileBreastfeedingRowAEndsTheFeedOnTheSideScreen() {
        val proto = build(WearSamples.tileBreastfeeding())
        assertTrue(proto.has("End feed"))
        assertTrue(proto.has("18m"))
        assertTrue(proto.contains(MainActivity.OPEN_END_FEED))
        assertTrue(proto.has("End feed. Breastfeeding for 18 minutes"))
    }

    @Test
    fun asleepShowsTheDurationAndWokeUp() {
        val proto = build(WearSamples.tileAsleep())
        assertTrue(proto.has("Asleep 1h 07m"))
        assertTrue(proto.has("Woke up"))
        assertTrue(proto.has("Woke up, end sleep"))
        assertTrue(proto.has("1h 32m ago"))
        // "Woke up" names the sleep it showed.
        assertTrue(proto.contains("sleep_end#5#sample-sleep-0205"))
        assertFalse(proto.contains("sleep_start"))
    }

    @Test
    fun justLoggedTitleWinsAndTheBackgroundOpensLoggedWithUndo() {
        val state = WearSamples.tileJustLogged()
        assertEquals("sample-both-1432", state.undoEntryId)
        val proto = build(state)
        assertTrue(proto.has("Both 14:32 · "))
        assertTrue(proto.has("Undo"))
        assertTrue(proto.contains(MainActivity.OPEN_LOGGED))
        assertTrue(proto.contains("sample-both-1432"))
        // Buttons never relabel for feedback.
        assertTrue(proto.has("Start sleep"))
    }

    @Test
    fun guardTitleWinsOverEverything() {
        val state = WearSamples.tileGuard()
        assertEquals(TileTitle.Guard(GuardKind.ALREADY_ASLEEP), state.title)
        assertTrue(build(state).has("Already asleep"))
    }

    @Test
    fun theTitleIsHiddenBelow225dp() {
        assertFalse(build(WearSamples.tileIdle(), sizeDp = 192).has("Noa"))
    }

    @Test
    fun dynamicTextKeepsTheStaticValueAsFallback() {
        val proto = build(WearSamples.tileIdle(), dynamic = true)
        assertTrue(proto.has("2h 10m ago"))
        assertTrue(proto.contains("ConcatStringOp"))
    }

    @Test
    fun theConfirmationIsItsOwnTimelineEntry() {
        val state = WearSamples.tileJustLogged()
        assertEquals(listOf(SampleData.NOW_1432 + TILE_FEEDBACK_MILLIS), state.changes)
        assertTrue(state.at(SampleData.NOW_1432 + TILE_FEEDBACK_MILLIS).title is TileTitle.Name)
        assertNull(state.at(SampleData.NOW_1432 + TILE_FEEDBACK_MILLIS).undoEntryId)
    }

    @Test
    fun aConfirmationGoesWhenItsEntryIsUndone() {
        val feedback = WearSamples.tileJustLogged().feedback
        // The same moment, but the logged diaper was undone in the app (or deleted on the phone).
        val summary = summarize(SampleData.scenario(), SampleData.NOW_1432, SampleData.ZONE)
        val state = TileUiState.from(summary, SampleData.BABY_NAME, feedback, guard = null)
        assertTrue(state.title is TileTitle.Name)
        assertNull(state.undoEntryId)
    }

    @Test
    fun aSpokenConfirmationNamesTheDiaperInWords() {
        // Never "Both" or a "+" (DESIGN_SPEC §9.4).
        assertTrue(build(WearSamples.tileJustLogged()).has("Pee and poop at 14:32. Tap the tile to undo"))
    }

    @Test
    fun aSpokenLastDiaperNamesBothInWords() {
        val state = WearSamples.tileIdle(babyName = "").let { idle ->
            idle.copy(lastDiaper = idle.lastDiaper?.copy(poop = true))
        }
        assertTrue(build(state).has("Last diaper, pee and poop, 48 minutes ago"))
    }

    @Test
    fun aSleepCancelledOnTheTileOffersUndo() {
        val sleep = WearSamples.tileIdle()
        val feedback = TileFeedback(TileFeedbackKind.SLEEP_CANCELLED, "cancelled-sleep", sleep.now, null, sleep.now + TILE_FEEDBACK_MILLIS)
        val state = sleep.copy(feedback = feedback)
        // DESIGN_SPEC §4.4: "Sleep cancelled", with Undo; the background opens it on the watch.
        assertEquals("cancelled-sleep", state.undoEntryId)
        val proto = build(state)
        assertTrue(proto.has("Sleep cancelled · "))
        assertTrue(proto.has("Undo"))
        assertTrue(proto.has("Sleep cancelled. Tap the tile to undo"))
        assertTrue(proto.contains("cancelled-sleep"))
    }

    @Test
    fun aCancelledSleepConfirmationGoesWhenTheSleepIsBack() {
        // Undone in the app: the discarded sleep runs again, so there is nothing left to undo.
        val summary = summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, SampleData.ZONE)
        val running = checkNotNull(summary.runningSleep)
        val feedback = TileFeedback(TileFeedbackKind.SLEEP_CANCELLED, running.id, summary.now, null, summary.now + TILE_FEEDBACK_MILLIS)
        assertNull(TileUiState.from(summary, SampleData.BABY_NAME, feedback, guard = null).feedback)
        // Still discarded (not among the entries): it stands.
        val other = feedback.copy(entryId = "discarded")
        assertEquals(other, TileUiState.from(summary, SampleData.BABY_NAME, other, guard = null).feedback)
    }

    @Test
    fun onSmallWatchesRowACarriesTheConfirmation() {
        // No title below 225 dp (DESIGN_SPEC §3.6): the feedback is Row A's secondary label for the 10 s.
        val proto = build(WearSamples.tileJustLogged(), sizeDp = 192)
        assertFalse(proto.has("Noa"))
        assertTrue(proto, proto.has("Both · Undo") || proto.has("Both 14:32 · Undo") || proto.has("Both 14:32"))
        assertTrue(proto.contains("Pee and poop at 14:32. Tap the tile to undo. Feed."))
        // Without a confirmation Row A says the time since the last feed.
        assertTrue(build(WearSamples.tileIdle(), sizeDp = 192).has("2h 10m ago"))
    }

    @Test
    fun titlePriority() {
        val asleep = WearSamples.tileAsleep()
        assertTrue(asleep.title is TileTitle.Asleep)
        val loggedWhileAsleep = asleep.copy(feedback = WearSamples.tileJustLogged().feedback?.copy(untilMillis = asleep.now + 5_000))
        assertTrue(loggedWhileAsleep.title is TileTitle.Logged)
        assertTrue(WearSamples.tileIdle().title is TileTitle.Name)
        assertTrue(WearSamples.tileIdle(babyName = "  ").title is TileTitle.LastDiaper)
    }

    @Test
    fun oldRenderersGetStaticTextAndAMinuteRefresh() {
        assertFalse(VersionInfo.Builder().setMajor(1).setMinor(100).build().supportsDynamicText())
        assertTrue(VersionInfo.Builder().setMajor(1).setMinor(200).build().supportsDynamicText())
    }
}
