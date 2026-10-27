package com.ffmathy.pooparazzi.phone.system

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.ffmathy.pooparazzi.core.CoreGraph
import com.ffmathy.pooparazzi.phone.BuildConfig
import com.ffmathy.pooparazzi.phone.notify.NotificationLink
import com.ffmathy.pooparazzi.phone.notify.TimerNotifications
import com.ffmathy.pooparazzi.phone.notify.TimerState
import com.ffmathy.pooparazzi.phone.widget.LogWidgetReceiver
import com.ffmathy.pooparazzi.phone.widget.QuickWidgetReceiver
import com.ffmathy.pooparazzi.phone.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The surfaces outside the app's window: the home-screen widgets and the ongoing timer notifications. Started once per
 * process (the app, a widget tap, a notification action and the sync listener all start the process), they follow the
 * entries and settings from the application scope, so a sleep ended on the watch also updates the widget and cancels
 * the notification (DESIGN_SPEC §10.5, §10.11).
 */
object SystemSurfaces {
    private const val TAG = "SystemSurfaces"

    /** Bumped to re-render the notifications without a data change (the permission was just granted). */
    private val notificationRefreshes = MutableStateFlow(0)

    @Volatile
    private var started: CoreGraph? = null

    fun start(context: Context, graph: CoreGraph) {
        if (started === graph) return
        started = graph
        val app = context.applicationContext
        val notifications = TimerNotifications(app)
        graph.scope.launch {
            val states = combine(graph.repository.observeEntries(), graph.settings.settings) { entries, settings ->
                TimerState.of(entries, settings, graph.clock.now(), ZoneId.systemDefault())
            }.distinctUntilChanged()
            // Equal states post nothing new, but a refresh (the permission was just granted) always renders again.
            combine(states, notificationRefreshes) { state, _ -> state }
                .collect { state -> guard("notifications") { notifications.render(state) } }
        }
        graph.scope.launch {
            combine(graph.repository.observeEntries(), graph.settings.settings) { _, _ -> Unit }
                .conflate()
                .collect {
                    guard("widgets") { WidgetUpdater.refresh(app) }
                    // Coalesce bursts (sync batches, undo + log) into one widget update.
                    delay(WIDGET_COALESCE_MILLIS)
                }
        }
        graph.scope.launch { publishWidgetPreviews(app) }
    }

    /** Re-renders the timer notifications (e.g. once the permission is granted). */
    fun refreshNotifications() {
        notificationRefreshes.value++
    }

    /** Whether notifications could be posted when the app last came to the foreground; null before that. */
    @Volatile
    private var notificationsAllowed: Boolean? = null

    /**
     * The app came to the foreground. When notifications were turned on meanwhile (the permission in system settings,
     * the app's notification switch, or the in-context permission dialog), the running timers' notifications are
     * posted now rather than at the next entry change.
     */
    fun onAppResumed(context: Context) {
        val allowed = NotificationLink.current(context) != NotificationLink.OFF
        val before = notificationsAllowed
        notificationsAllowed = allowed
        if (allowed && before == false) refreshNotifications()
    }

    /** Re-renders the widgets after a configuration change (system dark theme, language) while the process runs. */
    fun onConfigurationChanged(context: Context, graph: CoreGraph) {
        graph.scope.launch { guard("widgets") { WidgetUpdater.refresh(context.applicationContext) } }
    }

    /**
     * Publishes the generated widget previews (Glance 1.2, Android 15+) once per app version: the call is rate-limited
     * by the system, so it is not repeated on every start.
     */
    private suspend fun publishWidgetPreviews(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = SurfacePrefs(context)
        if (prefs.widgetPreviewsVersion == BuildConfig.VERSION_CODE.toLong()) return
        guard("widget previews") {
            val manager = GlanceAppWidgetManager(context)
            val results = listOf(LogWidgetReceiver::class, QuickWidgetReceiver::class).map { manager.setWidgetPreviews(it) }
            if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
                prefs.widgetPreviewsVersion = BuildConfig.VERSION_CODE.toLong()
            }
        }
    }

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Updating the $what failed", e)
        }
    }

    private const val WIDGET_COALESCE_MILLIS = 300L
}
