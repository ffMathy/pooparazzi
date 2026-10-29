package com.ffmathy.pooparazzi.phone.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidget
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.domain.epochMillis
import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.phone.ui.theme.NightRule
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
            // By each provider's own widget ids: Glance's updateAll only finds widgets whose receiver has already handled
            // an update, and a host that never sent one (Samsung's cover screen) would then never be drawn.
            val manager = AppWidgetManager.getInstance(context)
            val glance = GlanceAppWidgetManager(context)
            val placed = providers().flatMap { (receiver, widget) ->
                manager.getAppWidgetIds(ComponentName(context, receiver)).map { widget to glance.getGlanceIdBy(it) }
            }
            if (placed.isEmpty()) return
            placed.forEach { (widget, id) -> widget.update(context, id) }
            scheduleTimedRefreshes(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh failed", e)
        }
    }

    /** Each widget provider's receiver and the widget it draws. */
    private fun providers(): List<Pair<Class<*>, GlanceAppWidget>> = listOf(
        LogWidgetReceiver::class.java to LogWidget(),
        QuickWidgetReceiver::class.java to QuickWidget(),
        CoverWidgetReceiver::class.java to CoverWidget(),
    )

    private suspend fun scheduleTimedRefreshes(context: Context) {
        val graph = Core.ensureInitialized(context)
        val now = graph.clock.now()
        val zone = ZoneId.systemDefault()
        val work = WorkManager.getInstance(context)
        PrefsWidgetMemoStore.get(context).memo.value.nextExpiry(now)?.let { at -> work.refreshAt(context, "expiry", at, now) }
        WidgetSource.from(context).current().forecastExpiry(now)?.let { at -> work.refreshAt(context, "forecast", at, now) }
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

    /** Stops the timed refreshes once no widget of any kind is placed (a provider's last instance was removed). */
    fun cancelIfNoneLeft(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val left = providers().sumOf { (receiver, _) -> manager.getAppWidgetIds(ComponentName(context, receiver)).size }
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
