package com.ffmathy.xaternity.wear.state

import androidx.compose.runtime.Immutable
import com.ffmathy.xaternity.core.domain.Summary
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import com.ffmathy.xaternity.core.model.isInterval
import com.ffmathy.xaternity.core.sync.SyncStatus
import java.time.LocalDate
import java.time.ZoneId

/** What Main leads with (DESIGN_SPEC §3.5.1–2, V1_ADDITIONS §2.2). A running breastfeed wins over a running sleep. */
enum class MainMode { IDLE, ASLEEP, FEEDING }

/** The guard word shown after a rejected toggle (DESIGN_SPEC §3.5.2, §4.4). */
enum class GuardKind { ALREADY_ASLEEP, ALREADY_AWAKE, ALREADY_FEEDING }

/** Which control shows the guard: the sleep toggle (EdgeButton or list button) or "End feed". */
enum class GuardTarget { SLEEP_TOGGLE, END_FEED }

/** A visible guard: shown on [target] until [untilMillis] (2 s, DESIGN_SPEC §5.12). */
@Immutable
data class Guard(val kind: GuardKind, val target: GuardTarget, val untilMillis: Long)

/**
 * Main (W1/W2 and the running-feed variant). Domain values only: the screen formats them with the shared
 * formatters, so the ViewModel stays free of resources.
 *
 * @property now minute-resolution "now" used for every duration on the screen.
 * @property lastSleep the most recent completed sleep ("Awake … since 13:27").
 */
@Immutable
data class MainUiState(
    val now: Long,
    val zone: ZoneId,
    val loaded: Boolean,
    val lastFeed: Entry?,
    val lastDiaper: Entry?,
    val runningSleep: Entry?,
    val runningFeed: Entry?,
    val lastSleep: Entry?,
    val guard: Guard?,
    val sync: SyncStatus,
) {
    val mode: MainMode
        get() = when {
            runningFeed != null -> MainMode.FEEDING
            runningSleep != null -> MainMode.ASLEEP
            else -> MainMode.IDLE
        }

    /** The guard on the sleep toggle, if one is showing. */
    val sleepGuard: GuardKind? get() = guard?.takeIf { it.target == GuardTarget.SLEEP_TOGGLE }?.kind

    /** The guard on "End feed", if one is showing. */
    val endFeedGuard: GuardKind? get() = guard?.takeIf { it.target == GuardTarget.END_FEED }?.kind

    companion object {
        fun from(summary: Summary, guard: Guard?, sync: SyncStatus) = MainUiState(
            now = summary.now,
            zone = summary.zone,
            loaded = true,
            lastFeed = summary.lastFeed,
            lastDiaper = summary.lastDiaper,
            runningSleep = summary.runningSleep,
            runningFeed = summary.runningFeed,
            lastSleep = summary.lastSleep,
            guard = guard?.takeIf { it.untilMillis > summary.now },
            sync = sync,
        )

        /** Before the first database read. */
        fun loading(now: Long, zone: ZoneId) = MainUiState(
            now = now, zone = zone, loaded = false, lastFeed = null, lastDiaper = null, runningSleep = null,
            runningFeed = null, lastSleep = null, guard = null, sync = SyncStatus.Unavailable,
        )
    }
}

/** Earlier offsets cycle 0 → −5 → −10 → −15 → 0 (V1_ADDITIONS §2.2). Offsets are never cumulative. */
object EarlierOffsets {
    val CYCLE: List<Int> = listOf(0, -5, -10, -15)

    fun next(current: Int): Int = CYCLE[(CYCLE.indexOf(current).coerceAtLeast(0) + 1) % CYCLE.size]
}

private const val MINUTE_MILLIS = 60_000L

/** The Feed type screen: the time stamped on the Feed tap, plus an Earlier offset. */
@Immutable
data class FeedDraft(val stamp: Long, val offsetMinutes: Int = 0, val zone: ZoneId) {
    /** The time the feed is logged at. */
    val at: Long get() = stamp + offsetMinutes * MINUTE_MILLIS
}

/** The Side screen: the running breastfeed and the end time stamped on the "End feed" tap. */
@Immutable
data class SideDraft(val feed: Entry, val endAt: Long, val zone: ZoneId)

/** The Diaper screen (V1_ADDITIONS §3.2). Both checkboxes start off; Save needs one. */
@Immutable
data class DiaperDraft(
    val stamp: Long,
    val zone: ZoneId,
    val offsetMinutes: Int = 0,
    val pee: Boolean = false,
    val poop: Boolean = false,
    val color: PoopColor? = null,
    val texture: PoopTexture? = null,
) {
    val at: Long get() = stamp + offsetMinutes * MINUTE_MILLIS
    val canSave: Boolean get() = pee || poop
}

/** What a Logged confirmation is for (DESIGN_SPEC §3.5.6, V1_ADDITIONS §2.2, §3.2). */
enum class ReceiptKind { FEED_LOGGED, DIAPER_LOGGED, FEED_ENDED, FEED_CANCELLED, WOKE_UP, SLEEP_CANCELLED }

