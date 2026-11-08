package com.ffmathy.pooparazzi.wear.sync

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.CoreGraph
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.PooparazziDatabase
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
import com.ffmathy.pooparazzi.wear.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** The watch merges what its Data Layer holds when the tile is drawn (throttled) and from the periodic worker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchCatchUpTest {
    private val pulls = AtomicInteger(0)
    private val sync = object : EntrySync by NoopEntrySync {
        override suspend fun pullAll(): Int = 0.also { pulls.incrementAndGet() }
    }
    private val clock = FixedClock(SampleData.NOW_1432)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: PooparazziDatabase
    private lateinit var graph: CoreGraph

    @Before
    fun setUp() {
        WatchCatchUp.resetForTest()
        db = PooparazziDatabase.inMemory(ApplicationProvider.getApplicationContext())
        graph = CoreGraph(clock, RoomEntryRepository(db, clock), FakeSettingsRepository(), sync, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        WatchCatchUp.resetForTest()
        // The worker's pull uses the app's graph.
        Core.reset()
    }

    private fun awaitPulls(count: Int) = runBlocking {
        withTimeout(5_000) { while (pulls.get() < count) delay(10) }
    }

    @Test
    fun `a tile request pulls at most once a minute`() {
        val now = SampleData.NOW_1432
        assertTrue(WatchCatchUp.onTileRequest(graph, now))
        awaitPulls(1)
        assertFalse(WatchCatchUp.onTileRequest(graph, now + 10_000))
        assertFalse(WatchCatchUp.onTileRequest(graph, now + WatchCatchUp.TILE_PULL_INTERVAL_MILLIS - 1))
        assertTrue(WatchCatchUp.onTileRequest(graph, now + WatchCatchUp.TILE_PULL_INTERVAL_MILLIS))
        awaitPulls(2)
        assertEquals(2, pulls.get())
    }

    @Test
    fun `a clock set back does not stop the pulls`() {
        val now = SampleData.NOW_1432
        assertTrue(WatchCatchUp.onTileRequest(graph, now))
        assertTrue(WatchCatchUp.onTileRequest(graph, now - 3_600_000))
    }

    @Test
    fun `the periodic worker succeeds`() {
        val worker = TestListenableWorkerBuilder<WatchCatchUp.CatchUpWorker>(ApplicationProvider.getApplicationContext()).build()
        // Off the main thread, as WorkManager runs it: the surfaces are drawn on the main looper, idled here.
        val result = scope.async { worker.doWork() }
        runBlocking {
            withTimeout(30_000) {
                while (!result.isCompleted) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(10)
                }
            }
        }
        assertEquals(ListenableWorker.Result.success(), runBlocking { result.await() })
    }
}
