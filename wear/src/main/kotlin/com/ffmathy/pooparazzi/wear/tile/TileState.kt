package com.ffmathy.pooparazzi.wear.tile

import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.wear.glance.GlanceTitle
import com.ffmathy.pooparazzi.wear.glance.WatchGlance
import com.ffmathy.pooparazzi.wear.state.GuardKind
import java.time.ZoneId

/** How long the "just logged" title shows after a tile log (DESIGN_SPEC §5.12: 10 s timeline entry). */
const val TILE_FEEDBACK_MILLIS = 10_000L

/** How long the guard title shows after a rejected toggle (DESIGN_SPEC §5.12). */
const val TILE_GUARD_MILLIS = 2_000L

/** What a tile log confirms in the title for [TILE_FEEDBACK_MILLIS]. */
enum class TileFeedbackKind {
    /** A sleep ended from the tile: "Woke up 14:40 · Undo". */
    WOKE_UP,

    /** A sleep stopped under a minute was discarded: "Sleep cancelled · Undo" (Undo resumes it, DESIGN_SPEC §4.4). */
    SLEEP_CANCELLED,
}

/**
 * The confirmation of the last tile log. While it shows, the tile background opens Logged for [entryId] with Undo
 * active at once (DESIGN_SPEC §3.6, §10.7).
 *
 * @property atMillis the logged time shown in the title ("Woke up 14:32").
 * @property untilMillis when the title goes back to its normal content.
 * @property babyId the baby the log was for (null: unknown, from before several babies). The confirmation belongs to
 *   that baby and is dropped once the watch switches to another.
 */
data class TileFeedback(
    val kind: TileFeedbackKind,
    val entryId: String,
    val atMillis: Long,
    val untilMillis: Long,
    val babyId: String? = null,
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

    /** The last tile log (10 s): "Woke up 14:32 · Undo". Wins over the glance's "Asleep" (§3.6). */
    data class Logged(val feedback: TileFeedback) : TileTitle

    /** What Main's title line shows too ([WatchGlance.title]): asleep, the baby's name, or the last diaper. */
    data class Glance(val title: GlanceTitle) : TileTitle
}

/**
 * Everything the tile shows at [now] (DESIGN_SPEC §3.6, V1_ADDITIONS §2.2): the [glance] it shares with the app's Main,
 * plus the tile's own feedback and guard. Domain values only; the layout formats them with the shared formatters.
 */
data class TileUiState(
    val glance: WatchGlance,
    val feedback: TileFeedback? = null,
    val guard: TileGuard? = null,
) {
    val now: Long get() = glance.now
    val zone: ZoneId get() = glance.zone
    val lastFeed: Entry? get() = glance.lastFeed
    val lastDiaper: Entry? get() = glance.lastDiaper
    val runningSleep: Entry? get() = glance.runningSleep

    /** Guard, then the just-logged confirmation, then the glance's own title (asleep, the name, the last diaper). */
    val title: TileTitle
        get() = when {
            guard != null && guard.untilMillis > now -> TileTitle.Guard(guard.kind)
            feedback != null && feedback.untilMillis > now -> TileTitle.Logged(feedback)
            else -> TileTitle.Glance(glance.title)
        }

    /** The background opens Logged (with Undo) for this entry, while its confirmation shows. */
    val undoEntryId: String?
        get() = (title as? TileTitle.Logged)?.feedback?.takeIf { it.undoable }?.entryId

    /**
     * The same tile at [time]: the timeline entry that starts then. Feedback and guard expire on their own; every
     * duration on the tile is a dynamic expression, so the entries differ only in their title.
     */
    fun at(time: Long): TileUiState = copy(glance = glance.copy(now = time))

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
            }
        }

        /**
         * The tile for [summary] (the current baby's entries). [babyId] is the current baby: a confirmation of a log
         * for another baby (made before a switch) is dropped.
         */
        fun from(summary: Summary, babyName: String, feedback: TileFeedback?, guard: TileGuard?, babyId: String? = null) = TileUiState(
            glance = WatchGlance.from(summary, babyName),
            feedback = feedback?.takeIf { it.untilMillis > summary.now && it.forBaby(babyId) && it.stillStands(summary) },
            guard = guard?.takeIf { it.untilMillis > summary.now },
        )

        private fun TileFeedback.forBaby(current: String?): Boolean = babyId == null || current == null || babyId == current

        /**
         * What the idle title names (BABIES_AND_BACKUP §2.5): the baby's name with one baby (empty when unnamed, so
         * the title falls back to the last diaper as before); with two or more, the current baby's label, named or not
         * ("Noa", "Baby 2").
         */
        fun titleName(settings: Settings, labels: LabelFormatter): String =
            if (settings.hasSeveralBabies) labels.currentBaby(settings) else settings.babyName
    }
}
