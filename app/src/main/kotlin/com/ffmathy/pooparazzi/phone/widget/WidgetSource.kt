package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.CoreGraph
import com.ffmathy.pooparazzi.core.data.CurrentLog
import com.ffmathy.pooparazzi.core.data.observeCurrentLog
import com.ffmathy.pooparazzi.phone.system.systemDarkTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * The data behind the widgets: entries, settings and the widget memo, rebuilt into a [WidgetSnapshot] whenever one of
 * them changes or [WidgetUpdater] refreshes (a receipt expired, the night window began). The settings come with their
 * current baby's entries ([observeCurrentLog]), so a switch never labels the new baby with the previous one's facts,
 * and with every baby's, for Auto's "any baby asleep".
 */
internal class WidgetSource(
    private val context: Context,
    private val graph: CoreGraph,
    private val store: WidgetMemoStore,
    private val babies: WidgetBabies,
) {
    /** The snapshots of the widget [widgetId] (null: one that has no id yet, which shows the current baby). */
    fun snapshots(widgetId: Int?): Flow<WidgetSnapshot> = combine(
        graph.repository.observeCurrentLog(graph.settings.settings),
        store.memo,
        babies.chosen,
        WidgetUpdater.refreshes,
    ) { log, memo, chosen, _ -> build(log, memo, chosen, widgetId) }
        .distinctUntilChanged()

    suspend fun current(widgetId: Int? = null): WidgetSnapshot =
        build(graph.repository.observeCurrentLog(graph.settings.settings).first(), store.memo.value, babies.chosen.value, widgetId)

    /** The widget's own baby ([WidgetBabies]), else the current one; its log is cut from every baby's. */
    private fun build(log: CurrentLog, memo: WidgetMemo, chosen: Map<Int, String>, widgetId: Int?): WidgetSnapshot {
        val baby = WidgetBabies.babyOf(chosen, widgetId, log.settings)
        return WidgetSnapshot.of(
            log.allEntries.filter { it.babyId == baby.id },
            log.settings,
            memo,
            graph.clock.now(),
            ZoneId.systemDefault(),
            context.applicationContext.systemDarkTheme,
            allEntries = log.allEntries,
            babyId = baby.id,
        )
    }

    companion object {
        fun from(context: Context): WidgetSource =
            WidgetSource(context.applicationContext, Core.ensureInitialized(context), PrefsWidgetMemoStore.get(context), PrefsWidgetBabies.get(context))
    }
}

/** Every widget's [GlanceAppWidget.provideGlance]: [content] for the current snapshot, recomposed as it changes. */
internal suspend fun GlanceAppWidget.provideSnapshots(context: Context, id: GlanceId, content: @Composable (WidgetSnapshot) -> Unit): Nothing {
    val source = WidgetSource.from(context)
    val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
    val initial = source.current(widgetId)
    val snapshots = source.snapshots(widgetId)
    provideContent {
        val snapshot by snapshots.collectAsState(initial)
        content(snapshot)
    }
}
