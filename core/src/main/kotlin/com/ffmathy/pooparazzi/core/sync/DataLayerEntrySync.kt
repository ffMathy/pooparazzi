package com.ffmathy.pooparazzi.core.sync

import android.content.Context
import android.util.Log
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.SystemClock
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryJson
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [EntrySync] over the Wearable Data Layer.
 *
 * - Each write is a data item: `/entries/<id>` with the JSON entry, `/settings` with the JSON settings,
 *   sent with `setUrgent()`. Play Services queues items while the other device is away.
 * - [pullAll] (at startup) merges every item already in the Data Layer, including those that arrived
 *   while this app was not installed or running. The listener service delivers later changes to [receive].
 * - Items are never deleted: a purged entry keeps its `deleted = true` item, so a device that was away for
 *   the whole retention period still learns of the deletion.
 * - Nothing here throws: without Play Services or a paired device the status becomes
 *   [SyncStatus.Unavailable] and the apps keep working locally.
 */
class DataLayerEntrySync internal constructor(
    private val dataLayer: DataLayer,
    private val clock: Clock,
) : EntrySync {

    constructor(context: Context, clock: Clock = SystemClock) : this(PlayServicesDataLayer(context), clock)

    private val state = MutableStateFlow<SyncStatus>(SyncStatus.Pending)
    override val status: StateFlow<SyncStatus> = state.asStateFlow()

    @Volatile
    private var sink: SyncSink? = null

    override fun attach(sink: SyncSink) {
        this.sink = sink
    }

    override suspend fun push(entry: Entry): Boolean =
        put(SyncPaths.entry(entry.id), EntryJson.encode(entry))

    override suspend fun pushSettings(settings: Settings): Boolean =
        put(SyncPaths.SETTINGS, SettingsJson.encode(settings))

    override suspend fun pullAll(): Int {
        val sink = sink ?: return 0
        if (state.value !is SyncStatus.Synced) state.value = SyncStatus.Pending
        val items = attempt { dataLayer.items(SyncPaths.ENTRIES) + dataLayer.items(SyncPaths.SETTINGS) } ?: return 0
        val applied = merge(items, sink)
        refreshStatus()
        return applied
    }

    override suspend fun receive(items: List<RemoteItem>): Int {
        val sink = sink ?: return 0
        // onDataChanged fires on every connected node, the writer included. Our own items are already in the
        // database, and they say nothing about the other device being reachable.
        val localId = attempt(reportFailure = false) { dataLayer.localNodeId() }
        val remote = items.filter { localId == null || it.sourceNodeId != localId }
        if (remote.isEmpty()) return 0
        val applied = merge(remote, sink)
        // Something just arrived from the other device, so it is connected.
        state.value = SyncStatus.Synced(clock.now())
        return applied
    }

    override suspend fun retry() {
        val sink = sink ?: return
        var allPushed = safe { sink.pushPendingEntries() } ?: false
        safe { sink.localSettings() }?.let { if (!pushSettings(it)) allPushed = false }
        if (allPushed) refreshStatus()
    }

    /** Re-reads whether the other device is reachable and updates [status]. */
    suspend fun refreshStatus() {
        val peers = attempt { dataLayer.peerState() } ?: return
        state.value = when (peers) {
            PeerState.CONNECTED -> SyncStatus.Synced(clock.now())
            PeerState.DISCONNECTED -> SyncStatus.Offline
            PeerState.NONE -> SyncStatus.Unavailable
        }
    }

    private suspend fun put(path: String, json: String): Boolean {
        attempt { dataLayer.put(path, json.encodeToByteArray(), urgent = true) } ?: return false
        refreshStatus()
        return true
    }

    /**
     * Decodes [items] and hands all entries to the sink as **one batch**, so it can apply them oldest write
     * first and resolve cross-device conflicts once, whatever order the Data Layer delivered them in.
     * (Startup pulls include this device's own items: they restore a database that lost its data.)
     */
    private suspend fun merge(items: List<RemoteItem>, sink: SyncSink): Int {
        var applied = 0
        val entries = mutableListOf<Entry>()
        for (item in items) {
            val text = item.data.decodeToString()
            val entryId = SyncPaths.entryId(item.path)
            when {
                entryId != null -> {
                    val entry = EntryJson.decodeOrNull(text)
                    if (entry == null || entry.id != entryId) Log.w(TAG, "Ignoring malformed item at ${item.path}") else entries += entry
                }
                item.path == SyncPaths.SETTINGS -> {
                    val remote = SettingsJson.decodeRemoteOrNull(text)
                    val stored = remote?.let {
                        safe { if (it.legacy) sink.applyRemoteLegacySettings(it.settings) else sink.applyRemoteSettings(it.settings) }
                    }
                    if (stored == true) applied++
                }
            }
        }
        if (entries.isNotEmpty()) {
            applied += safe { sink.applyRemoteEntries(entries.sortedWith(compareBy<Entry> { it.updatedAtMillis }.thenBy { it.id })) } ?: 0
        }
        return applied
    }

    /** Runs a Data Layer call; on failure records the status (when [reportFailure]) and returns null. */
    private suspend fun <T : Any> attempt(reportFailure: Boolean = true, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        Log.w(TAG, "Wearable call failed", e)
        if (reportFailure) state.value = if (isWearableUnavailable(e)) SyncStatus.Unavailable else SyncStatus.Error
        null
    }

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Sync merge step failed", e)
        null
    }

    private companion object {
        const val TAG = "EntrySync"
    }
}
