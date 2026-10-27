package com.ffmathy.xaternity.phone.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.data.EndResult
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.model.isSleep
import com.ffmathy.xaternity.phone.system.SurfacePrefs
import kotlinx.coroutines.launch

/**
 * The timer notifications' broadcasts (not exported): "Woke up", which ends the running sleep without opening the app
 * (also from the lock screen), and the dismissal of a notification, which is remembered so it is not posted again
 * (DESIGN_SPEC §10.5).
 */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        when (intent.action) {
            ACTION_WOKE_UP -> {
                val pending = goAsync()
                val graph = Core.ensureInitialized(context)
                graph.scope.launch {
                    try {
                        endSleep(context, entryId)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_DISMISSED -> {
                val prefs = SurfacePrefs(context)
                prefs.dismissedTimers = prefs.dismissedTimers + entryId
            }
        }
    }

    companion object {
        private const val TAG = "TimerActionReceiver"
        const val ACTION_WOKE_UP = "com.ffmathy.xaternity.phone.action.WOKE_UP"
        const val ACTION_DISMISSED = "com.ffmathy.xaternity.phone.action.TIMER_DISMISSED"
        const val EXTRA_ENTRY_ID = "entry_id"

        /**
         * Ends the running sleep [entryId] now. A sleep that already ended (on the watch, in the app) is left alone; one
         * stopped within 60 s is discarded by the repository like any mis-tap.
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
            TimerNotifications(context).cancelSleep()
            return result
        }

        fun wokeUpIntent(context: Context, entryId: String): PendingIntent = broadcast(context, ACTION_WOKE_UP, entryId)

        fun dismissedIntent(context: Context, entryId: String): PendingIntent = broadcast(context, ACTION_DISMISSED, entryId)

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
