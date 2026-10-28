package com.ffmathy.pooparazzi.core.data

import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.core.sync.RemoteItem
import com.ffmathy.pooparazzi.core.sync.SyncSink
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phone and watch as two repositories with their own databases, joined by a fake Data Layer whose delivery
 * order the test chooses. Each device's node holds the last pushed version of every entry it wrote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TwoDeviceSyncTest {
    private val clock = FixedClock(SampleData.at(12, 0))
    private val minute = 60_000L
    private val phone = Device("phone")
    private val watch = Device("watch")

    @After
    fun tearDown() {
        phone.close()
        watch.close()
    }

    private inner class Device(val name: String) {
        val db = PooparazziDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val node = NodeSync()
        val repo = RoomEntryRepository(db, clock, node)

        suspend fun running(): List<String> = repo.observeEntries().first().filter { it.isRunning }.map { it.id }
        suspend fun visible(): List<Entry> = repo.observeEntries().first()
        fun close() = db.close()
    }

    /** This node's Data Layer items: the last version of each entry it pushed. */
    private class NodeSync : EntrySync {
        val items = linkedMapOf<String, Entry>()
        override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Synced(0))
        override fun attach(sink: SyncSink) = Unit
        override suspend fun push(entry: Entry): Boolean {
            items[entry.id] = entry
            return true
        }
        override suspend fun pushSettings(settings: Settings): Boolean = true
        override suspend fun pullAll(): Int = 0
        override suspend fun receive(items: List<RemoteItem>): Int = 0
        override suspend fun retry() = Unit
    }

    /** Delivers [from]'s items to [to] one at a time, in the given id order (worst case: separate deliveries). */
    private suspend fun deliverOneByOne(from: Device, to: Device, vararg ids: String) {
        for (id in ids) to.repo.applyRemote(from.node.items.getValue(id))
    }

    /** Delivers everything [from] holds to [to] as one batch. */
    private suspend fun deliverAll(from: Device, to: Device) = to.repo.applyRemoteAll(from.node.items.values.toList())

    /** Exchanges items both ways until nothing changes any more. */
    private suspend fun settle() {
        repeat(10) {
            val changed = deliverAll(phone, watch) + deliverAll(watch, phone)
            if (changed == 0) return
        }
        error("Devices did not converge")
    }

    private suspend fun assertConverged() {
        val onPhone = phone.visible()
        assertEquals(onPhone, watch.visible())
    }

    // ---- Running sleeps (finding: order-dependent duplicate resolution) ---------------------------

    @Test
    fun aStaleRunningSleepNeverDeletesTheWatchsNewSleep_newSleepArrivesFirst() = runBlocking {
        val s1 = phone.repo.startSleep(clock.now()).getOrThrow() // 12:00 on the phone
        deliverAll(phone, watch)
        clock.set(SampleData.at(13, 0))
        watch.repo.endSleep(s1.id, clock.now())
        clock.set(SampleData.at(14, 0))
        val s2 = watch.repo.startSleep(clock.now()).getOrThrow()

        clock.set(SampleData.at(14, 5))
        deliverOneByOne(watch, phone, s2.id, s1.id) // the new sleep first, then the end of the old one

        assertEquals(listOf(s2.id), phone.running())
        assertFalse(phone.repo.get(s2.id)!!.deleted)
        assertFalse("nothing about the watch's sleep is pushed back", phone.node.items.containsKey(s2.id))
        settle()
        assertEquals(listOf(s2.id), watch.running())
        assertConverged()
    }

    @Test
    fun aStaleRunningSleepNeverDeletesTheWatchsNewSleep_endArrivesFirst() = runBlocking {
        val s1 = phone.repo.startSleep(clock.now()).getOrThrow()
        deliverAll(phone, watch)
        clock.set(SampleData.at(13, 0))
        watch.repo.endSleep(s1.id, clock.now())
        clock.set(SampleData.at(14, 0))
        val s2 = watch.repo.startSleep(clock.now()).getOrThrow()

        clock.set(SampleData.at(14, 5))
        deliverOneByOne(watch, phone, s1.id, s2.id)

        assertEquals(listOf(s2.id), phone.running())
        settle()
        assertEquals(listOf(s2.id), watch.running())
        assertConverged()
    }

    @Test
    fun sleepsStartedWhileApartConvergeOnTheEarliestInEitherOrder() = runBlocking {
        for (phoneFirst in listOf(true, false)) {
            val p = Device("phone")
            val w = Device("watch")
            try {
                clock.set(SampleData.at(10, 0))
                val onPhone = p.repo.startSleep(clock.now()).getOrThrow()
                clock.set(SampleData.at(10, 2))
                w.repo.startSleep(clock.now()).getOrThrow()
                clock.set(SampleData.at(10, 30))

                if (phoneFirst) w.repo.applyRemoteAll(p.node.items.values.toList()) else p.repo.applyRemoteAll(w.node.items.values.toList())
                repeat(4) {
                    p.repo.applyRemoteAll(w.node.items.values.toList())
                    w.repo.applyRemoteAll(p.node.items.values.toList())
                }

                assertEquals(listOf(onPhone.id), p.running())
                assertEquals(listOf(onPhone.id), w.running())
                assertEquals(p.visible(), w.visible())
            } finally {
                p.close()
                w.close()
            }
        }
    }

    @Test
    fun theSameBreastfeedLoggedOnBothDevicesWhileApartIsOneEntry() = runBlocking {
        clock.set(SampleData.at(14, 32))
        watch.repo.logFeed(FeedType.BREAST, clock.now(), BreastSide.LEFT)
        phone.repo.logFeed(FeedType.BREAST, clock.now() - 2_000, BreastSide.LEFT_THEN_RIGHT) // 2 s earlier: it wins
        clock.set(SampleData.at(14, 40))
        settle()
        val feeds = phone.visible().filter { it.feedType == FeedType.BREAST }
        assertEquals(1, feeds.size)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, feeds.single().breastSide)
        assertTrue(phone.running().isEmpty())
        assertConverged()
    }

    // ---- Cross-device dedupe (DESIGN_SPEC §4.4: "from any surface") -------------------------------

    @Test
    fun theSameLogOnPhoneAndWatchWithin5sIsOneEntry() = runBlocking {
        clock.set(SampleData.NOW_1432)
        phone.repo.logDiaper(clock.now(), pee = true, poop = false)
        watch.repo.logDiaper(clock.now() + 2_000, pee = true, poop = false)
        phone.repo.logFeed(FeedType.FORMULA, clock.now() - 3_000)
        watch.repo.logFeed(FeedType.FORMULA, clock.now())
        clock.advanceBy(10_000)
        settle()

        val today = summarize(phone.visible(), clock.now(), SampleData.ZONE).today
        assertEquals(1, today.pee)
        assertEquals(1, today.feeds)
        assertConverged()
    }

    @Test
    fun differentLogsWithin5sAreKept() = runBlocking {
        clock.set(SampleData.NOW_1432)
        phone.repo.logDiaper(clock.now(), pee = true, poop = false)
        watch.repo.logDiaper(clock.now() + 2_000, pee = true, poop = true)
        phone.repo.logFeed(FeedType.FORMULA, clock.now())
        watch.repo.logFeed(FeedType.FOOD, clock.now())
        watch.repo.logDiaper(clock.now() - 6 * minute, pee = true, poop = false)
        clock.advanceBy(10_000)
        settle()
        assertEquals(5, phone.visible().size)
        assertConverged()
    }

    // ---- Purge (finding: forget() left a late device diverged) -----------------------------------

    @Test
    fun aDeviceAwayForTheWholeRetentionStillLearnsOfADeletion() = runBlocking {
        clock.set(SampleData.NOW_1432)
        val pee = phone.repo.logDiaper(clock.now(), pee = true, poop = false)
        deliverAll(phone, watch)
        phone.repo.delete(pee.id)
        // The watch is off for a week.
        clock.advanceBy(EntryRules.SOFT_DELETE_RETENTION_MILLIS + minute)
        assertEquals(1, phone.repo.purgeDeleted())

        settle()
        assertTrue(phone.visible().isEmpty())
        assertTrue(watch.visible().isEmpty())
    }

    // ---- Tie-break (finding: equal stamps swapped versions) --------------------------------------

    @Test
    fun equalStampsWithDifferentContentConverge() = runBlocking {
        clock.set(SampleData.NOW_1432)
        val feed = phone.repo.logFeed(FeedType.FOOD, clock.now() - 10 * minute)
        deliverAll(phone, watch)
        // Both devices edit the same entry in the same millisecond.
        phone.repo.update(feed.copy(startMillis = clock.now() - 20 * minute))
        watch.repo.update(feed.copy(startMillis = clock.now() - 30 * minute))
        assertEquals(phone.repo.get(feed.id)!!.updatedAtMillis, watch.repo.get(feed.id)!!.updatedAtMillis)
        settle()
        assertConverged()
    }

    // ---- Pushes (finding: an older push could land last and leave stale data) --------------------

    @Test
    fun aSlowOlderPushCannotOverwriteANewerVersion() = runBlocking {
        val db = PooparazziDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val gate = CompletableDeferred<Unit>()
        val sync = RacingSync(gate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RoomEntryRepository(db, clock, sync, scope)
            clock.set(SampleData.NOW_1432)
            val sleep = repo.startSleep(clock.now()).getOrThrow() // push #1 hangs (a cold Play Services bind)
            sync.firstPushStarted.await()
            clock.advanceBy(1)
            repo.delete(sleep.id) // push #2 must not overtake #1 with a version #1 then overwrites
            // Give an unserialized push #2 every chance to land first (with the fix it waits for #1 instead).
            withTimeoutOrNull(500) { sync.laterPushDone.await() }
            gate.complete(Unit)
            withTimeout(10_000) { scope.coroutineContext[Job]!!.children.forEach { it.join() } }

            assertTrue("the Data Layer holds the newest version", sync.items.getValue(sleep.id).deleted)
            assertEquals(repo.get(sleep.id), sync.items.getValue(sleep.id))
            assertTrue(repo.pendingSync().isEmpty())
        } finally {
            scope.cancel()
            db.close()
        }
    }

    @Test
    fun manyWritesFromManyCoroutinesLeaveTheLatestVersionInTheDataLayer() = runBlocking {
        val db = PooparazziDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val sync = RacingSync(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RoomEntryRepository(db, clock, sync, scope)
            clock.set(SampleData.NOW_1432)
            val feed = repo.logFeed(FeedType.FOOD, clock.now() - 30 * minute)
            for (i in 1..20) {
                clock.advanceBy(1)
                repo.update(feed.copy(startMillis = clock.now() - i * minute))
            }
            withTimeout(10_000) { scope.coroutineContext[Job]!!.children.forEach { it.join() } }
            assertEquals(repo.get(feed.id), sync.items.getValue(feed.id))
            assertTrue(repo.pendingSync().isEmpty())
        } finally {
            scope.cancel()
            db.close()
        }
    }

    /** A sync whose first push waits for [gate] (when given) and whose pushes take a little time. */
    private class RacingSync(private val gate: CompletableDeferred<Unit>?) : EntrySync {
        val items = java.util.concurrent.ConcurrentHashMap<String, Entry>()
        val firstPushStarted = CompletableDeferred<Unit>()
        val laterPushDone = CompletableDeferred<Unit>()
        private val calls = java.util.concurrent.atomic.AtomicInteger()
        override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Synced(0))
        override fun attach(sink: SyncSink) = Unit
        override suspend fun push(entry: Entry): Boolean {
            val first = calls.getAndIncrement() == 0
            if (first) {
                firstPushStarted.complete(Unit)
                gate?.await()
            }
            Thread.sleep(1)
            items[entry.id] = entry
            if (!first) laterPushDone.complete(Unit)
            return true
        }
        override suspend fun pushSettings(settings: Settings): Boolean = true
        override suspend fun pullAll(): Int = 0
        override suspend fun receive(items: List<RemoteItem>): Int = 0
        override suspend fun retry() = Unit
    }

    // ---- Guards under concurrency ----------------------------------------------------------------

    @Test
    fun parallelStartsCreateExactlyOneRunningSleep() = runBlocking {
        clock.set(SampleData.NOW_1432)
        val results = (1..16).map { async(Dispatchers.IO) { phone.repo.startSleep(clock.now()) } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, phone.running().size)
    }

    @Test
    fun aLocalStartRacingARemoteStartLeavesOneRunningSleepAfterSync() = runBlocking {
        repeat(20) { round ->
            val p = Device("phone")
            val w = Device("watch")
            try {
                clock.set(SampleData.NOW_1432 + round * minute)
                // The watch started a sleep a minute ago; it reaches the phone just as the phone's own start runs.
                val remote = Entry.sleep(clock.now() - minute, id = "w-$round", updatedAtMillis = clock.now())
                w.db.entryDao().write(EntryEntity.from(remote, pendingSync = false, createdLocally = true))
                listOf(
                    async(Dispatchers.IO) { p.repo.startSleep(clock.now()) },
                    async(Dispatchers.IO) { p.repo.applyRemote(remote) },
                ).awaitAll()
                repeat(4) {
                    p.repo.applyRemoteAll(w.node.items.values.toList())
                    w.repo.applyRemoteAll(p.node.items.values.toList())
                }
                assertEquals(1, p.running().size)
                assertEquals(p.running(), w.running())
            } finally {
                p.close()
                w.close()
            }
        }
    }
}
