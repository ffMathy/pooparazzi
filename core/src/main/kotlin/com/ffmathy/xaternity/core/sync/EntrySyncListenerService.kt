package com.ffmathy.xaternity.core.sync

import android.util.Log
import com.ffmathy.xaternity.core.Core
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives `/entries/<id>` and `/settings` changes from the other device and merges them (last-writer-wins).
 * Declared in core's manifest, so it is merged into both apps. Play Services calls it on a background
 * thread, so blocking until the merge is written is fine (and keeps the work inside the service's lifetime).
 */
class EntrySyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            // Copy out before returning: the buffer is released after this call.
            val items = remoteItems(dataEvents)
            if (items.isEmpty()) return
            val graph = Core.ensureInitialized(applicationContext)
            runBlocking {
                withTimeoutOrNull(RECEIVE_TIMEOUT_MILLIS) { graph.sync.receive(items) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not merge remote data", e)
        }
    }

    internal companion object {
        private const val TAG = "EntrySyncListener"
        private const val RECEIVE_TIMEOUT_MILLIS = 20_000L

        /**
         * Copies the changed items out of [events], with the writing node from the item's URI host. Deletions are
         * skipped: items are never deleted on purpose (a purged entry keeps its `deleted = true` item), so a
         * TYPE_DELETED event carries nothing to merge. [receive][EntrySync.receive] drops this device's own items.
         */
        fun remoteItems(events: Iterable<DataEvent>): List<RemoteItem> = buildList {
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                val path = item.uri.path ?: continue
                val data = item.data ?: continue
                add(RemoteItem(path, data.copyOf(), item.uri.host))
            }
        }
    }
}
