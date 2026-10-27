package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.runtime.Immutable
import com.ffmathy.pooparazzi.core.domain.DayCounts
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import java.time.ZoneId

/**
 * Everything Home shows (DESIGN_SPEC §3.3.1, V1_ADDITIONS §2.1), as data. Text is formatted by the screen with the
 * current locale's resources, so the same state renders in English and Danish.
 *
 * @property now the minute the screen describes (epoch ms).
 * @property babyName empty until the parent sets one (the screen then shows the "Baby" hint).
 * @property row1 since-row 1: "Awake …" when idle, "Last fed …" while a sleep runs, "Asleep …" while a breastfeed runs
 *   during a sleep (each fact appears once per screen).
 * @property row2 since-row 2: the last diaper, kind first.
 * @property card the undo card in the whisper slot; null shows the Today grid.
 * @property sheet the open logging sheet, if any.
 */
@Immutable
data class HomeUiState(
    val now: Long,
    val zone: ZoneId,
    val babyName: String,
    val sync: SyncStatus,
    val lamp: LampState,
    val row1: SinceRow?,
    val row2: SinceRow?,
    val today: DayCounts,
    val runningSleep: Entry?,
    val runningFeed: Entry?,
    val card: UndoCardUi? = null,
    val sheet: HomeSheetUi? = null,
) {
    val sleepRunning: Boolean get() = runningSleep != null
    val feedRunning: Boolean get() = runningFeed != null

    /** Something runs: at night this is lights-down (DESIGN_SPEC §8.2). */
    val anythingRunning: Boolean get() = sleepRunning || feedRunning
}

/** A since-row (DESIGN_SPEC §3.3.1). Tapping it edits [entryId], the latest entry of that row. */
@Immutable
sealed interface SinceRow {
    val entryId: String

    /** "Awake 1h 05m · since 13:27". */
    data class Awake(val sleep: Entry, val sinceMillis: Long, val elapsedMillis: Long) : SinceRow {
        override val entryId: String get() = sleep.id
    }

    /** "Asleep 1h 07m · since 02:05": shown only while a breastfeed holds the Lamp. */
    data class Asleep(val sleep: Entry, val elapsedMillis: Long) : SinceRow {
        override val entryId: String get() = sleep.id
    }

    /** "Last fed 1h 32m ago · 01:40". */
    data class LastFed(val feed: Entry, val elapsedMillis: Long) : SinceRow {
        override val entryId: String get() = feed.id
    }

    /** "Pee 48m ago · 13:44" (kind first; never colour or texture). */
    data class LastDiaper(val diaper: Entry, val kind: DiaperKind, val elapsedMillis: Long) : SinceRow {
        override val entryId: String get() = diaper.id
    }
}

/** An offset chip ("−5m", "+5m", or "Now" for 0). */
@Immutable
data class OffsetChipUi(val minutes: Int, val selected: Boolean, val enabled: Boolean = true)

/**
 * The undo card (DESIGN_SPEC §3.3.1). Titles are time-free; the effective time shows once, in the Lamp or a since-row.
 *
 * @property key identity of the card: a new log gets a new key (and fades in).
 * @property windowKey changes whenever the 10 s window restarts (chip taps, dedupe), restarting the drain line.
 * @property timeoutMillis the window length the drain line shows.
 */
@Immutable
data class UndoCardUi(
    val key: Long,
    val windowKey: Int,
    val timeoutMillis: Long,
    val variant: CardVariant,
    val action: CardAction,
    val row: CardRow,
)

/** The end-of-row button: "Undo", "Done" (Adjust start) or nothing ("Undone", notices). */
enum class CardAction { UNDO, DONE, NONE }

/** What the card says in row 1. */
@Immutable
sealed interface CardVariant {
    /** "Formula logged" / "Food logged"; "Feed logged" for a feed with no type (null). */
    data class FeedLogged(val type: FeedType?) : CardVariant

    /** "Pee logged" / "Poop logged" / "Pee + poop logged" / "Diaper logged". */
    data class DiaperLogged(val kind: DiaperKind) : CardVariant

    /** "Feed started" (a breastfeed). */
    data object FeedStarted : CardVariant

