package com.ffmathy.xaternity.phone.ui.edit

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

/** Editing an existing entry, or adding a past one (DESIGN_SPEC §3.3.4 "Add past entry"). */
enum class EditMode { EDIT, ADD }

/** Which time of the draft a control changes: the start (or the instant's time) or the end of an interval. */
enum class TimeField { START, END }

/**
 * The form's values. An interval ([isInterval]) whose [end] is null is running ("Still asleep" / "Still feeding").
 * Converted to an [Entry] with [toEntry]; the repository enforces the invariants again on save.
 */
@Serializable
data class EditDraft(
    val id: String,
    val kind: EntryKind,
    val start: Long,
    val end: Long? = null,
    val feedType: FeedType? = null,
    val side: BreastSide? = null,
    val pee: Boolean = false,
    val poop: Boolean = false,
    val color: PoopColor? = null,
    val texture: PoopTexture? = null,
) {
    val isBreastfeed: Boolean get() = kind == EntryKind.FEED && feedType == FeedType.BREAST
    val isInterval: Boolean get() = kind == EntryKind.SLEEP || isBreastfeed
    val running: Boolean get() = isInterval && end == null

    /** The entry this draft describes (instants drop any end; colour and texture only with poop). */
    fun toEntry(updatedAtMillis: Long): Entry = when (kind) {
        EntryKind.SLEEP -> Entry.sleep(start, end, id = id, updatedAtMillis = updatedAtMillis)
        EntryKind.FEED -> if (isBreastfeed) {
            Entry.breastfeed(start, end, side.takeIf { end != null }, id = id, updatedAtMillis = updatedAtMillis)
        } else {
            Entry.feed(feedType ?: FeedType.FORMULA, start, id = id, updatedAtMillis = updatedAtMillis)
        }
        EntryKind.DIAPER -> Entry.diaper(start, pee, poop, color, texture, id = id, updatedAtMillis = updatedAtMillis)
    }

    companion object {
        fun of(entry: Entry): EditDraft = EditDraft(
            id = entry.id,
            kind = entry.kind,
            start = entry.startMillis,
            end = entry.endMillis,
            feedType = entry.feedType,
            side = entry.breastSide,
            pee = entry.pee,
            poop = entry.poop,
            color = entry.poopColor,
            texture = entry.poopTexture,
        )
    }
}

/** A rule the draft breaks (DESIGN_SPEC §3.3.4 validation slot). Only blocking issues disable Save. */
@Immutable
sealed interface EditIssue {
    val blocksSave: Boolean

    /** "Woke up must be after fell asleep." / "The feed must end after it starts." */
    data class EndBeforeStart(val kind: EntryKind) : EditIssue {
        override val blocksSave: Boolean get() = true
    }

    /** "13:50 hasn't happened yet. Log it for yesterday?" with "Use yesterday". */
    data class InFuture(val field: TimeField, val at: Long) : EditIssue {
        override val blocksSave: Boolean get() = true
    }

    /** "Overlaps the 12:10 sleep. Save anyway?" (a warning: Save stays enabled). */
    data class Overlaps(val other: Entry) : EditIssue {
        override val blocksSave: Boolean get() = false
    }

    /** The last save was refused (e.g. another sleep started on the watch meanwhile). */
    data object SaveFailed : EditIssue {
        override val blocksSave: Boolean get() = false
    }

    /**
     * The entry changed on the watch while the sheet was open, so Save did not overwrite it. The sheet now shows the
     * watch's version with this sheet's changes on top ("Changed on the watch. Check and save again."); [deleted]: it
     * was deleted there and can no longer be saved.
     */
    data class ChangedElsewhere(val deleted: Boolean) : EditIssue {
        override val blocksSave: Boolean get() = deleted
    }
}

/** How the stored entry moved away from the one the sheet opened with (see [EditIssue.ChangedElsewhere]). */
@Serializable
enum class EditConflict { CHANGED, DELETED }

/**
 * Everything the edit sheet shows (DESIGN_SPEC §3.3.4, V1_ADDITIONS §4), as data.
 *
 * @property key identifies one opening of the sheet.
 * @property original the stored entry when editing ("was 12:40", "was 47m"); null when adding.
 * @property wasStart the original start, shown as "was 12:40" once the start's minute changed.
 * @property wasEnd the original end, shown once the end's minute changed.
 * @property durationMillis the interval's displayed duration (to now while running); null for instants.
 * @property wasDurationMillis the original duration, shown once the duration changed.
 * @property complete everything required is set: a diaper has pee or poop; an ended breastfeed has a side.
 */
@Immutable
data class EditUiState(
    val key: Long,
    val mode: EditMode,
    val now: Long,
    val zone: ZoneId,
    val today: LocalDate,
    val draft: EditDraft,
    val original: Entry?,
    val wasStart: Long?,
    val wasEnd: Long?,
    val durationMillis: Long?,
    val wasDurationMillis: Long?,
    val issue: EditIssue?,
    val complete: Boolean,
) {
    val canSave: Boolean get() = complete && issue?.blocksSave != true
}

/** Everything the edit sheet can ask for. Defaults do nothing, so previews and screenshots can pass [None]. */
@Stable
interface EditActions {
    /** Close, Cancel, a scrim tap, back or a swipe: the sheet closes without saving. */
    fun onDismiss() {}
    fun onSave() {}
    fun onDelete() {}

    /** The sheet finished its closing animation. */
    fun onClosed() {}

    /** "Add past entry": Sleep | Feed | Diaper. */
    fun onKind(kind: EntryKind) {}
    fun onFeedType(type: FeedType) {}
    fun onSide(side: BreastSide) {}

    /** ±1m / ±5m. */
    fun onNudge(field: TimeField, minutes: Int) {}

    /** The time picker's result: [hour]:[minute] on the field's current day. */
    fun onTimeSet(field: TimeField, hour: Int, minute: Int) {}

    /** The day chip: Today, Yesterday or an earlier date, keeping the clock time. */
    fun onDaySet(field: TimeField, date: LocalDate) {}

    /** "Use yesterday" in the future-time message. */
    fun onUseYesterday() {}

    /** "End now" on a running sleep or breastfeed. */
    fun onEndNow() {}
    fun onPee(checked: Boolean) {}
    fun onPoop(checked: Boolean) {}
    fun onPoopColor(color: PoopColor?) {}
    fun onPoopTexture(texture: PoopTexture?) {}

    object None : EditActions
}

/** What the sheet reports to the screen that opened it. */
sealed interface EditEvent {
    data class Saved(val entry: Entry) : EditEvent

    /** Deleted from the sheet: the screen offers Undo with [previous] (Home: a card; History: a snackbar). */
    data class Deleted(val previous: Entry) : EditEvent
}
