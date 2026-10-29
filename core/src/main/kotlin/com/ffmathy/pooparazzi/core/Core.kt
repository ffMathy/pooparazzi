package com.ffmathy.pooparazzi.core

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.ffmathy.pooparazzi.core.cloud.CloudBackend
import com.ffmathy.pooparazzi.core.cloud.CloudBackup
import com.ffmathy.pooparazzi.core.cloud.CloudSync
import com.ffmathy.pooparazzi.core.cloud.DataStoreCloudBookkeepingStore
import com.ffmathy.pooparazzi.core.cloud.NoopCloudSync
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The object graph shared by the phone and watch apps.
 *
 * @property scope application-lifetime scope for background sync work.
 * @property cloud the backup to the account's backend: [NoopCloudSync] on the watch and in builds without one.
 */
class CoreGraph(
    val clock: Clock,
    val repository: EntryRepository,
    val settings: SettingsRepository,
    val sync: EntrySync,
    val scope: CoroutineScope,
    private val database: PooparazziDatabase? = null,
    val cloud: CloudSync = NoopCloudSync,
) {
    private val adopting = AtomicBoolean(false)

    /**
     * Startup work: purge old soft deletes, merge what arrived while the app was not running, push pending writes;
     * then start the backup.
     */
    fun start() {
        scope.launch {
            runCatching { repository.purgeDeleted() }.onFailure { Log.w(TAG, "Purge failed", it) }
            sync.pullAll()
            sync.retry()
        }
        cloud.start()
    }

    /**
     * Keeps a baby in the settings for every baby id among the stored entries (BABIES_AND_BACKUP §2.1): entries that
     * arrive from the backend, an Android restore or the other device add unnamed babies, in the order of their first
     * entry. The phone calls this once after [Core.init]; the watch never does (it learns the babies from the phone's
     * settings). Calling it again does nothing; a graph whose repository is not on Room has nothing to adopt from.
     */
    fun adoptBabiesFromEntries() {
        val room = repository as? RoomEntryRepository ?: return
        if (!adopting.compareAndSet(false, true)) return
        scope.launch {
            room.observeBabyIds().collect { ids ->
                runCatching { settings.ensureBabies(ids) }.onFailure { Log.w(TAG, "Could not adopt babies", it) }
            }
        }
    }

    internal fun close() {
        scope.cancel()
        database?.close()
    }

    companion object {
        private const val TAG = "Core"

        /**
         * The production graph: Room database, DataStore settings and Wearable Data Layer sync, plus the backup when
         * [cloud] is given (the phone app with a backend).
         */
        fun create(context: Context, clock: Clock = SystemClock, cloud: CloudBackend? = null): CoreGraph {
            val app = context.applicationContext
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w(TAG, "Background work failed", e) },
            )
            val database = PooparazziDatabase.build(app)
            val sync = DataLayerEntrySync(app, clock)
            val settings = DataStoreSettingsRepository(app.settingsDataStore, clock, sync, scope)
            val repository = RoomEntryRepository(
                database,
                clock,
                sync,
                scope,
                selectedBaby = settings.settings.map { it.selectedBaby.id },
                // Flagged for the backend only while an account is signed in (BABIES_AND_BACKUP §4.2).
                cloudBackup = cloud?.account?.let { account -> { account.value != null } } ?: { false },
            )
            val backup = cloud?.let {
                CloudBackup(repository, it.store, it.account, DataStoreCloudBookkeepingStore(app.cloudDataStore), clock, scope)
            }
            sync.attach(CoreSyncSink(repository, settings))
            return CoreGraph(clock, repository, settings, sync, scope, database, backup ?: NoopCloudSync)
        }
    }
}

/**
 * Tiny service locator. Call [init] from `Application.onCreate()` in both apps, then use
 * [repository], [settings], [sync], [cloud] and [clock] (or [graph]) anywhere. The sync listener service
 * initialises Core itself when it runs before the app's UI.
 */
object Core {
    @Volatile
    private var current: CoreGraph? = null

    /**
     * Builds the graph and starts the startup sync, once per process. Safe to call more than once (later calls return
     * the graph as it is). The phone app passes its [cloud] backend, when the build has one.
     */
    fun init(context: Context, clock: Clock = SystemClock, cloud: CloudBackend? = null): CoreGraph =
        current ?: synchronized(this) {
            current ?: CoreGraph.create(context, clock, cloud).also {
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
    val cloud: CloudSync get() = graph.cloud
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

/** One DataStore per process for the backup's bookkeeping (the phone with a backend only). */
private val Context.cloudDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pooparazzi_cloud",
    // Losing the bookkeeping is safe (a full pull and an upload of the differences); a file that can never be
    // written again would stop every sync.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private class CoreSyncSink(
    private val repository: RoomEntryRepository,
    private val settings: SettingsRepository,
) : SyncSink {
    override suspend fun applyRemoteEntry(entry: Entry): Boolean = repository.applyRemote(entry)
    override suspend fun applyRemoteEntries(entries: List<Entry>): Int = repository.applyRemoteAll(entries)
    override suspend fun applyRemoteSettings(settings: Settings): Boolean = this.settings.applyRemote(settings)
    override suspend fun applyRemoteLegacySettings(settings: Settings): Boolean = this.settings.applyLegacyRemote(settings)
    override suspend fun pushPendingEntries(): Boolean = repository.pushPending()
    override suspend fun localSettings(): Settings? = settings.current().takeIf { it.updatedAtMillis > 0 }
}
