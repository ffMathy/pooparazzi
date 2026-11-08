package com.ffmathy.pooparazzi.wear.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.CoreGraph
import com.ffmathy.pooparazzi.wear.WearApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Catches the watch up with the phone when the sync listener was not woken (DESIGN_SPEC §10.9, §3.8).
 *
 * The phone's changes reach the watch's Data Layer, but Play Services may deliver them to the listener late, or not
 * until the app runs again; the tile and the Ongoing Activity then showed old data until the app was opened. So the
 * watch also merges what its Data Layer holds:
 * - when the tile is drawn, at most every [TILE_PULL_INTERVAL_MILLIS] ([onTileRequest]), in the background: anything
 *   new asks for another tile;
 * - every [PERIODIC_MINUTES] minutes from WorkManager ([schedule]), also when the process has died, which then brings the
 *   tile, complications and Ongoing Activities up to date too.
 *
 * A pull reads the watch's own copy of the Data Layer (no radio) and stores only what is newer.
 */
object WatchCatchUp {
    private const val TAG = "WatchCatchUp"
    private const val WORK_NAME = "watch-catch-up"
    internal const val PERIODIC_MINUTES = 15L
    internal const val TILE_PULL_INTERVAL_MILLIS = 60_000L

    private val lastTilePull = AtomicLong(Long.MIN_VALUE / 2)

    /** Queues the periodic catch-up once (kept if already queued). */
    fun schedule(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<CatchUpWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } catch (e: Exception) {
            // No WorkManager (e.g. some tests): the listener and the tile still sync.
            Log.w(TAG, "Could not schedule the catch-up", e)
        }
    }

    /**
     * A tile is being drawn: merges the Data Layer in the background, unless that happened in the last
     * [TILE_PULL_INTERVAL_MILLIS]. Returns whether a pull was started.
     */
    fun onTileRequest(graph: CoreGraph, now: Long = graph.clock.now()): Boolean {
        val last = lastTilePull.get()
        if (now - last in 0 until TILE_PULL_INTERVAL_MILLIS) return false
        if (!lastTilePull.compareAndSet(last, now)) return false
        // A merge changes the entries, and the SurfaceUpdater asks for a new tile.
        graph.scope.launch { graph.sync.pullAll() }
        return true
    }

    /** Merges the Data Layer and brings the surfaces up to date; returns when both are done. */
    suspend fun now(context: Context) {
        val graph = Core.init(context.applicationContext)
        graph.sync.pullAll()
        (context.applicationContext as? WearApp)?.updateSurfacesNow()
    }

    internal fun resetForTest() = lastTilePull.set(Long.MIN_VALUE / 2)

    class CatchUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            now(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Catch-up failed", e)
            Result.success()
        }
    }
}
