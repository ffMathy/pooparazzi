package com.ffmathy.pooparazzi.phone.account

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.CoreGraph
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Makes sure entries waiting for the family's backup go up even when the app is not opened (BABIES_AND_BACKUP §4.4).
 *
 * The backup pushes a moment after each write, in the app's process. But a log from the watch arrives through the sync
 * listener, and one from a widget or notification through a broadcast: the process is cached (and soon frozen) right
 * after, so the upload waited until the parent opened the app, and the family's other phones did not see the log.
 * Whenever rows become pending, a one-time job is queued that syncs once there is a network, and again with back-off
 * while the backend cannot be reached.
 */
object BackupWork {
    private const val TAG = "BackupWork"
    private const val WORK_NAME = "backup-pending"

    /** Gives the in-process push (2 s after a write) time to go first: the job then finds nothing to send. */
    private const val DELAY_SECONDS = 30L

    /** Gives up after this many failed attempts; the next pending row, or opening the app, starts again. */
    internal const val MAX_ATTEMPTS = 10

    /** Queues the job each time rows become pending. Call once, after `Core.init` with a backend. */
    fun follow(context: Context, graph: CoreGraph) {
        val room = graph.repository as? RoomEntryRepository ?: return
        val app = context.applicationContext
        graph.scope.launch {
            room.observeCloudPendingCount().map { it > 0 }.distinctUntilChanged().collect { pending ->
                if (pending) enqueue(app)
            }
        }
    }

    private fun enqueue(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        } catch (e: Exception) {
            // No WorkManager (e.g. some tests): the backup still pushes while the app runs.
            Log.w(TAG, "Could not queue the backup", e)
        }
    }

    /** One sync of the backup; tried again later while the backend cannot be reached. */
    class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            val cloud = Core.ensureInitialized(applicationContext).cloud
            cloud.syncNow()
            val broken = cloud.status.value == CloudStatus.Offline || cloud.status.value == CloudStatus.Error
            if (broken && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backup job failed", e)
            Result.success()
        }
    }
}
