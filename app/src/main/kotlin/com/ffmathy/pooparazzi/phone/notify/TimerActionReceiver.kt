package com.ffmathy.pooparazzi.phone.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.CoreGraph
import com.ffmathy.pooparazzi.core.data.EndResult
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The timer notifications' broadcasts (not exported): "Woke up", which ends the running sleep without opening the app
 * (also from the lock screen); the Earlier offsets of the woke-up notification that follows, which move the wake-up
 * time; the dismissal of a notification, which is remembered so it is not posted again (DESIGN_SPEC §10.5); and the
 * once-a-minute refresh of the running sleep's duration.
 */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> async(context) { refresh(context) }
            ACTION_WOKE_UP -> {
                val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
                async(context) { endSleep(context, entryId) }
            }
            ACTION_EARLIER -> {
                val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
                val base = intent.getLongExtra(EXTRA_BASE, -1L).takeIf { it >= 0 } ?: return
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                val current = intent.getIntExtra(EXTRA_CURRENT, 0)
                async(context) { moveWakeUp(context, entryId, base, minutes, current) }
            }
            ACTION_DISMISSED -> {
                val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
                val prefs = SurfacePrefs(context)
                prefs.dismissedTimers = prefs.dismissedTimers + entryId
            }
        }
    }

    private fun async(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        Core.ensureInitialized(context).scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TimerActionReceiver"
        const val ACTION_WOKE_UP = "com.ffmathy.pooparazzi.phone.action.WOKE_UP"
        const val ACTION_EARLIER = "com.ffmathy.pooparazzi.phone.action.WOKE_UP_EARLIER"
        const val ACTION_DISMISSED = "com.ffmathy.pooparazzi.phone.action.TIMER_DISMISSED"
        const val ACTION_REFRESH = "com.ffmathy.pooparazzi.phone.action.TIMER_REFRESH"
        const val EXTRA_ENTRY_ID = "entry_id"
        private const val EXTRA_BASE = "base_millis"
        private const val EXTRA_MINUTES = "offset_minutes"
        private const val EXTRA_CURRENT = "current_offset_minutes"
        private const val MINUTE_MILLIS = 60_000L

        /**
         * Ends the running sleep [entryId] now and posts the woke-up notification with its Earlier offsets. A sleep that
         * already ended (on the watch, in the app) is left alone; one stopped within 60 s is discarded by the repository
         * like any mis-tap.
         */
        suspend fun endSleep(context: Context, entryId: String): EndResult? {
            val graph = Core.ensureInitialized(context)
            val sleep = graph.repository.get(entryId)
            val result = if (sleep != null && sleep.isSleep && sleep.isRunning) {
                runCatching { graph.repository.endSleep(entryId, graph.clock.now()) }
                    .onFailure { Log.w(TAG, "Could not end the sleep", it) }
                    .getOrNull()
            } else {
                null
            }
            val notifications = TimerNotifications(context)
            notifications.cancelSleep()
            if (result is EndResult.Ended) {
                val end = result.entry.endMillis ?: graph.clock.now()
                notifications.renderWokeUp(state(graph), result.entry, baseMillis = end, offsetMinutes = 0)
            }
            return result
        }

        /**
         * An Earlier offset on the woke-up notification: moves the end of [entryId] to [baseMillis] + [minutes], or back
         * to [baseMillis] when [minutes] is the [current] offset (as the undo card's chips). An offset that would end the
         * sleep before it started is refused. A sleep deleted or running again meanwhile drops the notification.
         */
        suspend fun moveWakeUp(context: Context, entryId: String, baseMillis: Long, minutes: Int, current: Int): Entry? {
            val graph = Core.ensureInitialized(context)
            val notifications = TimerNotifications(context)
            val sleep = graph.repository.get(entryId)?.takeUnless { it.deleted || it.isRunning || !it.isSleep }
            if (sleep == null) {
                notifications.cancelWokeUp()
                return null
            }
            val next = if (minutes == current) 0 else minutes
            val target = baseMillis + next * MINUTE_MILLIS
            val moved = if (target < sleep.startMillis || target > graph.clock.now()) {
                null
            } else {
                runCatching { graph.repository.update(sleep.copy(endMillis = target)) }
                    .onFailure { Log.w(TAG, "Could not move the wake-up", it) }
                    .getOrNull()
            }
            notifications.renderWokeUp(state(graph), moved ?: sleep, baseMillis, if (moved != null) next else current)
            return moved
        }

        /** Re-renders the running sleep's notification with the current time (the once-a-minute alarm). */
        suspend fun refresh(context: Context) {
            val graph = Core.ensureInitialized(context)
            TimerNotifications(context).render(state(graph))
        }

        private suspend fun state(graph: CoreGraph): TimerState = TimerState.of(
            graph.repository.observeEntries().first(),
            graph.settings.settings.first(),
            graph.clock.now(),
            ZoneId.systemDefault(),
        )

        fun wokeUpIntent(context: Context, entryId: String): PendingIntent = broadcast(context, ACTION_WOKE_UP, entryId)

        fun dismissedIntent(context: Context, entryId: String): PendingIntent = broadcast(context, ACTION_DISMISSED, entryId)

        /** One Earlier offset of the woke-up notification; [current] is the offset applied when it was posted. */
        fun earlierIntent(context: Context, entryId: String, baseMillis: Long, minutes: Int, current: Int): PendingIntent {
            val intent = Intent(context, TimerActionReceiver::class.java)
                .setAction(ACTION_EARLIER)
                .putExtra(EXTRA_ENTRY_ID, entryId)
                .putExtra(EXTRA_BASE, baseMillis)
                .putExtra(EXTRA_MINUTES, minutes)
                .putExtra(EXTRA_CURRENT, current)
            return PendingIntent.getBroadcast(
                context,
                31 * entryId.hashCode() + minutes,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TimerActionReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun broadcast(context: Context, action: String, entryId: String): PendingIntent {
            val intent = Intent(context, TimerActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ENTRY_ID, entryId)
            return PendingIntent.getBroadcast(
                context,
                entryId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
