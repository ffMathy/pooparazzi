package com.ffmathy.xaternity.wear.tile

import com.ffmathy.xaternity.core.domain.Summary
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.wear.state.GuardKind
import java.time.ZoneId

/** How long the "just logged" title shows after a tile log (DESIGN_SPEC §5.12: 10 s timeline entry). */
const val TILE_FEEDBACK_MILLIS = 10_000L

/** How long the guard title shows after a rejected toggle (DESIGN_SPEC §5.12). */
const val TILE_GUARD_MILLIS = 2_000L

/** What a tile log confirms in the title for [TILE_FEEDBACK_MILLIS]. */
enum class TileFeedbackKind {
    /** A diaper (Pee / Poop / Both): "Pee 14:32 · Undo". */
    DIAPER,

    /** A sleep ended from the tile: "Woke up 14:40 · Undo". */
    WOKE_UP,

    /** A sleep stopped under a minute was discarded: "Sleep cancelled · Undo" (Undo resumes it, DESIGN_SPEC §4.4). */
    SLEEP_CANCELLED,
}

/**
 * The confirmation of the last tile log. While it shows, the tile background opens Logged for [entryId] with Undo
 * active at once (DESIGN_SPEC §3.6, §10.7).
 *
 * @property atMillis the logged time shown in the title ("Pee 14:32").
 * @property untilMillis when the title goes back to its normal content.
 */
data class TileFeedback(
    val kind: TileFeedbackKind,
    val entryId: String,
    val atMillis: Long,
    val diaperKind: DiaperKind?,
    val untilMillis: Long,
) {
    /**
     * Whether the background tap can open Logged with Undo for this log: always, including "Sleep cancelled"
     * (DESIGN_SPEC §4.4: "with Undo, on phone and watch"), whose Undo resumes the discarded sleep.
     */
    val undoable: Boolean get() = true
}

/** A rejected tile toggle: "Already asleep" / "Already awake" in the title until [untilMillis]. */
data class TileGuard(val kind: GuardKind, val untilMillis: Long)

/** The title slot (DESIGN_SPEC §3.6). Exactly one shows, in the priority order of [TileUiState.title]. */
sealed interface TileTitle {
    /** A rejected toggle (2 s). */
    data class Guard(val kind: GuardKind) : TileTitle

    /** The last tile log (10 s): "Pee 14:32 · Undo". Wins over [Asleep] (§3.6). */
    data class Logged(val feedback: TileFeedback) : TileTitle

    /** A sleep runs: "Asleep 1h 07m". */
    data class Asleep(val sleep: Entry) : TileTitle

    /** The baby's name, synced from the phone's settings. */
    data class Name(val name: String) : TileTitle

    /** No name set: the last diaper, kind first ("Pee 48m ago"), as the approved mockup shows. */
    data class LastDiaper(val diaper: Entry) : TileTitle

    /** Nothing to say yet (no name, no diapers). */
    data object None : TileTitle
}

/**
 * Everything the tile layout shows at [now] (DESIGN_SPEC §3.6, V1_ADDITIONS §2.2). Domain values only; the layout
 * formats them with the shared formatters.
 */
data class TileUiState(
    val now: Long,
    val zone: ZoneId,
    val babyName: String,
    val lastFeed: Entry?,
    val lastDiaper: Entry?,
    val runningSleep: Entry?,
    val runningFeed: Entry?,
    val feedback: TileFeedback? = null,
    val guard: TileGuard? = null,
) {
    /** Guard, then the just-logged confirmation, then asleep, then the name, then the last diaper. */
    val title: TileTitle
        get() = when {
            guard != null && guard.untilMillis > now -> TileTitle.Guard(guard.kind)
            feedback != null && feedback.untilMillis > now -> TileTitle.Logged(feedback)
            runningSleep != null -> TileTitle.Asleep(runningSleep)
            babyName.isNotBlank() -> TileTitle.Name(babyName)
            lastDiaper != null -> TileTitle.LastDiaper(lastDiaper)
            else -> TileTitle.None
        }

    /** The background opens Logged (with Undo) for this entry, while its confirmation shows. */
    val undoEntryId: String?
        get() = (title as? TileTitle.Logged)?.feedback?.takeIf { it.undoable }?.entryId

    /**
     * The same tile at [time]: the timeline entry that starts then. Feedback and guard expire on their own; every
     * duration on the tile is a dynamic expression, so the entries differ only in their title.
     */
    fun at(time: Long): TileUiState = copy(now = time)

    /** The moments the title changes after [now] (feedback and guard expiry), in order. */
    val changes: List<Long>
        get() = listOfNotNull(feedback?.untilMillis, guard?.untilMillis).filter { it > now }.distinct().sorted()

    companion object {
        /**
         * A confirmation is dropped as soon as what it confirms is gone (undone in the app, or deleted on the phone),
         * so the title never offers Undo for nothing.
         */
        private fun TileFeedback.stillStands(summary: Summary): Boolean {
            val entry = summary.entries.firstOrNull { it.id == entryId }
            return when (kind) {
                // The discarded sleep is soft-deleted, so it is missing from the entries until Undo restores it.
                TileFeedbackKind.SLEEP_CANCELLED -> entry == null
                TileFeedbackKind.WOKE_UP -> entry?.endMillis != null
                TileFeedbackKind.DIAPER -> entry != null
            }
        }

        fun from(summary: Summary, babyName: String, feedback: TileFeedback?, guard: TileGuard?) = TileUiState(
            now = summary.now,
            zone = summary.zone,
            babyName = babyName.trim(),
            lastFeed = summary.lastFeed,
            lastDiaper = summary.lastDiaper,
            runningSleep = summary.runningSleep,
            runningFeed = summary.runningFeed,
            feedback = feedback?.takeIf { it.untilMillis > summary.now && it.stillStands(summary) },
            guard = guard?.takeIf { it.untilMillis > summary.now },
        )
    }
}
