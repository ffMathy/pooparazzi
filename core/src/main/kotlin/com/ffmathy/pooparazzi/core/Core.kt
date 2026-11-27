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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The object graph shared by the phone and watch apps.
 *
 * @property scope application-lifetime scope for background sync work.
 * @property cloud the backup of the log and the babies to the account's family: [NoopCloudSync] on the watch and in
 *   builds without a backend.
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
     * Runs after the sync listener merged something from the other device, before the listener returns. Play Services
     * lets the process go as soon as `onDataChanged` returns, and a cached process is soon frozen: work left to a flow
     * collector (a new tile, the Ongoing Activity) could then wait until the user opens the app. The watch sets this
     * to bring its surfaces up to date at once.
     */
    @Volatile
    var afterRemoteMerge: (suspend () -> Unit)? = null

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

    /** When [catchUpNow] last merged the Data Layer ([clock]). */
    private val lastCatchUpPull = AtomicLong(Long.MIN_VALUE / 2)

    /**
     * Catches up with the other device in the background ([catchUpNow]): the app coming to the foreground.
     */
    fun catchUp() {
        scope.launch { catchUpNow() }
    }

    /**
     * Merges what the Data Layer holds from the other device and pushes this device's writes that have not reached it
     * yet. [start] does this once per process, but a process can live for days: a change the sync listener was not
     * woken for (or whose merge timed out), and a write whose push failed or never ran (the process frozen right after
     * a tap on a tile, widget or notification), would otherwise wait for the next process. The merge runs at most once
     * every [CATCH_UP_PULL_INTERVAL_MILLIS] unless [force]d; the pending pushes every time (cheap when there are none).
     */
    suspend fun catchUpNow(force: Boolean = false) {
        val now = clock.now()
        val last = lastCatchUpPull.get()
        val due = force || now - last !in 0 until CATCH_UP_PULL_INTERVAL_MILLIS
        if (due && lastCatchUpPull.compareAndSet(last, now)) sync.pullAll()
        pushPendingWrites()
    }

    /**
     * Waits (at most [timeoutMillis]) until this device's local writes have been handed to the Data Layer. For entry
     * points whose process may be frozen or killed as soon as they return (a widget, notification or tile action): a
     * push still left to the background would then wait for the next process. Stopping to wait does not stop the push.
     * Returns true when everything was pushed in time.
     */
    suspend fun awaitPendingWrites(timeoutMillis: Long = PENDING_WRITES_TIMEOUT_MILLIS): Boolean {
        val push = scope.async { pushPendingWrites() }
        // Timed on a background dispatcher: the caller may be on the main thread, which must not hold the timer up.
        return withContext(Dispatchers.Default) { withTimeoutOrNull(timeoutMillis) { push.await() } } ?: false
    }

    /** Pushes every local write the Data Layer has not accepted yet; true when all were. Never throws. */
    private suspend fun pushPendingWrites(): Boolean {
        val room = repository as? RoomEntryRepository ?: return true
        return try {
            room.pushPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Pending pushes failed", e)
            false
        }
    }

    internal fun close() {
        scope.cancel()
        database?.close()
    }

    companion object {
        private const val TAG = "Core"

        /** [catchUpNow] merges the Data Layer at most this often. */
        const val CATCH_UP_PULL_INTERVAL_MILLIS = 60_000L

        /** How long [awaitPendingWrites] waits by default. */
        const val PENDING_WRITES_TIMEOUT_MILLIS = 5_000L

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
            // Flagged for the backend only while an account is signed in (BABIES_AND_BACKUP §4.2).
            val cloudBackup: () -> Boolean = cloud?.account?.let { account -> { account.value != null } } ?: { false }
            val settings = DataStoreSettingsRepository(app.settingsDataStore, clock, sync, scope, cloudBackup)
            val repository = RoomEntryRepository(
                database,
                clock,
                sync,
                scope,
                selectedBaby = settings.settings.map { it.selectedBaby.id },
                cloudBackup = cloudBackup,
            )
            val backup = cloud?.let {
                CloudBackup(repository, settings, it.store, it.account, DataStoreCloudBookkeepingStore(app.cloudDataStore), clock, scope)
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
