package com.ffmathy.pooparazzi.phone.ui.edit

import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.clockMinutesBetween
import com.ffmathy.pooparazzi.core.model.isBreastfeed
import com.ffmathy.pooparazzi.core.model.isSleep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The edit sheet's rules (DESIGN_SPEC §3.3.4, §4.4; V1_ADDITIONS §1, §4). Pure Kotlin, unit-tested without Android.
 */
internal object EditRules {
    const val MINUTE_MILLIS = 60_000L

    /** A new sleep in "Add past entry" lasts this long by default (never past now). */
    const val DEFAULT_INTERVAL_MILLIS = 15 * MINUTE_MILLIS

    /** The minute containing [millis]. */
    fun floorToMinute(millis: Long): Long = Math.floorDiv(millis, MINUTE_MILLIS) * MINUTE_MILLIS

    /** Whether two instants show the same clock minute (a change within a minute shows no "was"). */
    fun sameMinute(a: Long, b: Long): Boolean = Math.floorDiv(a, MINUTE_MILLIS) == Math.floorDiv(b, MINUTE_MILLIS)

    /**
     * A new draft of [kind] starting at [start]: a sleep ends 15 min later (not after now), a feed (an instant) takes
     * [feedType] (null: no type), a diaper starts with both toggles off (a plain change unless one is ticked).
     */
    fun newDraft(id: String, kind: EntryKind, start: Long, now: Long, feedType: FeedType?): EditDraft {
        val end = defaultEnd(start, now)
        return when (kind) {
            EntryKind.SLEEP -> EditDraft(id, EntryKind.SLEEP, start, end)
            EntryKind.FEED -> EditDraft(id, EntryKind.FEED, start, null, feedType)
            EntryKind.DIAPER -> EditDraft(id, EntryKind.DIAPER, start)
        }
    }

    fun defaultEnd(start: Long, now: Long): Long = maxOf(start, minOf(start + DEFAULT_INTERVAL_MILLIS, floorToMinute(now)))

    /**
     * Changes a feed's type, keeping its time. A breastfeed gets back the original's side when [original] was one,
     * otherwise no side; a formula feed gets back the original's amount when [original] was formula, otherwise none.
     * Other types drop the side and the amount. Feeds never have an end.
     */
    fun withFeedType(draft: EditDraft, type: FeedType?, original: Entry?): EditDraft {
        if (draft.kind != EntryKind.FEED || draft.feedType == type) return draft
        val side = if (type == FeedType.BREAST && original?.isBreastfeed == true) original.breastSide else null
        val amount = if (type == FeedType.FORMULA && original?.feedType == FeedType.FORMULA) original.amountMl else null
        return draft.copy(feedType = type, end = null, side = side, amountMl = amount)
    }

    /** Sets a formula feed's amount ([amountMl] null or not positive: none); other drafts are unchanged. */
    fun withAmount(draft: EditDraft, amountMl: Int?): EditDraft =
        if (draft.isFormula) draft.copy(amountMl = amountMl?.takeIf { it > 0 }) else draft

    /** Moves [field] by [minutes]; a running end does not move. */
    fun nudge(draft: EditDraft, field: TimeField, minutes: Int): EditDraft = when (field) {
        TimeField.START -> draft.copy(start = draft.start + minutes * MINUTE_MILLIS)
        TimeField.END -> draft.end?.let { draft.copy(end = it + minutes * MINUTE_MILLIS) } ?: draft
    }

    /** Sets [field] to [hour]:[minute] on the day it already falls on. */
    fun setTime(draft: EditDraft, field: TimeField, hour: Int, minute: Int, zone: ZoneId): EditDraft {
        val current = valueOf(draft, field) ?: return draft
        val day = localDate(current, zone)
        return set(draft, field, day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli())
    }

