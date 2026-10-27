package com.ffmathy.xaternity.phone.widget

import android.content.Context
import android.util.Log
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.data.Clock
import com.ffmathy.xaternity.core.data.EndResult
import com.ffmathy.xaternity.core.data.EntryRepository
import com.ffmathy.xaternity.core.data.LogResult
import com.ffmathy.xaternity.core.model.AlreadyRunningException
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.EntryJson
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.model.isSleep
import com.ffmathy.xaternity.phone.launch.ReceiptKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the widget buttons do without opening the app (DESIGN_SPEC §3.3.6, §10.7, §10.11): the one-tap diaper logs,
 * the sleep toggle and Undo of the last widget log. Writes go through the repository, so they are idempotent (dedupe,
 * one running sleep) and sync like any other log. Each log leaves a receipt with Undo in row 1 for
 * [RECEIPT_MILLIS]; guards, duplicates and "Undone" leave a [NOTICE_MILLIS] line instead.
 *
 * Feed and End feed are not here: they open the app on the Feed and Side sheets, which need a choice.
 * Commands run one at a time (two quick taps are applied in order).
 */
class WidgetCommands(
    private val repository: EntryRepository,
    private val store: WidgetMemoStore,
    private val clock: Clock,
) {
    /** Pee, Poop or Both, logged now with no colour or texture (they can be added later in the app). */
    suspend fun logDiaper(kind: DiaperKind) = lock.withLock {
        val now = clock.now()
        val result = attempt { repository.logDiaperChecked(at = now, pee = kind != DiaperKind.POOP, poop = kind != DiaperKind.PEE) }
            ?: return@withLock
        when (result) {
            is LogResult.Logged -> receipt(ReceiptKind.LOGGED, result.entry.id, result.entry.startMillis, now, diaperKind = kind)
            is LogResult.Duplicate -> notice(NoticeType.ALREADY_LOGGED, result.entry.startMillis, now, diaperKind = kind)
        }
    }

    /** Start sleep / Woke up, with the 2 s double-toggle guard ("Already asleep since 02:05"). */
    suspend fun toggleSleep() = lock.withLock {
        val now = clock.now()
        val entries = repository.observeEntries().first()
        val running = entries.firstOrNull { it.isSleep && it.isRunning }
        val last = store.memo.value.lastToggleAtMillis
        if (last != null && now - last in 0 until GUARD_MILLIS) {
            if (running != null) {
                notice(NoticeType.ALREADY_ASLEEP, running.startMillis, now)
            } else {
                val awakeSince = entries.filter { it.isSleep }.mapNotNull { it.endMillis }.maxOrNull() ?: now
                notice(NoticeType.ALREADY_AWAKE, awakeSince, now)
            }
            return@withLock
        }
        store.update { it.copy(lastToggleAtMillis = now) }
        if (running == null) {
            val started = attempt { repository.startSleep(now) } ?: return@withLock
            started
                .onSuccess { receipt(ReceiptKind.SLEEP_STARTED, it.id, it.startMillis, now) }
                .onFailure { e -> if (e is AlreadyRunningException) notice(NoticeType.ALREADY_ASLEEP, e.running.startMillis, now) }
        } else {
            when (val result = attempt { repository.endSleep(running.id, now) } ?: return@withLock) {
                is EndResult.Ended -> receipt(ReceiptKind.WOKE_UP, result.entry.id, result.entry.endMillis ?: now, now, previous = EntryJson.encode(result.previous))
                is EndResult.Discarded -> receipt(ReceiptKind.SLEEP_CANCELLED, result.entry.id, now, now, previous = EntryJson.encode(result.previous))
                is EndResult.NotRunning -> notice(NoticeType.ALREADY_AWAKE, result.current?.endMillis ?: now, now)
            }
        }
    }

    /**
     * Undo of the receipt for [entryId]: a diaper or sleep start is removed, a Woke up or Sleep cancelled brings the
     * sleep back. A stale Undo (expired, or replaced by a newer log) does nothing.
     */
    suspend fun undo(entryId: String) = lock.withLock {
        val now = clock.now()
        val receipt = store.memo.value.receipt?.takeIf { it.entryId == entryId && it.expiresAtMillis > now } ?: return@withLock
        try {
            when (receipt.kind) {
                ReceiptKind.LOGGED, ReceiptKind.SLEEP_STARTED -> repository.delete(entryId)
                ReceiptKind.WOKE_UP, ReceiptKind.SLEEP_CANCELLED -> {
                    val previous = receipt.previousJson?.let(EntryJson::decodeOrNull) ?: return@withLock
                    repository.restore(previous)
                }
            }
            store.update {
                it.copy(
                    receipt = null,
                    notice = WidgetNotice(NoticeType.UNDONE, now, now + NOTICE_MILLIS),
                    // Undo resets the toggle guard, as in the app.
                    lastToggleAtMillis = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AlreadyRunningException) {
            // Another sleep started meanwhile (e.g. on the watch): the old one cannot run again.
            notice(NoticeType.ALREADY_ASLEEP, e.running.startMillis, now)
        } catch (e: Exception) {
            Log.w(TAG, "Widget undo failed", e)
        }
    }

    private fun receipt(kind: ReceiptKind, entryId: String, at: Long, now: Long, diaperKind: DiaperKind? = null, previous: String? = null) {
        val receipt = WidgetReceipt(kind, entryId, at, now + RECEIPT_MILLIS, diaperKind, previous)
        store.update { it.copy(receipt = receipt, notice = null) }
    }

    private fun notice(type: NoticeType, at: Long, now: Long, diaperKind: DiaperKind? = null) {
        store.update { it.copy(notice = WidgetNotice(type, at, now + NOTICE_MILLIS, diaperKind)) }
    }

    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Widget command failed", e)
        null
    }

    companion object {
        private const val TAG = "WidgetCommands"

        /** How long a widget receipt offers Undo (DESIGN_SPEC §3.3.6). */
        const val RECEIPT_MILLIS = 10_000L

        /** Guard, dedupe and "Undone" lines (DESIGN_SPEC §10.7). */
        const val NOTICE_MILLIS = 2_000L

        /** A second sleep toggle within this window is ignored (DESIGN_SPEC §4.4). */
        const val GUARD_MILLIS = 2_000L

        /** Shared by every command in the process, so widget taps are applied one at a time. */
        private val lock = Mutex()

        /** The commands on the app's core graph and the widgets' memo. */
        fun from(context: Context): WidgetCommands {
            val graph = Core.ensureInitialized(context)
            return WidgetCommands(graph.repository, PrefsWidgetMemoStore.get(context), graph.clock)
        }
    }
}
