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
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
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

        val feed = Core.repository.logFeed(FeedType.FORMULA, SampleData.NOW_1432)
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
                    Entry.feed(FeedType.FORMULA, SampleData.at(11, 0), id = "g"),
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
}