    /** "Feed ended" + "Left → Right · 18m" (just "18m" when no side was ticked). */
    data class FeedEnded(val side: BreastSide?, val durationMillis: Long) : CardVariant

    /** "Feed cancelled" + "Left · <1m": a breastfeed ended under a minute after its start was discarded. */
    data class FeedCancelled(val side: BreastSide?) : CardVariant

    /** "Sleep started". */
    data object SleepStarted : CardVariant

    /** "Woke up" + "Slept 1h 07m". */
    data class WokeUp(val sleptMillis: Long) : CardVariant

    /** "Sleep cancelled" + "Slept <1m". */
    data object SleepCancelled : CardVariant

    /** "Adjust start" of the running sleep or breastfeed (the button reads "Done"). */
    data class AdjustStart(val sleep: Boolean) : CardVariant

    /** A guard with no card to attach to: shows the current state ("Asleep", "Awake", "Breastfeeding"). */
    data class Notice(val guard: GuardKind) : CardVariant

    /** "Sleep deleted" / "Pee + poop deleted" (DESIGN_SPEC §3.3.4), with Undo. */
    data class Deleted(val entry: Entry) : CardVariant

    /** "Undone" (2 s, no drain line). */
    data object Undone : CardVariant
}

/** The guard lines (DESIGN_SPEC §4.4). */
enum class GuardKind { ASLEEP, AWAKE, FEEDING }

/** Row 2 of the card. */
@Immutable
sealed interface CardRow {
    data object None : CardRow

    /** "Earlier?" + "−5m" "−10m" "−15m". */
    data class Earlier(val chips: List<OffsetChipUi>) : CardRow

    /** A feed or diaper logged while a sleep runs: "End sleep" + "−5m" "−10m". */
    data class RunningSleep(val endSleepEnabled: Boolean, val chips: List<OffsetChipUi>) : CardRow

    /** "−15m" "−5m" "+5m" + the Edit chip for [editEntryId]. */
    data class Adjust(val chips: List<OffsetChipUi>, val editEntryId: String) : CardRow

    /** "Already asleep since 14:32" / "Already awake since 14:40" / "Already feeding since 14:32" (2 s). */
    data class Guard(val kind: GuardKind, val sinceMillis: Long) : CardRow

    /** "Already logged at 14:32" (2 s), with the icon of [entry]. */
    data class Dedupe(val entry: Entry) : CardRow
}

/** The open logging sheet. [stamp] is the moment the sheet was opened; the chosen time is stamp + offset. */
@Immutable
sealed interface HomeSheetUi {
    val stamp: Long
    val offsetMinutes: Int
    val chips: List<OffsetChipUi>
    val time: Long get() = stamp + offsetMinutes * MINUTE_MILLIS

    /** Feed sheet (V1_ADDITIONS §2.1): Breast / Formula / Food / Just the time. */
    data class Feed(override val stamp: Long, override val offsetMinutes: Int, override val chips: List<OffsetChipUi>) : HomeSheetUi

    /**
     * Side sheet: ends the running breastfeed [feedId] with the optional side [ticks] (Left / Right checkboxes, in the
     * order ticked; [side] is what that order records, null for none).
     */
    data class Side(
        val feedId: String,
        val startMillis: Long,
        override val stamp: Long,
        override val offsetMinutes: Int,
        override val chips: List<OffsetChipUi>,
        val ticks: List<Breast> = emptyList(),
    ) : HomeSheetUi {
        val side: BreastSide? get() = BreastSide.fromOrder(ticks)
    }

    /**
     * Diaper sheet (V1_ADDITIONS §3.1): optional Pee / Poop checkboxes, optional colour and texture. Save is always
     * allowed: nothing ticked logs a plain diaper change.
     */
    data class Diaper(
        override val stamp: Long,
        override val offsetMinutes: Int,
        override val chips: List<OffsetChipUi>,
        val pee: Boolean,
        val poop: Boolean,
        val color: PoopColor?,
        val texture: PoopTexture?,
    ) : HomeSheetUi
}

internal const val MINUTE_MILLIS = 60_000L
