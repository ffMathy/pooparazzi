package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.CoreGraph
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.phone.system.systemDarkTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * The data behind the widgets: entries, settings and the widget memo, rebuilt into a [WidgetSnapshot] whenever one of
 * them changes or [WidgetUpdater] refreshes (a receipt expired, the night window began).
 */
internal class WidgetSource(
    private val context: Context,
    private val graph: CoreGraph,
    private val store: WidgetMemoStore,
) {
    fun snapshots(): Flow<WidgetSnapshot> = combine(
        graph.repository.observeEntries(),
        graph.settings.settings,
        store.memo,
        WidgetUpdater.refreshes,
    ) { entries, settings, memo, _ -> build(entries, settings, memo) }
        .distinctUntilChanged()

    suspend fun current(): WidgetSnapshot =
        build(graph.repository.observeEntries().first(), graph.settings.current(), store.memo.value)

    private fun build(entries: List<Entry>, settings: Settings, memo: WidgetMemo): WidgetSnapshot =
        WidgetSnapshot.of(entries, settings, memo, graph.clock.now(), ZoneId.systemDefault(), context.applicationContext.systemDarkTheme)

    companion object {
        fun from(context: Context): WidgetSource =
            WidgetSource(context.applicationContext, Core.ensureInitialized(context), PrefsWidgetMemoStore.get(context))
    }
}
