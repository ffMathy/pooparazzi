package com.ffmathy.pooparazzi.wear.state

import androidx.compose.runtime.Immutable
import com.ffmathy.pooparazzi.core.domain.Expected
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.forecast
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import java.time.ZoneId

/** What Main leads with (DESIGN_SPEC §3.5.1–2, V1_ADDITIONS §2.2). Only a sleep runs: feeds are instants. */
enum class MainMode { IDLE, ASLEEP }

/** The guard word shown after a rejected toggle (DESIGN_SPEC §3.5.2, §4.4). */
enum class GuardKind { ALREADY_ASLEEP, ALREADY_AWAKE }

/** Which control shows the guard: the sleep toggle (the EdgeButton). */
enum class GuardTarget { SLEEP_TOGGLE }

/** A visible guard: shown on [target] until [untilMillis] (2 s, DESIGN_SPEC §5.12). */
@Immutable
data class Guard(val kind: GuardKind, val target: GuardTarget, val untilMillis: Long)

/**
 * Main (W1/W2). Domain values only: the screen formats them with the shared
 * formatters, so the ViewModel stays free of resources.
 *
 * @property now minute-resolution "now" used for every duration on the screen.
 * @property lastSleep the most recent completed sleep ("Awake … since 13:27").
 * @property babies the babies, in order (from the phone's settings); the screens label them (`LabelFormatter.baby`).
 * @property babyId the watch's current baby, the one the entries above are for and every tap logs for.
 */
@Immutable
data class MainUiState(
    val now: Long,
    val zone: ZoneId,
    val loaded: Boolean,
    val lastFeed: Entry?,
    val lastDiaper: Entry?,
    val runningSleep: Entry?,
    val lastSleep: Entry?,
    val guard: Guard?,
    val sync: SyncStatus,
    val babies: List<Baby> = listOf(Baby(Baby.FIRST_ID)),
    val babyId: String = Baby.FIRST_ID,
    /** The expected wake-up while asleep, else the expected next sleep (V1_ADDITIONS §9); null when unpredictable. */
    val sleepForecast: Expected? = null,
    /** The expected next feed (V1_ADDITIONS §9); null when unpredictable. */
    val feedForecast: Expected? = null,
) {
    val mode: MainMode
        get() = if (runningSleep != null) MainMode.ASLEEP else MainMode.IDLE

    /** The guard on the sleep toggle, if one is showing. */
    val sleepGuard: GuardKind? get() = guard?.takeIf { it.target == GuardTarget.SLEEP_TOGGLE }?.kind

    /** The current baby: the one with [babyId], else the first (as `Settings.selectedBaby`). */
    val baby: Baby get() = babies.firstOrNull { it.id == babyId } ?: babies.firstOrNull() ?: Baby(Baby.FIRST_ID)

    /**
     * Two or more babies: every screen names the current one in its time text and Main offers the baby picker
     * (BABIES_AND_BACKUP §2.5). With one baby the watch looks exactly as before.
     */
    val hasSeveralBabies: Boolean get() = babies.size > 1

    companion object {
        fun from(summary: Summary, guard: Guard?, sync: SyncStatus, babies: List<Baby> = listOf(Baby(Baby.FIRST_ID)), babyId: String = Baby.FIRST_ID) = MainUiState(
            now = summary.now,
            zone = summary.zone,
            loaded = true,
            lastFeed = summary.lastFeed,
            lastDiaper = summary.lastDiaper,
            runningSleep = summary.runningSleep,
            lastSleep = summary.lastSleep,
            guard = guard?.takeIf { it.untilMillis > summary.now },
            sync = sync,
            babies = babies,
            babyId = babyId,
        ).let { state -> forecast(summary).let { state.copy(sleepForecast = it.sleep, feedForecast = it.feed) } }

        /** Before the first database read. */
        fun loading(now: Long, zone: ZoneId) = MainUiState(
            now = now, zone = zone, loaded = false, lastFeed = null, lastDiaper = null, runningSleep = null,
            lastSleep = null, guard = null, sync = SyncStatus.Unavailable,
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

/**
 * The Side screen after Breast on the Feed type screen: the time the breast feed is logged at ([at], the Feed tap's
 * stamp plus its Earlier offset) and the Left / Right checkboxes ticked so far, in tick order ([side] is what that
 * order records; null = no side).
 */
@Immutable
data class SideDraft(val at: Long, val zone: ZoneId, val ticks: List<Breast> = emptyList()) {
    val side: BreastSide? get() = BreastSide.fromOrder(ticks)
}

/** The Diaper screen (V1_ADDITIONS §3.2). Both checkboxes start off and are optional: nothing ticked is a plain change. */
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
}

/** What a Logged confirmation is for (DESIGN_SPEC §3.5.6, V1_ADDITIONS §2.2, §3.2). */
enum class ReceiptKind { FEED_LOGGED, DIAPER_LOGGED, WOKE_UP, SLEEP_CANCELLED }

/** How Undo reverts a receipt. */
sealed interface Undo {
    /** A new entry: soft-delete it. */
    data class Delete(val id: String) : Undo

    /** An ended or discarded sleep: write the running snapshot back. */
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

/** Haptics the app plays (DESIGN_SPEC §5.11), mapped onto Compose `HapticFeedbackType` by the UI. */
enum class XHaptic { CONFIRM, REJECT, TOGGLE_ON, TOGGLE_OFF, SEGMENT_TICK }

/** One-shot effects from the ViewModel: haptics and navigation that depend on a write's outcome. */
sealed interface WearEvent {
    data class Haptic(val type: XHaptic) : WearEvent

    /** Show (or refresh) the Logged confirmation for the current receipt. */
    data object ShowLogged : WearEvent

    /** Undo succeeded: show "Undone", then Main. */
    data object ShowUndone : WearEvent

    /** Breast was chosen on the Feed type screen: pick the side order, then Save. */
    data object ShowSide : WearEvent

    /** Return to Main (closing pickers). */
    data object BackToMain : WearEvent
}
