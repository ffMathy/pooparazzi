package com.ffmathy.pooparazzi.core

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.data.SystemClock
import com.ffmathy.pooparazzi.core.data.PooparazziDatabase
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.settings.DataStoreSettingsRepository
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.core.sync.DataLayerEntrySync
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.core.sync.SyncSink
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The object graph shared by the phone and watch apps.
 *
 * @property scope application-lifetime scope for background sync work.
 */
class CoreGraph(
    val clock: Clock,
    val repository: EntryRepository,
    val settings: SettingsRepository,
    val sync: EntrySync,
    val scope: CoroutineScope,
    private val database: PooparazziDatabase? = null,
) {
    /** Startup work: purge old soft deletes, merge what arrived while the app was not running, push pending writes. */
    fun start() {
        scope.launch {
            runCatching { repository.purgeDeleted() }.onFailure { Log.w(TAG, "Purge failed", it) }
            sync.pullAll()
            sync.retry()
        }
    }

    internal fun close() {
        scope.cancel()
        database?.close()
    }

    companion object {
        private const val TAG = "Core"

        /** The production graph: Room database, DataStore settings and Wearable Data Layer sync. */
        fun create(context: Context, clock: Clock = SystemClock): CoreGraph {
            val app = context.applicationContext
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w(TAG, "Background work failed", e) },
            )
            val database = PooparazziDatabase.build(app)
            val sync = DataLayerEntrySync(app, clock)
            val repository = RoomEntryRepository(database, clock, sync, scope)
            val settings = DataStoreSettingsRepository(app.settingsDataStore, clock, sync, scope)
            sync.attach(CoreSyncSink(repository, settings))
            return CoreGraph(clock, repository, settings, sync, scope, database)
        }
    }
}

/**
 * Tiny service locator. Call [init] from `Application.onCreate()` in both apps, then use
 * [repository], [settings], [sync] and [clock] (or [graph]) anywhere. The sync listener service
 * initialises Core itself when it runs before the app's UI.
 */
object Core {
    @Volatile
    private var current: CoreGraph? = null

    /** Builds the graph and starts the startup sync, once per process. Safe to call more than once. */
    fun init(context: Context, clock: Clock = SystemClock): CoreGraph =
        current ?: synchronized(this) {
            current ?: CoreGraph.create(context, clock).also {
                current = it
                it.start()
            }
        }

    /** Like [init] with the system clock; used by the sync listener service. */
    fun ensureInitialized(context: Context): CoreGraph = current ?: init(context)

    val isInitialized: Boolean get() = current != null

    val graph: CoreGraph
        get() = checkNotNull(current) { "Core is not initialised: call Core.init(context) in Application.onCreate()" }

    val repository: EntryRepository get() = graph.repository
    val settings: SettingsRepository get() = graph.settings
    val sync: EntrySync get() = graph.sync
    val clock: Clock get() = graph.clock

    /** Replaces the graph (e.g. with fakes and a fixed clock) for tests and screenshots. Does not call [CoreGraph.start]. */
    @VisibleForTesting
    fun install(graph: CoreGraph) {
        synchronized(this) {
            current?.takeIf { it !== graph }?.close()
            current = graph
        }
    }

    /** Drops the graph, cancelling its work and closing its database. */
    @VisibleForTesting
    fun reset() {
        synchronized(this) {
            current?.close()
            current = null
        }
    }
}

/** One DataStore per process for the settings file. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pooparazzi_settings")

private class CoreSyncSink(
    private val repository: RoomEntryRepository,
    private val settings: SettingsRepository,
) : SyncSink {
    override suspend fun applyRemoteEntry(entry: Entry): Boolean = repository.applyRemote(entry)
    override suspend fun applyRemoteEntries(entries: List<Entry>): Int = repository.applyRemoteAll(entries)
    override suspend fun applyRemoteSettings(settings: Settings): Boolean = this.settings.applyRemote(settings)
    override suspend fun pushPendingEntries(): Boolean = repository.pushPending()
    override suspend fun localSettings(): Settings? = settings.current().takeIf { it.updatedAtMillis > 0 }
}
