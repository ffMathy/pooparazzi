package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
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
) {
    fun snapshots(): Flow<WidgetSnapshot> = combine(
        graph.repository.observeCurrentLog(graph.settings.settings),
        store.memo,
        WidgetUpdater.refreshes,
    ) { log, memo, _ -> build(log, memo) }
        .distinctUntilChanged()

    suspend fun current(): WidgetSnapshot = build(graph.repository.observeCurrentLog(graph.settings.settings).first(), store.memo.value)

    private fun build(log: CurrentLog, memo: WidgetMemo): WidgetSnapshot = WidgetSnapshot.of(
        log.entries,
        log.settings,
        memo,
        graph.clock.now(),
        ZoneId.systemDefault(),
        context.applicationContext.systemDarkTheme,
        allEntries = log.allEntries,
    )

    companion object {
        fun from(context: Context): WidgetSource =
            WidgetSource(context.applicationContext, Core.ensureInitialized(context), PrefsWidgetMemoStore.get(context))
    }
}
