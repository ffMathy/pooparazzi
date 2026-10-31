package com.ffmathy.pooparazzi.wear.sample

import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.wear.state.AdjustUiState
import com.ffmathy.pooparazzi.wear.state.DiaperDraft
import com.ffmathy.pooparazzi.wear.state.FeedDraft
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.MainUiState
import com.ffmathy.pooparazzi.wear.state.Receipt
import com.ffmathy.pooparazzi.wear.state.ReceiptKind
import com.ffmathy.pooparazzi.wear.state.SideDraft
import com.ffmathy.pooparazzi.wear.state.TimeField
import com.ffmathy.pooparazzi.wear.state.TodayUiState
import com.ffmathy.pooparazzi.wear.state.Undo
import com.ffmathy.pooparazzi.wear.tile.TILE_FEEDBACK_MILLIS
import com.ffmathy.pooparazzi.wear.tile.TILE_GUARD_MILLIS
import com.ffmathy.pooparazzi.wear.tile.TileFeedback
import com.ffmathy.pooparazzi.wear.tile.TileFeedbackKind
import com.ffmathy.pooparazzi.wear.tile.TileGuard
import com.ffmathy.pooparazzi.wear.tile.TileUiState

/**
 * Screen states for the reference scenario (V1_ADDITIONS §6) at its snapshots: 14:32 idle, 03:12 asleep,
 * 14:50 after the 14:32 breast feed. Main builds on the weeks of [SampleData.history] before them, so it has a sleep
 * forecast (V1_ADDITIONS §9). Used by previews and screenshot tests.
 */
object WearSamples {
    val zone = SampleData.ZONE

    private val synced1431 = SyncStatus.Synced(SampleData.SYNCED_1431)
    private val synced0311 = SyncStatus.Synced(SampleData.SYNCED_0311)

    fun mainIdle(): MainUiState =
        MainUiState.from(summarize(SampleData.history(), SampleData.NOW_1432, zone), guard = null, sync = synced1431)

    /** [mainIdle] at 15:55 with nothing logged since: "Next feed" "24m ago · ~15:30", "Next sleep 36m ago". */
    fun mainForecastPassed(): MainUiState =
        MainUiState.from(summarize(SampleData.history(), SampleData.at(15, 55), zone), guard = null, sync = synced1431)

    fun mainAsleep(): MainUiState =
        MainUiState.from(summarize(SampleData.historyAt0312(), SampleData.NOW_0312, zone), guard = null, sync = synced0311)

    /** 14:50, 18 minutes after the 14:32 breast feed (Left → Right): idle, "Fed at 14:32 · L → R". */
    fun mainAfterBreastfeed(): MainUiState =
        MainUiState.from(summarize(SampleData.history() + SampleData.recentBreastfeed(), SampleData.NOW_1450, zone), guard = null, sync = synced1431)

    /** Twins (Noa, the current baby, and Ida) at 14:32: "Noa · 14:32" and the baby button below the hero. */
    fun mainIdleTwins(): MainUiState = mainIdle().copy(babies = SampleData.twinSettings.babies, babyId = Baby.FIRST_ID)

    /** Twins with Ida current (her log is empty in the samples): "Ida · 14:32", "No feeds yet". */
    fun mainIdleTwinsIda(): MainUiState =
        MainUiState.from(summarize(emptyList(), SampleData.NOW_1432, zone), guard = null, sync = synced1431)
            .copy(babies = SampleData.twinSettings.babies, babyId = SampleData.TWIN_ID)

    fun feedDraft(): FeedDraft = FeedDraft(stamp = SampleData.NOW_1432, zone = zone)

    /** Breast chosen on the Feed type screen opened at 14:32: Side, nothing ticked yet. */
    fun sideDraft(): SideDraft = SideDraft(at = SampleData.NOW_1432, zone = zone)

