package com.ffmathy.pooparazzi.wear.ongoing

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * The Ongoing Activity's minute tick: its status is relative ("Asleep · feed in 2h 05m"), so while a sleep runs it is
 * redrawn every minute ([RunningActivities] schedules the next tick, or cancels it once the sleep ends). Only the
 * Ongoing Activity is redrawn; the tile and complications keep their own schedules.
 */
class OngoingTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val graph = Core.ensureInitialized(context)
        graph.scope.launch {
            try {
                val settings = graph.settings.current()
                val summary = summarize(graph.repository.observeEntries().first(), graph.clock.now(), ZoneId.systemDefault())
                val baby = if (settings.hasSeveralBabies) LabelFormatter(context).currentBaby(settings) else null
                withContext(Dispatchers.Main) { RunningActivities(context).sync(summary, baby) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not redraw the Ongoing Activity", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "OngoingTick"

        fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, OngoingTickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
