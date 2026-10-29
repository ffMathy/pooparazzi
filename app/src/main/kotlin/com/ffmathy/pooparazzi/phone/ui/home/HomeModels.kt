package com.ffmathy.pooparazzi.phone.ui.home

import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture

/** The view model's record of the whisper slot's notice card (2 s): "Undone", or a guard with nothing else showing. */
internal data class CardModel(val key: Long, val spec: CardSpec)

internal sealed interface CardSpec {
    /** "Asleep" + "Already asleep since 14:32" / "Awake" + "Already awake since 14:40" (DESIGN_SPEC §4.4). */
    data class Guard(val kind: GuardKind, val sinceMillis: Long) : CardSpec
    data object Undone : CardSpec
}

/** What a receipt sheet confirms. Entry ids point at the entry that the offsets move. */
internal sealed interface ReceiptSpec {
    /**
     * [type] null: a feed logged with just its time ("Feed logged"). [side]: a breast feed's ticked order, if any;
     * [amountMl]: a formula feed's amount, if any.
     */
    data class FeedLogged(
        val entryId: String,
        val type: FeedType?,
        val side: BreastSide? = null,
        val amountMl: Int? = null,
    ) : ReceiptSpec
    data class DiaperLogged(val entryId: String, val kind: DiaperKind) : ReceiptSpec
    data class SleepStarted(val entryId: String) : ReceiptSpec
    data class WokeUp(val entryId: String) : ReceiptSpec
    data object SleepCancelled : ReceiptSpec

    /** "Sleep deleted" after a delete in the edit sheet; Undo writes [previous] back. */
    data class Deleted(val previous: Entry) : ReceiptSpec
}

/** Which time of the entry an offset chip moves. */
internal enum class MoveField { START, END }

/** The entry and field the receipt's offset chips move, or null when it has no chips. */
internal val ReceiptSpec.subject: Pair<String, MoveField>?
    get() = when (this) {
        is ReceiptSpec.FeedLogged -> entryId to MoveField.START
        is ReceiptSpec.DiaperLogged -> entryId to MoveField.START
        is ReceiptSpec.SleepStarted -> entryId to MoveField.START
        is ReceiptSpec.WokeUp -> entryId to MoveField.END
        ReceiptSpec.SleepCancelled, is ReceiptSpec.Deleted -> null
    }

/** A feed or diaper log may offer "End sleep" while a sleep runs. */
internal val ReceiptSpec.canEndSleep: Boolean
    get() = this is ReceiptSpec.FeedLogged || this is ReceiptSpec.DiaperLogged

/** How Undo reverts the receipt's log. */
internal sealed interface Undo {
    /** Feed, diaper, sleep start: remove the entry. */
    data class Delete(val entryId: String) : Undo

    /** Woke up, Sleep cancelled, a delete: write the previous snapshot back. */
    data class Restore(val previous: Entry) : Undo
}

/** The view model's record of the open sheet. */
internal sealed interface SheetModel {
    val stamp: Long
    val offsetMinutes: Int

    /**
     * The Feed sheet. [breast] is true once Breast was tapped: the sheet then shows the side checkboxes and "Log feed";
     * [ticks] are the breasts ticked so far, in tick order (V1_ADDITIONS §2.1). [formula] is true once Formula was
     * tapped: the sheet then shows the amount slider and "Log feed"; [amountMl] is the amount picked (null: none).
     */
    data class Feed(
        override val stamp: Long,
        override val offsetMinutes: Int = 0,
        val breast: Boolean = false,
        val ticks: List<Breast> = emptyList(),
        val formula: Boolean = false,
        val amountMl: Int? = null,
    ) : SheetModel

    data class Diaper(
        override val stamp: Long,
        override val offsetMinutes: Int = 0,
        val pee: Boolean = false,
        val poop: Boolean = false,
        val color: PoopColor? = null,
        val texture: PoopTexture? = null,
    ) : SheetModel

    /** "Adjust start" on the Lamp: moves the start of the running sleep [entryId] to [stamp] + offset, on Save. */
    data class Adjust(
        override val stamp: Long,
        override val offsetMinutes: Int = 0,
        val entryId: String,
    ) : SheetModel

    /**
     * The receipt sheet after a log (DESIGN_SPEC §3.3.1): what was logged, Undo, and offsets relative to [stamp] (the
     * base time, never cumulative) that apply at once. It has no timeout.
     *
     * @property key identity of the receipt: a new log gets a new key.
     * @property duringSleep the log happened while a sleep ran: the sheet offers "End sleep" while it still runs.
     * @property dedupe "Already logged at …" for 2 s, when a repeat log found [dedupe] instead.
     */
    data class Receipt(
        override val stamp: Long,
        override val offsetMinutes: Int = 0,
        val key: Long,
        val spec: ReceiptSpec,
        val duringSleep: Boolean = false,
        val undo: Undo? = null,
        val dedupe: Entry? = null,
    ) : SheetModel
}

/** Timing constants (DESIGN_SPEC §5.12). */
data class HomeTimings(
    /** "Undone" and guard notices in the whisper slot. */
    val noticeMillis: Long = 2_000L,
    /** The dedupe line in a receipt. */
    val messageMillis: Long = 2_000L,
    /** Double-toggle guard after Start sleep / Woke up. */
    val toggleGuardMillis: Long = 2_000L,
    /** Feed, Diaper and the Sleep toggle ignore taps this long after a committed sheet finished closing. */
    val homeCooldownMillis: Long = 600L,
)