    /** Pee + Poop on, Colour Yellow, Texture Seedy, stamped 14:32. */
    fun diaperDraft(): DiaperDraft = DiaperDraft(
        stamp = SampleData.NOW_1432,
        zone = zone,
        pee = true,
        poop = true,
        color = PoopColor.YELLOW,
        texture = PoopTexture.SEEDY,
    )

    /** "Breast at 14:32" / "Left → Right": the breast feed saved on the Side screen. */
    fun receiptBreast(): Receipt {
        val entry = SampleData.recentBreastfeed()
        return Receipt(ReceiptKind.FEED_LOGGED, entry, Undo.Delete(entry.id), zone)
    }

    /** "Formula at 14:32". */
    fun receiptFormula(): Receipt {
        val entry = Entry.feed(FeedType.FORMULA, SampleData.NOW_1432, id = "sample-formula-1432")
        return Receipt(ReceiptKind.FEED_LOGGED, entry, Undo.Delete(entry.id), zone)
    }

    /** "Pee + poop at 14:32". */
    fun receiptDiaper(): Receipt {
        val entry = Entry.diaper(SampleData.NOW_1432, pee = true, poop = true, PoopColor.YELLOW, PoopTexture.SEEDY, id = "sample-both-1432")
        return Receipt(ReceiptKind.DIAPER_LOGGED, entry, Undo.Delete(entry.id), zone)
    }

    val syncAt1431: SyncStatus get() = synced1431

    /** Adjust start of the sleep running since 02:05, at 03:12: "−15m · 01:50", "−5m · 02:00", "+5m · 02:10". */
    fun adjustRunningSleep(): AdjustUiState {
        val sleep = SampleData.scenarioAt0312().first { it.id == "sample-sleep-0205" }
        return AdjustUiState.build(sleep, listOf(TimeField.START), allowSetTime = false, now = SampleData.NOW_0312, zone = zone)
    }

    // ---- Tile (DESIGN_SPEC §3.6) ------------------------------------------------------------------

    /** Idle at 14:32 with the baby's name in the title. */
    fun tileIdle(babyName: String = SampleData.BABY_NAME): TileUiState =
        TileUiState.from(summarize(SampleData.scenario(), SampleData.NOW_1432, zone), babyName, feedback = null, guard = null)

    /** Asleep at 03:12: "Asleep 1h 07m", Woke up on the edge. */
    fun tileAsleep(): TileUiState =
        TileUiState.from(summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, zone), SampleData.BABY_NAME, feedback = null, guard = null)

    /** 14:50, after the 14:32 breast feed: Row A is "Feed · 18m ago" with nothing running. */
    fun tileAfterBreastfeed(): TileUiState =
        TileUiState.from(summarize(SampleData.scenarioWithRecentFeed(), SampleData.NOW_1450, zone), SampleData.BABY_NAME, feedback = null, guard = null)

    /** Woke up on the tile at 14:32 (a nap since 13:50): "Woke up 14:32 · Undo" for 10 s. */
    fun tileJustLogged(): TileUiState {
        val nap = Entry.sleep(SampleData.at(13, 50), SampleData.NOW_1432, id = "sample-nap-1350")
        val summary = summarize(SampleData.scenario() + nap, SampleData.NOW_1432, zone)
        val feedback = TileFeedback(TileFeedbackKind.WOKE_UP, nap.id, SampleData.NOW_1432, SampleData.NOW_1432 + TILE_FEEDBACK_MILLIS)
        return TileUiState.from(summary, SampleData.BABY_NAME, feedback, guard = null)
    }

    /** A Start sleep tap within 2 s of the last toggle at 03:12: "Already asleep". */
    fun tileGuard(): TileUiState =
        tileAsleep().copy(guard = TileGuard(GuardKind.ALREADY_ASLEEP, SampleData.NOW_0312 + TILE_GUARD_MILLIS))

    /** Today at 14:32, newest first. */
    fun today(): TodayUiState {
        val summary = summarize(SampleData.scenario(), SampleData.NOW_1432, zone)
        return TodayUiState(summary.now, zone, summary.todayDate, summary.dayEntries(summary.todayDate))
    }
}
