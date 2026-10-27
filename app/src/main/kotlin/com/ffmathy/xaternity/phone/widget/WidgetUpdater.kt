package com.ffmathy.xaternity.phone.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.domain.epochMillis
import com.ffmathy.xaternity.core.domain.localDate
import com.ffmathy.xaternity.core.settings.Appearance
import com.ffmathy.xaternity.phone.ui.theme.NightRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps the placed widgets current (DESIGN_SPEC §10.11): re-renders them on every log and sleep change (the app scope
 * observes the entries, see `SystemSurfaces`), after every widget command, when a receipt or notice expires, and at the
 * night-window boundaries while the Appearance is Auto (Night ↔ Day). The timed refreshes are one-time WorkManager work,
 * so they also happen when the process has died.
 */
object WidgetUpdater {
    private val ticks = MutableStateFlow(0L)

    /** Bumped on every refresh; running widget sessions recompute their snapshot (with a new "now") on each tick. */
    val refreshes: StateFlow<Long> = ticks.asStateFlow()

    /** Re-renders every placed widget, then schedules the next time-based refresh. Does nothing without widgets. */
    suspend fun refresh(context: Context) {
        ticks.update { it + 1 }
        try {
            val manager = GlanceAppWidgetManager(context)
            val placed = manager.getGlanceIds(LogWidget::class.java).size + manager.getGlanceIds(QuickWidget::class.java).size
            if (placed == 0) return
            LogWidget().updateAll(context)
            QuickWidget().updateAll(context)
            scheduleTimedRefreshes(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh failed", e)
        }
    }

    private suspend fun scheduleTimedRefreshes(context: Context) {
        val graph = Core.ensureInitialized(context)
        val now = graph.clock.now()
        val zone = ZoneId.systemDefault()
        val work = WorkManager.getInstance(context)
        PrefsWidgetMemoStore.get(context).memo.value.nextExpiry(now)?.let { at -> work.refreshAt(context, "expiry", at, now) }
        if (graph.settings.current().appearance == Appearance.AUTO) {
            work.refreshAt(context, "night", nextNightBoundary(now, zone), now)
        }
    }

    /**
     * One refresh at [at]. Each deadline has its own unique work (kept if already queued), so a refresh never replaces
     * the work that is running it.
     */
    private fun WorkManager.refreshAt(context: Context, purpose: String, at: Long, now: Long) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay((at - now).coerceAtLeast(0L) + SLACK_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(TAG_REFRESH)
            .build()
        enqueueUniqueWork("widget-refresh-$purpose-$at", ExistingWorkPolicy.KEEP, request)
    }

    /** Stops the timed refreshes once no widget of either size is placed (a provider's last instance was removed). */
    fun cancelIfNoneLeft(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val left = listOf(LogWidgetReceiver::class.java, QuickWidgetReceiver::class.java)
            .sumOf { manager.getAppWidgetIds(ComponentName(context, it)).size }
        if (left == 0) runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(TAG_REFRESH) }
    }

    /** The next start or end of the night window after [now] (DESIGN_SPEC §8.1, 19:00 and 07:00 by default). */
    fun nextNightBoundary(
        now: Long,
        zone: ZoneId,
        start: LocalTime = NightRule.DEFAULT_NIGHT_START,
        end: LocalTime = NightRule.DEFAULT_NIGHT_END,
    ): Long {
        val today = localDate(now, zone)
        return listOf(today, today.plusDays(1))
            .flatMap { day -> listOf(start, end).map { epochMillis(day, it.hour, it.minute, zone) } }
            .filter { it > now }
            .min()
    }

    private const val TAG = "WidgetUpdater"
    private const val TAG_REFRESH = "widget-refresh"

    /** WorkManager may run early by a few milliseconds; a little slack makes sure the deadline has passed. */
    private const val SLACK_MILLIS = 250L
}

/** Runs one [WidgetUpdater.refresh] (a receipt expired, or the night window began or ended). */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refresh(applicationContext)
        return Result.success()
    }
}
