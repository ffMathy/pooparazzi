package com.ffmathy.pooparazzi.core.sync

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-level sync state shown in the UI (DESIGN_SPEC §4.5). */
sealed interface SyncStatus {
    /** The last exchange with the other device succeeded at [atMillis]: "Synced with watch · 14:31". */
    data class Synced(val atMillis: Long) : SyncStatus

    /** A sync is in progress (startup merge): "Syncing with watch…". */
    data object Pending : SyncStatus

    /** Writes are saved and queued, but the other device is not reachable: "Saved on phone · will sync". */
    data object Offline : SyncStatus

    /** The last sync attempt failed: "Couldn't save · Tap to retry" on the phone (local data is safe). */
    data object Error : SyncStatus

    /**
     * Sync is not possible here: no Play Services / Wearable API, or no paired device with the app.
     * Everything works locally; apps hide the sync line.
     */
    data object Unavailable : SyncStatus
}

/** A Data Layer item as received: its path, its payload and the node that wrote it. */
class RemoteItem(val path: String, val data: ByteArray, val sourceNodeId: String? = null)

/** What sync merges into and re-pushes from. Wired by [com.ffmathy.pooparazzi.core.Core]. */
interface SyncSink {
    suspend fun applyRemoteEntry(entry: Entry): Boolean

    /**
     * Merges one delivery (or one startup pull) as a unit, so the outcome does not depend on the order of the
     * items in it. Returns how many were stored.
     */
    suspend fun applyRemoteEntries(entries: List<Entry>): Int = entries.count { applyRemoteEntry(it) }

    suspend fun applyRemoteSettings(settings: Settings): Boolean

    /**
     * Re-pushes the local entry writes that sync has not accepted yet, through the same ordered path as new writes
     * (the current version of each entry). Returns true when all were accepted.
     */
    suspend fun pushPendingEntries(): Boolean

    /** The local settings to (re)push, or null when they were never changed. */
    suspend fun localSettings(): Settings?
}

/**
 * Phone ↔ watch sync. Every local write is pushed as a data item (`/entries/<id>` holding the
 * JSON-encoded entry, `/settings` for settings). Implementations never throw from these calls.
 */
interface EntrySync {
    val status: StateFlow<SyncStatus>

    /** Connects the merge target. Called once by Core. */
    fun attach(sink: SyncSink)

    /** Pushes one entry. Returns true when the Data Layer accepted it (it then syncs when connected). */
    suspend fun push(entry: Entry): Boolean

    suspend fun pushSettings(settings: Settings): Boolean

    /** Merges every item currently in the Data Layer (e.g. written while this app was not running). Returns how many were applied. */
    suspend fun pullAll(): Int

    /**
     * Merges items delivered by the listener service. Items written by this device itself (the Data Layer
     * delivers every change to all nodes, the writer included) are skipped. Returns how many were applied.
     */
    suspend fun receive(items: List<RemoteItem>): Int

    /** Re-pushes pending local writes and refreshes [status] (the "Couldn't save · Tap to retry" action). */
    suspend fun retry()
}

/** Sync that does nothing: tests, previews and builds without a peer. Writes stay local. */
object NoopEntrySync : EntrySync {
    private val state = MutableStateFlow<SyncStatus>(SyncStatus.Unavailable)
    override val status: StateFlow<SyncStatus> = state.asStateFlow()
    override fun attach(sink: SyncSink) = Unit
    override suspend fun push(entry: Entry): Boolean = false
    override suspend fun pushSettings(settings: Settings): Boolean = false
    override suspend fun pullAll(): Int = 0
    override suspend fun receive(items: List<RemoteItem>): Int = 0
    override suspend fun retry() = Unit
}

/** Data Layer paths and the capability both apps advertise. */
object SyncPaths {
    const val ENTRIES = "/entries"
    const val ENTRY_PREFIX = "/entries/"
    const val SETTINGS = "/settings"

    /** Declared in core's `res/values/wear.xml`, so both apps advertise it. */
    const val CAPABILITY = "pooparazzi_tracker"

    fun entry(id: String): String = ENTRY_PREFIX + id

    /** The entry id in an `/entries/<id>` path, or null for any other path. */
    fun entryId(path: String): String? {
        if (!path.startsWith(ENTRY_PREFIX)) return null
        val id = path.substring(ENTRY_PREFIX.length)
        return id.takeIf { it.isNotBlank() && '/' !in it }
    }
}
