package com.ffmathy.pooparazzi.phone.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.data.EndResult
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.phone.system.SurfacePrefs
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
                        endSleep(context, entryId, intent.getStringExtra(EXTRA_BABY_ID))
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
        const val ACTION_WOKE_UP = "com.ffmathy.pooparazzi.phone.action.WOKE_UP"
        const val ACTION_DISMISSED = "com.ffmathy.pooparazzi.phone.action.TIMER_DISMISSED"
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_BABY_ID = "baby_id"

        /**
         * Ends the running sleep [entryId] now, whichever baby is the current one, and cancels that baby's notification
         * ([babyId], else the entry's). A sleep that already ended (on the watch, in the app) is left alone; one stopped
         * within 60 s is discarded by the repository like any mis-tap.
         */
        suspend fun endSleep(context: Context, entryId: String, babyId: String? = null): EndResult? {
            val graph = Core.ensureInitialized(context)
            val sleep = graph.repository.get(entryId)
            val result = if (sleep != null && sleep.isSleep && sleep.isRunning) {
                runCatching { graph.repository.endSleep(entryId, graph.clock.now()) }
                    .onFailure { Log.w(TAG, "Could not end the sleep", it) }
                    .getOrNull()
            } else {
                null
            }
            TimerNotifications(context).cancelSleep(babyId ?: sleep?.babyId ?: Baby.FIRST_ID)
            return result
        }

        fun wokeUpIntent(context: Context, entryId: String, babyId: String): PendingIntent =
            broadcast(context, ACTION_WOKE_UP, entryId, babyId)

        fun dismissedIntent(context: Context, entryId: String): PendingIntent = broadcast(context, ACTION_DISMISSED, entryId)

        private fun broadcast(context: Context, action: String, entryId: String, babyId: String? = null): PendingIntent {
            val intent = Intent(context, TimerActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ENTRY_ID, entryId)
                .apply { if (babyId != null) putExtra(EXTRA_BABY_ID, babyId) }
            return PendingIntent.getBroadcast(
                context,
                entryId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
