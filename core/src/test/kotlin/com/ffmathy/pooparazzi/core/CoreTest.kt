package com.ffmathy.pooparazzi.core

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.cloud.CloudAccount
import com.ffmathy.pooparazzi.core.cloud.CloudBackend
import com.ffmathy.pooparazzi.core.cloud.CloudBackup
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.cloud.FakeCloudStore
import com.ffmathy.pooparazzi.core.cloud.NoopCloudSync
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.PooparazziDatabase
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.DataStoreSettingsRepository
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
import com.ffmathy.pooparazzi.core.sync.SyncSink
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Core works locally without Play Services (Robolectric has none): nothing crashes, logging works. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = Core.reset()

    @Test
    fun initIsIdempotentAndWorksWithoutPlayServices() = runBlocking {
        val clock = FixedClock(SampleData.NOW_1432)
        val graph = Core.init(context, clock)
        assertSame(graph, Core.init(context))
        assertSame(clock, Core.clock)
        assertTrue(Core.isInitialized)

        val feed = Core.repository.logFeed(FeedType.BOTTLE, SampleData.NOW_1432)
        assertEquals(listOf(feed), Core.repository.observeEntries().first())
        Core.settings.setBabyName("Noa")
        assertEquals("Noa", Core.settings.current().babyName)
    }

    @Test
    fun ensureInitializedReusesTheGraph() {
        val graph = Core.init(context)
        assertSame(graph, Core.ensureInitialized(context))
        assertSame("no backend, no backup", NoopCloudSync, Core.cloud)
    }

    @Test
    fun aBackendStartsTheBackup() {
        val graph = Core.init(context, FixedClock(SampleData.NOW_1432), CloudBackend(FakeCloudStore(), MutableStateFlow<CloudAccount?>(null)))
        assertTrue(graph.cloud is CloudBackup)
        assertSame(graph.cloud, Core.cloud)
        assertEquals(CloudStatus.Off, Core.cloud.status.value)
    }

    @Test
    fun thePhoneAdoptsTheBabiesOfItsEntries() = runBlocking {
        val clock = FixedClock(SampleData.NOW_1432)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = PooparazziDatabase.inMemory(context)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb").also { it.delete() } }
            val settings = DataStoreSettingsRepository(store, clock)
            val repo = RoomEntryRepository(db, clock)
            val graph = CoreGraph(clock, repo, settings, NoopEntrySync, scope, db)
            // Restored from the backend: two babies this phone does not know, and the first one's entry.
            repo.applyRemoteAll(
                listOf(
                    Entry.diaper(SampleData.at(10, 0), pee = true, poop = false, id = "d", babyId = "b3"),
                    Entry.feed(FeedType.FOOD, SampleData.at(9, 0), id = "f", babyId = "b2"),
                    Entry.feed(FeedType.BOTTLE, SampleData.at(11, 0), id = "g"),
                ),
            )
            graph.adoptBabiesFromEntries()
            graph.adoptBabiesFromEntries()
            val adopted = withTimeout(5_000) { settings.settings.first { it.babies.size == 3 } }
            assertEquals(listOf(Baby(Baby.FIRST_ID), Baby("b2"), Baby("b3")), adopted.babies)

            // Later arrivals are adopted too, appended unnamed; the current baby stays.
            repo.applyRemote(Entry.sleep(SampleData.at(8, 0), SampleData.at(8, 30), id = "s", babyId = "b4"))
            val more = withTimeout(5_000) { settings.settings.first { it.babies.size == 4 } }
            assertEquals(listOf(Baby.FIRST_ID, "b2", "b3", "b4"), more.babies.map { it.id })
            assertEquals(Baby.FIRST_ID, more.selectedBabyId)
        } finally {
            scope.cancel()
            db.close()
        }
    }

    /** A sync that counts pulls and retries, and lets a test decide whether pushes reach the Data Layer. */
    private class CountingSync(var accept: Boolean) : EntrySync by NoopEntrySync {
        val pulls = AtomicInteger(0)
        val pushes = AtomicInteger(0)
        val items = ConcurrentHashMap<String, Entry>()
        var sink: SyncSink? = null
        override fun attach(sink: SyncSink) { this.sink = sink }
        override suspend fun push(entry: Entry): Boolean {
            pushes.incrementAndGet()
            if (accept) items[entry.id] = entry
            return accept
        }
        override suspend fun pullAll(): Int = 0.also { pulls.incrementAndGet() }
    }

    private fun graphOn(sync: CountingSync, clock: FixedClock, scope: CoroutineScope, db: PooparazziDatabase): CoreGraph {
        val repo = RoomEntryRepository(db, clock, sync, scope)
        sync.attach(object : SyncSink {
            override suspend fun applyRemoteEntry(entry: Entry) = repo.applyRemote(entry)
            override suspend fun applyRemoteSettings(settings: Settings) = false
            override suspend fun applyRemoteLegacySettings(settings: Settings) = false
            override suspend fun pushPendingEntries() = repo.pushPending()
            override suspend fun localSettings(): Settings? = null
        })
        return CoreGraph(clock, repo, FakeSettings, sync, scope, db)
    }

    @Test
    fun aWriteWhosePushFailedReachesTheDataLayerOnTheNextCatchUp() = runBlocking {
        val clock = FixedClock(SampleData.NOW_1432)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = PooparazziDatabase.inMemory(context)
        try {
            val sync = CountingSync(accept = false)
            val graph = graphOn(sync, clock, scope, db)
            val feed = graph.repository.logFeed(FeedType.BOTTLE, SampleData.NOW_1432)
            assertFalse(graph.awaitPendingWrites())
            assertTrue(sync.items.isEmpty())

            // The app comes back to the foreground long after (the process was never restarted).
            sync.accept = true
            graph.catchUpNow()
            assertEquals(feed, sync.items[feed.id])
            assertTrue((graph.repository as RoomEntryRepository).pendingSync().isEmpty())
            assertEquals(1, sync.pulls.get())

            // The merge is throttled; with nothing pending, nothing is pushed again.
            val pushes = sync.pushes.get()
            graph.catchUpNow()
            assertEquals(1, sync.pulls.get())
            assertEquals(pushes, sync.pushes.get())
            clock.advanceBy(CoreGraph.CATCH_UP_PULL_INTERVAL_MILLIS)
            graph.catchUpNow()
            assertEquals(2, sync.pulls.get())
            graph.catchUpNow(force = true)
            assertEquals(3, sync.pulls.get())
        } finally {
            scope.cancel()
            db.close()
        }
    }

    @Test
    fun awaitPendingWritesWaitsForAPushLeftToTheBackground() = runBlocking {
        val clock = FixedClock(SampleData.NOW_1432)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = PooparazziDatabase.inMemory(context)
        try {
            val sync = CountingSync(accept = true)
            val graph = graphOn(sync, clock, scope, db)
            // The write's own push is launched in the background (a widget, notification or tile tap).
            val sleep = graph.repository.startSleep(SampleData.NOW_1432).getOrThrow()
            assertTrue(graph.awaitPendingWrites())
            assertEquals(sleep, sync.items[sleep.id])
            assertTrue((graph.repository as RoomEntryRepository).pendingSync().isEmpty())
        } finally {
            scope.cancel()
            db.close()
        }
    }

    private object FakeSettings : SettingsRepository by NoSettings

    private object NoSettings : SettingsRepository {
        override val settings: Flow<Settings> = MutableStateFlow(Settings())
        override suspend fun setBabyName(name: String) = Settings()
        override suspend fun renameBaby(id: String, name: String) = Settings()
        override suspend fun setBabyBirthDate(id: String, date: java.time.LocalDate?) = Settings()
        override suspend fun setWatchForecast(kind: com.ffmathy.pooparazzi.core.domain.ForecastKind, shown: Boolean) = Settings()
        override suspend fun addBaby() = Baby(Baby.FIRST_ID)
        override suspend fun removeBaby(id: String) = Settings()
        override suspend fun selectBaby(id: String) = Settings()
        override suspend fun ensureBabies(ids: Collection<String>) = Settings()
        override suspend fun importBabies(babies: Collection<Baby>) = Settings()
        override suspend fun setAppearance(appearance: com.ffmathy.pooparazzi.core.settings.Appearance) = Settings()
        override suspend fun setLanguage(language: com.ffmathy.pooparazzi.core.settings.AppLanguage) = Settings()
        override suspend fun applyRemote(remote: Settings) = false
        override suspend fun applyLegacyRemote(remote: Settings) = false
    }
}
