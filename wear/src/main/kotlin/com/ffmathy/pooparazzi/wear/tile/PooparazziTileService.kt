package com.ffmathy.pooparazzi.wear.tile

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.TimelineBuilders.TimeInterval
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.protolayout.expression.VersionBuilders.VersionInfo
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.wear.surface.SurfaceHaptics
import com.ffmathy.pooparazzi.wear.surface.SurfaceText
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * The Pooparazzi tile (DESIGN_SPEC §3.6, §10.9; V1_ADDITIONS §2.2), in the fixed watch palette
 * (`allowDynamicTheme = false`, §10.13).
 *
 * A tap on Pee, Poop, Both or the sleep toggle is a `loadAction`: the system asks for a new tile with the tapped
 * clickable's id, the write happens here ([TileActions]), and the answer carries the 10 s confirmation as its own
 * timeline entry. The app requests a new tile whenever the entries or settings change, also through sync
 * ([com.ffmathy.pooparazzi.wear.surface.SurfaceUpdater]).
 */
class PooparazziTileService : Material3TileService(allowDynamicTheme = false, defaultColorScheme = TileTheme.colorScheme) {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val graph = Core.init(applicationContext)
        val memory = PreferencesTileMemory.of(applicationContext)
        val zone = ZoneId.systemDefault()

        val clicked = requestParams.currentState.lastClickableId
        val haptic = TileActions(graph.repository, memory, zone).handle(clicked, graph.clock.now())
        haptic?.let { SurfaceHaptics.play(applicationContext, it) }

        val now = graph.clock.now()
        val summary = summarize(graph.repository.observeEntries().first(), now, zone)
        val settings = graph.settings.current()
        val name = TileUiState.titleName(settings, LabelFormatter(this@PooparazziTileService))
        val state = TileUiState.from(summary, name, memory.feedback, memory.guard, settings.selectedBaby.id)
        val dynamic = deviceConfiguration.rendererSchemaVersion.supportsDynamicText()
        return tile(state, SurfaceText.from(this@PooparazziTileService, zone), memory.nextToken(), dynamic)
    }

    companion object {
        private const val TAG = "PooparazziTile"

        /** Asks the system for a fresh tile (after every write, sync merge or settings change). */
        fun requestUpdate(context: Context) {
            try {
                TileService.getUpdater(context).requestUpdate(PooparazziTileService::class.java)
            } catch (e: Exception) {
                // No tile host (e.g. tests, or a watch without tiles): nothing to refresh.
                Log.d(TAG, "Tile update not requested", e)
            }
        }
    }
}

/**
 * The tile for [state]: one timeline entry per title change (the 10 s confirmation and the 2 s guard each end on
 * their own), all built in this [MaterialScope] so their images are registered once.
 *
 * Every duration is a dynamic expression the renderer ticks; renderers without dynamic text get a refresh every
 * minute instead ([Tile.freshnessIntervalMillis]).
 */
fun MaterialScope.tile(state: TileUiState, text: SurfaceText, token: Long, dynamic: Boolean = true): Tile {
    val starts = listOf(state.now) + state.changes
    val timeline = Timeline.Builder()
    starts.forEachIndexed { index, start ->
        val end = starts.getOrNull(index + 1)
        val layout = Layout.Builder().setRoot(pooparazziTile(state.at(start), text, token, dynamic)).build()
        val entry = TimelineEntry.Builder().setLayout(layout)
        if (starts.size > 1) {
            // The first entry starts "now"; the last one has no end.
            entry.setValidity(
                TimeInterval.Builder()
                    .apply { if (index > 0) setStartMillis(start) }
                    .apply { if (end != null) setEndMillis(end) }
                    .build(),
            )
        }
        timeline.addTimelineEntry(entry.build())
    }
    return Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(timeline.build())
        .setFreshnessIntervalMillis(if (dynamic) 0L else STATIC_REFRESH_MILLIS)
        .build()
}

private const val RESOURCES_VERSION = "2"
private const val STATIC_REFRESH_MILLIS = 60_000L

/** Dynamic text and conditions arrived with renderer schema 1.200 (Wear OS 4). */
internal fun VersionInfo.supportsDynamicText(): Boolean = major > 1 || (major == 1 && minor >= 200)
