package com.ffmathy.xaternity.phone.ui.home

import com.ffmathy.xaternity.core.model.Breast
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture

/**
 * The view model's own record of the undo card. [HomeStateBuilder] turns it into [UndoCardUi] with live values
 * (durations, chip availability) from the current entries.
 *
 * @property baseTime what the offsets are relative to (never cumulative, DESIGN_SPEC §3.3.1).
 * @property offsetMinutes the selected offset (0 = none).
 * @property duringSleep the log happened while a sleep ran: row 2 offers "End sleep" while it still runs.
 */
internal data class CardModel(
    val key: Long,
    val spec: CardSpec,
    val baseTime: Long,
    val offsetMinutes: Int = 0,
    val duringSleep: Boolean = false,
    val undo: Undo? = null,
    val message: CardMessage? = null,
    val windowKey: Int = 0,
)

/** What the card is about. Entry ids point at the entry that the offsets move. */
internal sealed interface CardSpec {
    /** [type] null: a feed logged with just its time ("Feed logged"). */
    data class FeedLogged(val entryId: String, val type: FeedType?) : CardSpec
    data class DiaperLogged(val entryId: String, val kind: DiaperKind) : CardSpec
    data class FeedStarted(val entryId: String) : CardSpec
    /** [side] null: the feed ended with no side ticked. */
    data class FeedEnded(val entryId: String, val side: BreastSide?) : CardSpec
    data class FeedCancelled(val side: BreastSide?) : CardSpec
    data class SleepStarted(val entryId: String) : CardSpec
    data class WokeUp(val entryId: String) : CardSpec
    data object SleepCancelled : CardSpec
    data class AdjustStart(val entryId: String, val sleep: Boolean) : CardSpec
    data class Notice(val guard: GuardKind) : CardSpec

    /** "Sleep deleted" after a delete in the edit sheet; Undo writes [previous] back. */
    data class Deleted(val previous: Entry) : CardSpec
    data object Undone : CardSpec
}

/** Which time of the entry an offset chip moves. */
internal enum class MoveField { START, END }

/** The entry and field the card's offset chips move, or null when the card has no chips. */
internal val CardSpec.subject: Pair<String, MoveField>?
    get() = when (this) {
        is CardSpec.FeedLogged -> entryId to MoveField.START
        is CardSpec.DiaperLogged -> entryId to MoveField.START
        is CardSpec.FeedStarted -> entryId to MoveField.START
        is CardSpec.SleepStarted -> entryId to MoveField.START
        is CardSpec.AdjustStart -> entryId to MoveField.START
        is CardSpec.FeedEnded -> entryId to MoveField.END
        is CardSpec.WokeUp -> entryId to MoveField.END
        is CardSpec.FeedCancelled, CardSpec.SleepCancelled, is CardSpec.Notice, is CardSpec.Deleted, CardSpec.Undone -> null
    }

/** A feed or diaper log (or a breastfeed start) may offer "End sleep" while a sleep runs. */
internal val CardSpec.canEndSleep: Boolean
    get() = this is CardSpec.FeedLogged || this is CardSpec.DiaperLogged || this is CardSpec.FeedStarted

/** How Undo reverts the card's log. */
internal sealed interface Undo {
    /** Feed, diaper, sleep start, breastfeed start: remove the entry. */
    data class Delete(val entryId: String) : Undo

    /** Woke up, Feed ended, cancelled: write the running snapshot back (the interval runs again). */
    data class Restore(val previous: Entry) : Undo
}

/** A 2 s message in row 2. */
internal sealed interface CardMessage {
    data class Guard(val kind: GuardKind, val sinceMillis: Long) : CardMessage
    data class Dedupe(val entry: Entry) : CardMessage
}

/** The view model's record of the open sheet. */
internal sealed interface SheetModel {
    val stamp: Long
    val offsetMinutes: Int

    data class Feed(override val stamp: Long, override val offsetMinutes: Int = 0) : SheetModel

    /** [ticks]: the breasts ticked so far, in tick order (V1_ADDITIONS §2.1). */
    data class Side(
        val feedId: String,
        val startMillis: Long,
        override val stamp: Long,
        override val offsetMinutes: Int = 0,
        val ticks: List<Breast> = emptyList(),
    ) : SheetModel

    data class Diaper(
        override val stamp: Long,
        override val offsetMinutes: Int = 0,
        val pee: Boolean = false,
        val poop: Boolean = false,
        val color: PoopColor? = null,
        val texture: PoopTexture? = null,
    ) : SheetModel
}

/** Timing constants (DESIGN_SPEC §5.12). */
data class HomeTimings(
    /** Undo window; restarted by each chip tap. The app passes the accessibility-recommended value. */
    val undoWindowMillis: Long = 10_000L,
    val undoneMillis: Long = 2_000L,
    /** Guard and dedupe lines. */
    val messageMillis: Long = 2_000L,
    /** Double-toggle guard after Start sleep / Woke up. */
    val toggleGuardMillis: Long = 2_000L,
    /** Feed, Diaper and the Sleep toggle ignore taps this long after a committed sheet finished closing. */
    val homeCooldownMillis: Long = 600L,
)