/** How Undo reverts a receipt. */
sealed interface Undo {
    /** A new entry: soft-delete it. */
    data class Delete(val id: String) : Undo

    /** An ended or discarded interval: write the running snapshot back. */
    data class Restore(val previous: Entry) : Undo
}

/**
 * A Logged confirmation with its Undo.
 *
 * @property entry the entry as written (or the existing one for a [duplicate]).
 * @property duplicate the log matched an entry within 5 s: "Already logged" (DESIGN_SPEC §4.4).
 * @property fromTile opened from the Tile's undo path: Undo and dismiss are active at once (§10.7).
 * @property serial distinguishes two otherwise equal receipts, so the 5 s window restarts on a repeat.
 */
@Immutable
data class Receipt(
    val kind: ReceiptKind,
    val entry: Entry,
    val undo: Undo,
    val zone: ZoneId,
    val duplicate: Boolean = false,
    val fromTile: Boolean = false,
    val serial: Long = 0,
)

/** The time of an entry that Adjust can shift. */
enum class TimeField { START, END }

/** One Adjust choice: [minutes] from the base, landing at [at]. */
@Immutable
data class AdjustOption(val minutes: Int, val at: Long, val enabled: Boolean)

/** One group of Adjust choices for a [field] whose current value is [base]. */
@Immutable
data class AdjustGroup(val field: TimeField, val base: Long, val options: List<AdjustOption>)

/**
 * Adjust (DESIGN_SPEC §3.5.5): −15m / −5m / +5m for one or two times of [entry]. Options that would put a time in
 * the future or an end before its start are disabled.
 */
@Immutable
data class AdjustUiState(
    val entry: Entry,
    val now: Long,
    val zone: ZoneId,
    val groups: List<AdjustGroup>,
    val allowSetTime: Boolean,
) {
    companion object {
        /** The offsets of §3.5.5, in order. */
        val OFFSETS: List<Int> = listOf(-15, -5, 5)

        fun build(entry: Entry, fields: List<TimeField>, allowSetTime: Boolean, now: Long, zone: ZoneId): AdjustUiState {
            val groups = fields.mapNotNull { field ->
                val base = entry.timeOf(field) ?: return@mapNotNull null
                AdjustGroup(
                    field = field,
                    base = base,
                    options = OFFSETS.map { minutes ->
                        val at = base + minutes * MINUTE_MILLIS
                        AdjustOption(minutes, at, enabled = entry.withTime(field, at, now) != null)
                    },
                )
            }
            return AdjustUiState(entry, now, zone, groups, allowSetTime)
        }
    }
}

/** The 24 h "Set time" picker for one time of an entry. */
@Immutable
data class SetTimeUiState(val entry: Entry, val field: TimeField, val base: Long, val zone: ZoneId)

/** What Adjust is opened for. */
@Immutable
data class AdjustRequest(val entryId: String, val fields: List<TimeField>, val allowSetTime: Boolean)

/** Today (W8): today's entries, newest first. */
@Immutable
data class TodayUiState(val now: Long, val zone: ZoneId, val day: LocalDate, val entries: List<Entry>, val loaded: Boolean = true)

/** The value of [field], or null when the entry has no such time (an instant's end, a running end). */
fun Entry.timeOf(field: TimeField): Long? = when (field) {
    TimeField.START -> startMillis
    TimeField.END -> endMillis
}

/**
 * This entry with [field] moved to [at], or null when that is not allowed: in the future (after [now]), an end
 * before its start, a start after its end, or an end on an instant.
 */
fun Entry.withTime(field: TimeField, at: Long, now: Long): Entry? {
    if (at > now) return null
    return when (field) {
        TimeField.START -> {
            val end = endMillis
            if (end != null && at > end) null else copy(startMillis = at)
        }
        TimeField.END -> {
            if (!isInterval || endMillis == null || at < startMillis) null else copy(endMillis = at)
        }
    }
}

/** The Adjust fields a Today row offers: start and end of a completed interval, otherwise the start (the time). */
fun Entry.adjustableFields(): List<TimeField> =
    if (isInterval && endMillis != null) listOf(TimeField.START, TimeField.END) else listOf(TimeField.START)

/** Haptics the app plays (DESIGN_SPEC §5.11), mapped onto Compose `HapticFeedbackType` by the UI. */
enum class XHaptic { CONFIRM, REJECT, TOGGLE_ON, TOGGLE_OFF, SEGMENT_TICK }

/** One-shot effects from the ViewModel: haptics and navigation that depend on a write's outcome. */
sealed interface WearEvent {
    data class Haptic(val type: XHaptic) : WearEvent

    /** Show (or refresh) the Logged confirmation for the current receipt. */
    data object ShowLogged : WearEvent

    /** Undo succeeded: show "Undone", then Main. */
    data object ShowUndone : WearEvent

    /** "End feed" was accepted: pick the side. */
    data object ShowSide : WearEvent

    /** Return to Main (closing pickers). */
    data object BackToMain : WearEvent

    /** Return [count] screens (Adjust → its caller; Set time → Adjust's caller). */
    data class Back(val count: Int = 1) : WearEvent
}
