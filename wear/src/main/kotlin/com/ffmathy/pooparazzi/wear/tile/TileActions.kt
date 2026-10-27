package com.ffmathy.pooparazzi.wear.tile

import android.util.Log
import com.ffmathy.pooparazzi.core.data.EndResult
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.data.LogResult
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.AlreadyRunningException
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.XHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * The tile's one-tap actions (DESIGN_SPEC §10.9: `loadAction` clickables handled in `onTileRequest`). Feed and
 * End feed open the app instead (V1_ADDITIONS §2.2), so they are not here.
 *
 * The sleep toggle is two actions, so a tap says what the button showed: the tile may still show "Start sleep" a
 * few seconds after the phone started a sleep (or "Woke up" after it ended one), and the tap must then not flip the
 * state back (DESIGN_SPEC §4.4).
 */
enum class TileAction(val id: String) {
    LOG_PEE("log_pee"),
    LOG_POOP("log_poop"),
    LOG_BOTH("log_both"),

    /** "Start sleep" as shown: no sleep ran when the layout was built. */
    SLEEP_START("sleep_start"),

    /** "Woke up" as shown, for the sleep the layout showed (its id is the click id's target). */
    SLEEP_END("sleep_end"),
    ;

    /**
     * The clickable id for a layout built with [token]: "log_pee#12", or "sleep_end#12#<sleep id>" with a [target].
     * Each layout gets new ids (see [TileMemory.claim]).
     */
    fun clickId(token: Long, target: String? = null): String =
        id + SEPARATOR + token + (target?.let { SEPARATOR + it } ?: "")

    companion object {
        private const val SEPARATOR = "#"

        /** The action of a clickable id, or null for an empty id (a refresh, not a tap) or any other clickable. */
        fun fromClickId(clickId: String?): TileAction? {
            val id = clickId?.substringBefore(SEPARATOR)?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.id == id }
        }

        /** The entry a clickable id names after its token (the sleep behind "Woke up"), if any. */
        fun targetOf(clickId: String?): String? = clickId?.split(SEPARATOR)?.getOrNull(2)?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Performs tile taps against the repository. Writes are idempotent: a replayed click id is ignored
 * ([TileMemory.claim]), a repeat diaper within 5 s is the repository's duplicate, and a sleep toggle within 2 s of
 * any toggle shows the guard instead (DESIGN_SPEC §4.4, §10.7).
 */
class TileActions(
    private val repository: EntryRepository,
    private val memory: TileMemory,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /**
     * Handles the tap behind [clickId] at [now]. Returns the haptic to play, or null when nothing was done (no tap,
     * an unknown clickable or a replay).
     */
    suspend fun handle(clickId: String?, now: Long): XHaptic? {
        val action = TileAction.fromClickId(clickId) ?: return null
        if (!memory.claim(checkNotNull(clickId))) return null
        return lock.withLock {
            try {
                when (action) {
                    TileAction.LOG_PEE -> logDiaper(DiaperKind.PEE, now)
                    TileAction.LOG_POOP -> logDiaper(DiaperKind.POOP, now)
                    TileAction.LOG_BOTH -> logDiaper(DiaperKind.BOTH, now)
                    TileAction.SLEEP_START -> toggleSleep(shownSleepId = null, now)
                    TileAction.SLEEP_END -> toggleSleep(shownSleepId = TileAction.targetOf(clickId), now)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Tile action ${action.id} failed", e)
                XHaptic.REJECT
            }
        }
    }

    private suspend fun logDiaper(kind: DiaperKind, now: Long): XHaptic {
        val pee = kind != DiaperKind.POOP
        val poop = kind != DiaperKind.PEE
        // Colour and texture are added later in the app (V1_ADDITIONS §2.2).
        val result = repository.logDiaperChecked(now, pee, poop, color = null, texture = null)
        val entry = result.entry
        memory.feedback = TileFeedback(
            kind = TileFeedbackKind.DIAPER,
            entryId = entry.id,
            atMillis = entry.startMillis,
            diaperKind = entry.diaperKind ?: kind,
            untilMillis = now + TILE_FEEDBACK_MILLIS,
        )
        memory.guard = null
        return if (result is LogResult.Duplicate) XHaptic.REJECT else XHaptic.CONFIRM
    }

    /**
     * "Start sleep" ([shownSleepId] null) or "Woke up" for [shownSleepId], as the tapped layout showed it. When the
     * state changed since (the app or the phone started or ended a sleep), the tap shows "Already asleep" /
     * "Already awake" and writes nothing (DESIGN_SPEC §4.4: the first toggle wins).
     */
    private suspend fun toggleSleep(shownSleepId: String?, now: Long): XHaptic {
        val summary = summarize(repository.observeEntries().first(), now, zone)
        val running = summary.runningSleep
        // The double-toggle guard covers every surface: the tile's own last toggle, and any sleep written in the
        // last 2 s (the app's EdgeButton, or the phone through sync).
        val lastSleepWrite = summary.entries.filter { it.kind == EntryKind.SLEEP }.maxOfOrNull { it.updatedAtMillis }
        val recent = maxOf(memory.lastSleepToggleAt, lastSleepWrite ?: Long.MIN_VALUE)
        if (now - recent in 0 until TILE_GUARD_MILLIS) {
            return guard(if (running != null) GuardKind.ALREADY_ASLEEP else GuardKind.ALREADY_AWAKE, now)
        }
        if (shownSleepId == null && running != null) return guard(GuardKind.ALREADY_ASLEEP, now)
        if (shownSleepId != null && running?.id != shownSleepId) return guard(GuardKind.ALREADY_AWAKE, now)
        if (running == null) {
            val error = repository.startSleep(now).exceptionOrNull()
            return when {
                error == null -> {
                    memory.lastSleepToggleAt = now
                    memory.guard = null
                    XHaptic.TOGGLE_ON
                }
                error is AlreadyRunningException -> guard(GuardKind.ALREADY_ASLEEP, now)
                else -> throw error
            }
        }
        return when (val result = repository.endSleep(running.id, now)) {
            is EndResult.Ended -> {
                memory.lastSleepToggleAt = now
                memory.guard = null
                memory.feedback = TileFeedback(TileFeedbackKind.WOKE_UP, result.entry.id, now, null, now + TILE_FEEDBACK_MILLIS)
                XHaptic.TOGGLE_OFF
            }
            is EndResult.Discarded -> {
                memory.lastSleepToggleAt = now
                memory.guard = null
                memory.feedback = TileFeedback(TileFeedbackKind.SLEEP_CANCELLED, result.entry.id, now, null, now + TILE_FEEDBACK_MILLIS)
                XHaptic.TOGGLE_OFF
            }
            is EndResult.NotRunning -> guard(GuardKind.ALREADY_AWAKE, now)
        }
    }

    private fun guard(kind: GuardKind, now: Long): XHaptic {
        memory.guard = TileGuard(kind, now + TILE_GUARD_MILLIS)
        return XHaptic.REJECT
    }

    private companion object {
        const val TAG = "TileActions"

        /** One tap at a time across service instances, so a double tap sees the first tap's write. */
        val lock = Mutex()
    }
}
