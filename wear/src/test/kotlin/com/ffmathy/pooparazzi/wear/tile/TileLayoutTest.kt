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
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.MainActivity
import com.ffmathy.pooparazzi.wear.sample.WearSamples
import com.ffmathy.pooparazzi.wear.glance.GlanceTitle
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.surface.SurfaceText
import com.ffmathy.pooparazzi.core.settings.Settings as CoreSettings
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
    fun idleShowsTheNameFeedAndDiaperWithTimeSinceAndTheToggle() {
        val proto = build(WearSamples.tileIdle())
        assertTrue(proto.has("Noa"))
        assertTrue(proto.has("Feed"))
        assertTrue(proto.has("2h 10m ago"))
        assertTrue(proto.has("Feed. Last feed 2 hours 10 minutes ago"))
        // "Start sleep" says what it showed, so a stale tap cannot end a sleep started elsewhere.
        assertTrue(proto.contains("sleep_start#5"))
        assertFalse(proto.contains("sleep_end"))
        assertTrue(proto.has("Start sleep"))
        // Feed and Diaper open the app on Feed type and Diaper; they never log from the tile.
        assertTrue(proto.contains(MainActivity.OPEN_FEED_TYPE))
        assertTrue(proto.contains("open_diaper"))
        assertTrue(proto.has("Diaper"))
        assertTrue(proto.has("48m ago"))
        assertTrue(proto.has("Diaper. Last diaper 48 minutes ago"))
        assertFalse(proto.contains("log_"))
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
    fun afterABreastFeedRowAIsStillFeed() {
        val proto = build(WearSamples.tileAfterBreastfeed())
        assertTrue(proto.has("Feed"))
        assertTrue(proto.has("18m ago"))
        assertFalse(proto.has("End feed"))
        assertTrue(proto.contains(MainActivity.OPEN_FEED_TYPE))
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
        assertEquals("sample-nap-1350", state.undoEntryId)
        val proto = build(state)
        assertTrue(proto.has("Woke up 14:32 · "))
        assertTrue(proto.has("Undo"))
        assertTrue(proto.contains(MainActivity.OPEN_LOGGED))
        assertTrue(proto.contains("sample-nap-1350"))
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
        assertTrue(state.at(SampleData.NOW_1432 + TILE_FEEDBACK_MILLIS).title.let { it is TileTitle.Glance && it.title is GlanceTitle.Name })
        assertNull(state.at(SampleData.NOW_1432 + TILE_FEEDBACK_MILLIS).undoEntryId)
    }

    @Test
    fun aConfirmationGoesWhenItsEntryIsUndone() {
        val feedback = WearSamples.tileJustLogged().feedback
        // The same moment, but the nap was undone in the app (or deleted on the phone).
        val summary = summarize(SampleData.scenario(), SampleData.NOW_1432, SampleData.ZONE)
        val state = TileUiState.from(summary, SampleData.BABY_NAME, feedback, guard = null)
        assertTrue(state.title.let { it is TileTitle.Glance && it.title is GlanceTitle.Name })
        assertNull(state.undoEntryId)
    }

    @Test
    fun aSpokenConfirmationSaysWhatWasLoggedAndHowToUndo() {
        assertTrue(build(WearSamples.tileJustLogged()).has("Woke up at 14:32. Tap the tile to undo"))
    }

    @Test
    fun withoutDiapersRowBSaysSo() {
        val proto = build(WearSamples.tileIdle().let { it.copy(glance = it.glance.copy(lastDiaper = null)) })
        assertTrue(proto.has("No diapers yet"))
        assertTrue(proto.has("Diaper. No diapers yet"))
    }

    @Test
    fun aSpokenLastDiaperNamesBothInWords() {
        val state = WearSamples.tileIdle(babyName = "").let { idle ->
            idle.copy(glance = idle.glance.copy(lastDiaper = idle.lastDiaper?.copy(poop = true)))
        }
        assertTrue(build(state).has("Last diaper, pee and poop, 48 minutes ago"))
    }

    @Test
    fun aSleepCancelledOnTheTileOffersUndo() {
        val sleep = WearSamples.tileIdle()
        val feedback = TileFeedback(TileFeedbackKind.SLEEP_CANCELLED, "cancelled-sleep", sleep.now, sleep.now + TILE_FEEDBACK_MILLIS)
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
        val feedback = TileFeedback(TileFeedbackKind.SLEEP_CANCELLED, running.id, summary.now, summary.now + TILE_FEEDBACK_MILLIS)
        assertNull(TileUiState.from(summary, SampleData.BABY_NAME, feedback, guard = null).feedback)
        // Still discarded (not among the entries): it stands.
        val other = feedback.copy(entryId = "discarded")
        assertEquals(other, TileUiState.from(summary, SampleData.BABY_NAME, other, guard = null).feedback)
    }

    // ---- Babies (BABIES_AND_BACKUP §2.5) --------------------------------------------------------

    @Test
    fun withTwoUnnamedBabiesTheIdleTitleIsTheCurrentBabysLabel() {
        val labels = LabelFormatter(context)
        val twins = CoreSettings(babies = listOf(Baby(Baby.FIRST_ID), Baby(SampleData.TWIN_ID)))
        assertEquals("Baby 1", TileUiState.titleName(twins, labels))
        val second = twins.withSelectedBaby(SampleData.TWIN_ID)
        assertEquals("Baby 2", TileUiState.titleName(second, labels))
        val state = WearSamples.tileIdle(babyName = TileUiState.titleName(second, labels))
        assertEquals(TileTitle.Glance(GlanceTitle.Name("Baby 2")), state.title)
        assertTrue(build(state).has("Baby 2"))
        // The usual priorities still hold: a running sleep wins over the label.
        assertTrue(WearSamples.tileAsleep().let { it.copy(glance = it.glance.copy(babyName = "Baby 2")) }.title.let { it is TileTitle.Glance && it.title is GlanceTitle.Asleep })
    }

    @Test
    fun withOneBabyTheTitleIsAsBefore() {
        val labels = LabelFormatter(context)
        // Unnamed: no "Baby" title, the last diaper as before.
        assertEquals("", TileUiState.titleName(CoreSettings(), labels))
        assertTrue(WearSamples.tileIdle(babyName = TileUiState.titleName(CoreSettings(), labels)).title.let { it is TileTitle.Glance && it.title is GlanceTitle.LastDiaper })
        assertEquals(SampleData.BABY_NAME, TileUiState.titleName(SampleData.settings, labels))
        // Two babies, the named one current: its name.
        assertEquals(SampleData.BABY_NAME, TileUiState.titleName(SampleData.twinSettings, labels))
    }

    @Test
    fun aConfirmationForAnotherBabyIsDroppedAfterASwitch() {
        val logged = WearSamples.tileJustLogged()
        val feedback = checkNotNull(logged.feedback).copy(babyId = Baby.FIRST_ID)
        val summary = summarize(SampleData.scenario() + Entry.sleep(SampleData.at(13, 50), SampleData.NOW_1432, id = feedback.entryId), SampleData.NOW_1432, SampleData.ZONE)
        assertEquals(feedback, TileUiState.from(summary, SampleData.BABY_NAME, feedback, guard = null, babyId = Baby.FIRST_ID).feedback)
        // A "Sleep cancelled" is not among the entries either way: only its baby tells it apart.
        val cancelled = TileFeedback(TileFeedbackKind.SLEEP_CANCELLED, "discarded", summary.now, summary.now + TILE_FEEDBACK_MILLIS, Baby.FIRST_ID)
        assertNull(TileUiState.from(summarize(emptyList(), SampleData.NOW_1432, SampleData.ZONE), "Ida", cancelled, guard = null, babyId = SampleData.TWIN_ID).feedback)
        assertEquals(cancelled, TileUiState.from(summary, SampleData.BABY_NAME, cancelled, guard = null, babyId = Baby.FIRST_ID).feedback)
    }

    @Test
    fun onSmallWatchesRowACarriesTheConfirmation() {
        // No title below 225 dp (DESIGN_SPEC §3.6): the feedback is Row A's secondary label for the 10 s.
        val proto = build(WearSamples.tileJustLogged(), sizeDp = 192)
        assertFalse(proto.has("Noa"))
        assertTrue(proto, proto.has("Woke up · Undo") || proto.has("Woke up 14:32 · Undo") || proto.has("Woke up 14:32"))
        assertTrue(proto.contains("Woke up at 14:32. Tap the tile to undo. Feed."))
        // Without a confirmation Row A says the time since the last feed.
        assertTrue(build(WearSamples.tileIdle(), sizeDp = 192).has("2h 10m ago"))
    }

    @Test
    fun titlePriority() {
        val asleep = WearSamples.tileAsleep()
        assertTrue(asleep.title.let { it is TileTitle.Glance && it.title is GlanceTitle.Asleep })
        val loggedWhileAsleep = asleep.copy(feedback = WearSamples.tileJustLogged().feedback?.copy(untilMillis = asleep.now + 5_000))
        assertTrue(loggedWhileAsleep.title is TileTitle.Logged)
        assertTrue(WearSamples.tileIdle().title.let { it is TileTitle.Glance && it.title is GlanceTitle.Name })
        assertTrue(WearSamples.tileIdle(babyName = "  ").title.let { it is TileTitle.Glance && it.title is GlanceTitle.LastDiaper })
    }

    @Test
    fun oldRenderersGetStaticTextAndAMinuteRefresh() {
        assertFalse(VersionInfo.Builder().setMajor(1).setMinor(100).build().supportsDynamicText())
        assertTrue(VersionInfo.Builder().setMajor(1).setMinor(200).build().supportsDynamicText())
    }
}
