package com.ffmathy.xaternity.phone.ui.edit

import com.ffmathy.xaternity.core.domain.localDate
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.clockDurationMillis
import com.ffmathy.xaternity.core.model.clockMinutesBetween
import com.ffmathy.xaternity.core.model.isBreastfeed
import com.ffmathy.xaternity.core.model.isSleep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The edit sheet's rules (DESIGN_SPEC §3.3.4, §4.4; V1_ADDITIONS §1, §4). Pure Kotlin, unit-tested without Android.
 */
internal object EditRules {
    const val MINUTE_MILLIS = 60_000L

    /** A new interval in "Add past entry" lasts this long by default (never past now). */
    const val DEFAULT_INTERVAL_MILLIS = 15 * MINUTE_MILLIS

    /** The minute containing [millis]. */
    fun floorToMinute(millis: Long): Long = Math.floorDiv(millis, MINUTE_MILLIS) * MINUTE_MILLIS

    /** Whether two instants show the same clock minute (a change within a minute shows no "was"). */
    fun sameMinute(a: Long, b: Long): Boolean = Math.floorDiv(a, MINUTE_MILLIS) == Math.floorDiv(b, MINUTE_MILLIS)

    /**
     * A new draft of [kind] starting at [start]: intervals end 15 min later (not after now), a feed takes [feedType],
     * a diaper starts with both toggles off (Save waits for one, as in the Diaper sheet).
     */
    fun newDraft(id: String, kind: EntryKind, start: Long, now: Long, feedType: FeedType): EditDraft {
        val end = defaultEnd(start, now)
        return when (kind) {
            EntryKind.SLEEP -> EditDraft(id, EntryKind.SLEEP, start, end)
            EntryKind.FEED -> EditDraft(id, EntryKind.FEED, start, if (feedType == FeedType.BREAST) end else null, feedType)
            EntryKind.DIAPER -> EditDraft(id, EntryKind.DIAPER, start)
        }
    }

    fun defaultEnd(start: Long, now: Long): Long = maxOf(start, minOf(start + DEFAULT_INTERVAL_MILLIS, floorToMinute(now)))

    /**
     * Changes a feed's type, keeping its start. A breastfeed gets back the original's end and side when [original]
     * was one, otherwise a default end and no side (Save waits for one); formula and food drop end and side.
     */
    fun withFeedType(draft: EditDraft, type: FeedType, original: Entry?, now: Long): EditDraft = when {
        draft.kind != EntryKind.FEED || draft.feedType == type -> draft
        type == FeedType.BREAST && original?.isBreastfeed == true ->
            draft.copy(feedType = type, end = original.endMillis, side = original.breastSide)
        type == FeedType.BREAST -> draft.copy(feedType = type, end = defaultEnd(draft.start, now), side = null)
        else -> draft.copy(feedType = type, end = null, side = null)
    }

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
     * Save; offers "Use yesterday"); an overlap with another interval of the same kind (a warning). Null when none.
     */
    fun validate(draft: EditDraft, entries: List<Entry>, now: Long): EditIssue? {
        val end = draft.end
        if (draft.isInterval && end != null && end <= draft.start) return EditIssue.EndBeforeStart(draft.kind)
        if (draft.start > now) return EditIssue.InFuture(TimeField.START, draft.start)
        if (end != null && end > now) return EditIssue.InFuture(TimeField.END, end)
        if (draft.isInterval) overlap(draft, entries, now)?.let { return EditIssue.Overlaps(it) }
        return null
    }

    /** The latest other sleep (for a sleep) or breastfeed (for a breastfeed) whose interval meets the draft's. */
    fun overlap(draft: EditDraft, entries: List<Entry>, now: Long): Entry? {
        val sameKind: (Entry) -> Boolean = if (draft.kind == EntryKind.SLEEP) { e -> e.isSleep } else { e -> e.isBreastfeed }
        val from = draft.start
        val to = draft.end ?: now
        return entries
            .filter { it.id != draft.id && !it.deleted && sameKind(it) }
            .filter { other -> other.startMillis < to && from < (other.endMillis ?: now) }
            .maxByOrNull { it.startMillis }
    }

    /** Whether everything required is set: a diaper has pee or poop; an ended breastfeed has a side. */
    fun isComplete(draft: EditDraft): Boolean = when {
        draft.kind == EntryKind.DIAPER -> draft.pee || draft.poop
        draft.isBreastfeed && draft.end != null -> draft.side != null
        else -> true
    }

    /** True when saving [draft] would not change [original] (Save then just closes). */
    fun unchanged(draft: EditDraft, original: Entry): Boolean = draft == EditDraft.of(original)

    /** A rule that blocks Save comes first, then a refused save, then a warning. */
    private fun issue(validation: EditIssue?, saveFailed: Boolean): EditIssue? = when {
        validation?.blocksSave == true -> validation
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
            issue = issue(validate(draft, entries, now), saveFailed),
            complete = isComplete(draft),
        )
    }
}
