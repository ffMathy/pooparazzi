package com.ffmathy.pooparazzi.wear.surface

import android.content.Context
import android.util.Log
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.wear.complication.XComplicationService
import com.ffmathy.pooparazzi.wear.ongoing.RunningActivities
import com.ffmathy.pooparazzi.wear.tile.PooparazziTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Keeps the surfaces outside the app in step with the data (DESIGN_SPEC §10.9, §10.10, §10.6): on every change of the
 * entries, whether written in the app, by the tile, or merged from the phone by the sync listener, it requests a new
 * tile, asks the watch faces for fresh complication data and shows or removes the Ongoing Activities. A change of the
 * baby's name or language (settings synced from the phone) refreshes them too.
 *
 * Runs for the life of the process (started from the Application, so it also runs when only the sync listener woke
 * the app).
 */
class SurfaceUpdater(
    private val context: Context,
    private val repository: EntryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val surfaces: Surfaces = Surfaces.System(context),
) {
    private val refreshes = MutableStateFlow(0)

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                repository.observeEntries().distinctUntilChanged(),
                settings.settings.map { it.babyName to it.language }.distinctUntilChanged(),
                refreshes,
            ) { entries, _, _ -> entries }
                .collect { entries ->
                    val summary = summarize(entries, clock.now(), ZoneId.systemDefault())
                    try {
                        withContext(Dispatchers.Main) { surfaces.update(summary) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not update the watch surfaces", e)
                    }
                }
        }
    }

    /** Rebuilds everything once more (e.g. after the language or time zone changed). */
    fun refresh() {
        RunningActivities.invalidate()
        refreshes.value++
    }

    /** Brings every surface up to date now and returns when done (after a reboot, before the process may end). */
    suspend fun updateNow() {
        val summary = summarize(repository.observeEntries().first(), clock.now(), ZoneId.systemDefault())
        withContext(Dispatchers.Main) { surfaces.update(summary) }
    }

    /** Where updates go; replaced in tests. */
    fun interface Surfaces {
        fun update(summary: Summary)

        class System(private val context: Context) : Surfaces {
            private val running = RunningActivities(context)

            override fun update(summary: Summary) {
                PooparazziTileService.requestUpdate(context)
                XComplicationService.requestUpdateAll(context)
                running.sync(summary)
            }
        }
    }

    private companion object {
        const val TAG = "SurfaceUpdater"
    }
}