    /** Moves [field] to [date], keeping its clock time. */
    fun setDay(draft: EditDraft, field: TimeField, date: LocalDate, zone: ZoneId): EditDraft {
        val current = valueOf(draft, field) ?: return draft
        val time = Instant.ofEpochMilli(current).atZone(zone).toLocalTime()
        return set(draft, field, date.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }

    fun valueOf(draft: EditDraft, field: TimeField): Long? = when (field) {
        TimeField.START -> draft.start
        TimeField.END -> draft.end
    }

    private fun set(draft: EditDraft, field: TimeField, value: Long): EditDraft = when (field) {
        TimeField.START -> draft.copy(start = value)
        TimeField.END -> if (draft.isInterval) draft.copy(end = value) else draft
    }

    /**
     * The first rule [draft] breaks, in order: an end not after its start (blocks Save); a time in the future (blocks
     * Save; offers "Use yesterday"); an overlap with another sleep (a warning). Null when none.
     */
    fun validate(draft: EditDraft, entries: List<Entry>, now: Long): EditIssue? {
        val end = draft.end
        if (draft.isInterval && end != null && end <= draft.start) return EditIssue.EndBeforeStart(draft.kind)
        if (draft.start > now) return EditIssue.InFuture(TimeField.START, draft.start)
        if (end != null && end > now) return EditIssue.InFuture(TimeField.END, end)
        if (draft.isInterval) overlap(draft, entries, now)?.let { return EditIssue.Overlaps(it) }
        return null
    }

    /** The latest other sleep whose interval meets the draft sleep's. */
    fun overlap(draft: EditDraft, entries: List<Entry>, now: Long): Entry? {
        if (!draft.isInterval) return null
        val from = draft.start
        val to = draft.end ?: now
        return entries
            .filter { it.id != draft.id && !it.deleted && it.isSleep }
            .filter { other -> other.startMillis < to && from < (other.endMillis ?: now) }
            .maxByOrNull { it.startMillis }
    }

    /** The feed type after tapping [type] in the type selector: the selected type again clears it (no type). */
    fun tappedFeedType(draft: EditDraft, type: FeedType): FeedType? = type.takeUnless { draft.feedType == it }

    /** Ticks or unticks [breast] on a breastfeed; the side is the tick order (V1_ADDITIONS §2.1). */
    fun withSideTick(draft: EditDraft, breast: Breast): EditDraft =
        if (draft.isBreastfeed) {
            draft.copy(side = BreastSide.fromOrder(BreastSide.toggle(draft.side?.order.orEmpty(), breast)))
        } else {
            draft
        }

    /** True when saving [draft] would not change [original] (Save then just closes). */
    fun unchanged(draft: EditDraft, original: Entry): Boolean = draft == EditDraft.of(original)

    /**
     * [draft] (made from [original]) moved onto [stored], the entry as it is now after a change on the watch: the
     * fields the parent changed in the sheet keep the sheet's value, every other field takes the stored one. So a
     * corrected start survives a "Woke up" from the watch, and the watch's side is not wiped.
     */
    fun rebase(draft: EditDraft, original: Entry, stored: Entry): EditDraft {
        val base = EditDraft.of(original)
        val theirs = EditDraft.of(stored)
        fun <T> pick(mine: T, was: T, other: T): T = if (mine != was) mine else other
        val merged = EditDraft(
            id = draft.id,
            kind = draft.kind,
            start = pick(draft.start, base.start, theirs.start),
            end = pick(draft.end, base.end, theirs.end),
            feedType = pick(draft.feedType, base.feedType, theirs.feedType),
            side = pick(draft.side, base.side, theirs.side),
            pee = pick(draft.pee, base.pee, theirs.pee),
            poop = pick(draft.poop, base.poop, theirs.poop),
            color = pick(draft.color, base.color, theirs.color),
            texture = pick(draft.texture, base.texture, theirs.texture),
            amountMl = pick(draft.amountMl, base.amountMl, theirs.amountMl),
        )
        return when {
            merged.kind == EntryKind.DIAPER ->
                if (merged.poop) {
                    merged.copy(end = null, side = null, amountMl = null)
                } else {
                    merged.copy(end = null, side = null, color = null, texture = null, amountMl = null)
                }
            merged.isInterval -> merged.copy(side = null, amountMl = null)
            merged.isBreastfeed -> merged.copy(end = null, amountMl = null)
            merged.isFormula -> merged.copy(end = null, side = null)
            else -> merged.copy(end = null, side = null, amountMl = null)
        }
    }

    /** A deleted entry comes first, then a rule that blocks Save, a change on the watch, a refused save, a warning. */
    private fun issue(validation: EditIssue?, saveFailed: Boolean, conflict: EditConflict?): EditIssue? = when {
        conflict == EditConflict.DELETED -> EditIssue.ChangedElsewhere(deleted = true)
        validation?.blocksSave == true -> validation
        conflict == EditConflict.CHANGED -> EditIssue.ChangedElsewhere(deleted = false)
        saveFailed -> EditIssue.SaveFailed
        else -> validation
    }

    /** Builds the sheet's state. */
    fun state(
        key: Long,
        mode: EditMode,
        draft: EditDraft,
        original: Entry?,
        saveFailed: Boolean,
        entries: List<Entry>,
        now: Long,
        zone: ZoneId,
        conflict: EditConflict? = null,
    ): EditUiState {
        val wasStart = original?.startMillis?.takeUnless { sameMinute(it, draft.start) }
        val originalEnd = original?.endMillis
        val wasEnd = originalEnd?.takeIf { draft.end != null && !sameMinute(it, draft.end) }
        val duration = if (draft.isInterval) clockDurationMillis(draft.start, draft.end ?: now) else null
        val originalDuration = original?.takeIf { it.endMillis != null && draft.isInterval && it.kind == draft.kind }
            ?.let { clockMinutesBetween(it.startMillis, it.endMillis!!) * MINUTE_MILLIS }
        return EditUiState(
            key = key,
            mode = mode,
            now = now,
            zone = zone,
            today = localDate(now, zone),
            draft = draft,
            original = original,
            wasStart = wasStart,
            wasEnd = wasEnd,
            durationMillis = duration,
            wasDurationMillis = originalDuration?.takeIf { draft.end != null && it != duration },
            issue = issue(validate(draft, entries, now), saveFailed, conflict),
        )
    }
}
