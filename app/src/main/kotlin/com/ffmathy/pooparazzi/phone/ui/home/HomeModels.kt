package com.ffmathy.pooparazzi.phone.ui.home

import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture

/**
 * The view model's own record of the undo card. [HomeStateBuilder] turns it into [UndoCardUi] with live values
 * (durations, chip availability) from the current entries.
 *
 * @property baseTime what the offsets are relative to (never cumulative, DESIGN_SPEC §3.3.1).
 * @property offsetMinutes the selected offset (0 = none).
 * @property duringSleep the log happened while a sleep ran: row 2 offers "End sleep" while it still runs.
 * @property paused the Adjust time sheet is open over the card: its window waits until the sheet closes.
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
    val paused: Boolean = false,
)

/** What the card is about. Entry ids point at the entry that the offsets move. */
internal sealed interface CardSpec {
    /** [type] null: a feed logged with just its time ("Feed logged"). [side]: a breast feed's ticked order, if any. */
    data class FeedLogged(val entryId: String, val type: FeedType?, val side: BreastSide? = null) : CardSpec
    data class DiaperLogged(val entryId: String, val kind: DiaperKind) : CardSpec
    data class SleepStarted(val entryId: String) : CardSpec
    data class WokeUp(val entryId: String) : CardSpec
    data object SleepCancelled : CardSpec
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
        is CardSpec.SleepStarted -> entryId to MoveField.START
        is CardSpec.WokeUp -> entryId to MoveField.END
        CardSpec.SleepCancelled, is CardSpec.Notice, is CardSpec.Deleted, CardSpec.Undone -> null
    }

/** A feed or diaper log may offer "End sleep" while a sleep runs. */
internal val CardSpec.canEndSleep: Boolean
    get() = this is CardSpec.FeedLogged || this is CardSpec.DiaperLogged

/** How Undo reverts the card's log. */
internal sealed interface Undo {
    /** Feed, diaper, sleep start: remove the entry. */
    data class Delete(val entryId: String) : Undo

    /** Woke up, Sleep cancelled: write the running snapshot back (the sleep runs again). */
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

    /**
     * The Feed sheet. [breast] is true once Breast was tapped: the sheet then shows the side checkboxes and "Log feed";
     * [ticks] are the breasts ticked so far, in tick order (V1_ADDITIONS §2.1).
     */
    data class Feed(
        override val stamp: Long,
        override val offsetMinutes: Int = 0,
        val breast: Boolean = false,
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

    /**
     * The Adjust time sheet: moves [field] of [entryId] to [stamp] (its time when the sheet opened, or the card's
     * base time) + offset, on Save. [cardKey] is the undo card it was opened from (paused while it is open), or null
     * for "Adjust start" on the Lamp.
     */
    data class Adjust(
        override val stamp: Long,
        override val offsetMinutes: Int = 0,
        val entryId: String,
        val field: MoveField,
        val cardKey: Long? = null,
    ) : SheetModel
}

/** Timing constants (DESIGN_SPEC §5.12). */
data class HomeTimings(
    /** Undo window; restarted when the Adjust time sheet closes. The app passes the accessibility-recommended value. */
    val undoWindowMillis: Long = 10_000L,
    val undoneMillis: Long = 2_000L,
    /** Guard and dedupe lines. */
    val messageMillis: Long = 2_000L,
    /** Double-toggle guard after Start sleep / Woke up. */
    val toggleGuardMillis: Long = 2_000L,
    /** Feed, Diaper and the Sleep toggle ignore taps this long after a committed sheet finished closing. */
    val homeCooldownMillis: Long = 600L,
)
