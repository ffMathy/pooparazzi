package com.ffmathy.xaternity.wear.sample

import com.ffmathy.xaternity.core.domain.summarize
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.sync.SyncStatus
import com.ffmathy.xaternity.wear.state.AdjustUiState
import com.ffmathy.xaternity.wear.state.DiaperDraft
import com.ffmathy.xaternity.wear.state.FeedDraft
import com.ffmathy.xaternity.wear.state.MainUiState
import com.ffmathy.xaternity.wear.state.Receipt
import com.ffmathy.xaternity.wear.state.ReceiptKind
import com.ffmathy.xaternity.wear.state.SideDraft
import com.ffmathy.xaternity.wear.state.TimeField
import com.ffmathy.xaternity.wear.state.TodayUiState
import com.ffmathy.xaternity.wear.state.Undo

/**
 * Screen states for the reference scenario (V1_ADDITIONS §6) at its snapshots: 14:32 idle, 03:12 asleep,
 * 14:50 breastfeeding. Used by previews and screenshot tests.
 */
object WearSamples {
    val zone = SampleData.ZONE

    private val synced1431 = SyncStatus.Synced(SampleData.SYNCED_1431)
    private val synced0311 = SyncStatus.Synced(SampleData.SYNCED_0311)

    fun mainIdle(): MainUiState =
        MainUiState.from(summarize(SampleData.scenario(), SampleData.NOW_1432, zone), guard = null, sync = synced1431)

    fun mainAsleep(): MainUiState =
        MainUiState.from(summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, zone), guard = null, sync = synced0311)

    /** Breastfeeding since 14:32, seen at 14:50 ("Breastfeeding 18m"). */
    fun mainFeeding(): MainUiState =
        MainUiState.from(summarize(SampleData.scenarioWithRunningFeed(), SampleData.NOW_1450, zone), guard = null, sync = synced1431)

    fun feedDraft(): FeedDraft = FeedDraft(stamp = SampleData.NOW_1432, zone = zone)

    fun sideDraft(): SideDraft = SideDraft(SampleData.runningBreastfeed(), endAt = SampleData.NOW_1450, zone = zone)

    /** Pee + Poop on, Colour Yellow, Texture Seedy, stamped 14:32. */
    fun diaperDraft(): DiaperDraft = DiaperDraft(
        stamp = SampleData.NOW_1432,
        zone = zone,
        pee = true,
        poop = true,
        color = PoopColor.YELLOW,
        texture = PoopTexture.SEEDY,
    )

    /** "Feed ended" / "Left → Right · 18m" after the 14:32 breastfeed ends at 14:50. */
    fun receiptFeedEnded(): Receipt {
        val running = SampleData.runningBreastfeed()
        val ended = running.copy(endMillis = SampleData.NOW_1450, breastSide = BreastSide.LEFT_THEN_RIGHT)
        return Receipt(ReceiptKind.FEED_ENDED, ended, Undo.Restore(running), zone)
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

    /** Today at 14:32, newest first. */
    fun today(): TodayUiState {
        val summary = summarize(SampleData.scenario(), SampleData.NOW_1432, zone)
        return TodayUiState(summary.now, zone, summary.todayDate, summary.dayEntries(summary.todayDate))
    }
}
