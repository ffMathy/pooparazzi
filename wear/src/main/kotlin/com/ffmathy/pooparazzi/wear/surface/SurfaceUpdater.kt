package com.ffmathy.pooparazzi.wear.surface

import android.content.Context
import android.util.Log
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.format.LabelFormatter
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.wear.complication.XComplicationService
import com.ffmathy.pooparazzi.wear.ongoing.RunningActivities
import com.ffmathy.pooparazzi.wear.tile.PooparazziTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Keeps the surfaces outside the app in step with the data (DESIGN_SPEC §10.9, §10.10, §10.6): on every change of the
 * entries, whether written in the app, by the tile, or merged from the phone by the sync listener, it requests a new
 * tile, asks the watch faces for fresh complication data and shows or removes the Ongoing Activities. A change of the
 * babies (a name, a new baby from the phone's settings), of the watch's current baby (the picker) or of the language
 * refreshes them too: the surfaces show the current baby (BABIES_AND_BACKUP §2.5).
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            // The entries are read again for every change of the shown settings, so a switch of baby is never paired
            // with the previous baby's entries (an Ongoing Activity named for the wrong baby).
            val shownLog = settings.settings.map(::shown).distinctUntilChanged()
                .flatMapLatest { shown -> repository.observeEntries().distinctUntilChanged().map { it to shown } }
            combine(shownLog, refreshes) { log, _ -> log }
                .collect { (entries, settings) ->
                    val summary = summarize(entries, clock.now(), ZoneId.systemDefault())
                    try {
                        withContext(Dispatchers.Main) { surfaces.update(summary, settings) }
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
        val shown = shown(settings.current())
        withContext(Dispatchers.Main) { surfaces.update(summary, shown) }
    }

    /**
     * Where updates go; replaced in tests. [settings] are what the surfaces show of the settings: the babies, the
     * current one and the language ([shown]).
     */
    fun interface Surfaces {
        fun update(summary: Summary, settings: Settings)

        class System(private val context: Context) : Surfaces {
            private val running = RunningActivities(context)

            override fun update(summary: Summary, settings: Settings) {
                PooparazziTileService.requestUpdate(context)
                XComplicationService.requestUpdateAll(context)
                // The Ongoing Activity names the baby when there are two or more ("Noa · Asleep · feed in 2h 05m").
                val baby = if (settings.hasSeveralBabies) LabelFormatter(context).currentBaby(settings) else null
                running.sync(summary, baby)
            }
        }
    }

    private companion object {
        const val TAG = "SurfaceUpdater"

        /** The part of the settings the surfaces show (the appearance does not matter on the watch). */
        fun shown(settings: Settings) =
            Settings(babies = settings.babies, language = settings.language, selectedBabyId = settings.selectedBaby.id)
    }
}
