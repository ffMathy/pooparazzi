package com.ffmathy.pooparazzi.core.data

import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.model.AlreadyRunningException
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.FakeEntrySync
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The log is kept per baby (BABIES_AND_BACKUP §2.3): the current baby decides what is shown and what is logged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerBabyRepositoryTest {
    private lateinit var db: PooparazziDatabase
    private val clock = FixedClock(SampleData.NOW_1432)
    private val sync = FakeEntrySync()
    private val selected = MutableStateFlow(Baby.FIRST_ID)
    private lateinit var repo: RoomEntryRepository

    private val now get() = clock.now()
    private val minute = 60_000L
    private val twin = "baby-2"

    @Before
    fun setUp() {
        db = PooparazziDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = RoomEntryRepository(db, clock, sync, selectedBaby = selected)
    }

    @After
    fun tearDown() = db.close()

    private fun test(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun runningIds(): List<String> = repo.observeAllEntries().first().filter { it.isRunning }.map { it.id }

    @Test
    fun theCurrentBabysEntriesAreShownAndFollowTheSelection() = runBlocking {
        val noa = repo.logFeed(FeedType.FORMULA, now - 10 * minute)
        selected.value = twin
        val ida = repo.logDiaper(now - 5 * minute, pee = true, poop = false)
        assertEquals(Baby.FIRST_ID, noa.babyId)
        assertEquals(twin, ida.babyId)
        assertEquals(listOf(ida), repo.observeEntries().first())
        assertEquals(listOf(noa, ida), repo.observeAllEntries().first())

        // One collection for the whole process: switching re-emits the other baby's log.
        selected.value = Baby.FIRST_ID
        val emissions = Channel<List<Entry>>(Channel.UNLIMITED)
        val job = launch { repo.observeEntries().collect { emissions.send(it) } }
        try {
            assertEquals(listOf(noa), withTimeout(5_000) { emissions.receive() })
            selected.value = twin
            assertEquals(listOf(ida), withTimeout(5_000) { emissions.receive() })
            val more = repo.logFeed(FeedType.FOOD, now)
            assertEquals(listOf(ida, more), withTimeout(5_000) { emissions.receive() })
            selected.value = Baby.FIRST_ID
            assertEquals(listOf(noa), withTimeout(5_000) { emissions.receive() })
        } finally {
            job.cancel()
        }
    }

    @Test
    fun theDedupeWindowIsPerBaby() = test {
        val noaFeed = repo.logFeedChecked(FeedType.BREAST, now, BreastSide.LEFT)
        val noaPee = repo.logDiaperChecked(now, pee = true, poop = false)
        assertTrue(repo.logFeedChecked(FeedType.BREAST, now - 1_000) is LogResult.Duplicate)
        selected.value = twin
        // Twins changed within seconds of each other are two diapers, and two feeds.
        val idaFeed = repo.logFeedChecked(FeedType.BREAST, now - 1_000, BreastSide.RIGHT)
        val idaPee = repo.logDiaperChecked(now + 2_000, pee = true, poop = false)
        assertTrue(idaFeed is LogResult.Logged)
        assertTrue(idaPee is LogResult.Logged)
        assertEquals(idaPee.entry, repo.logDiaperChecked(now + 1_000, pee = true, poop = false).entry)
        assertEquals(4, repo.observeAllEntries().first().size)
        assertEquals(listOf(idaFeed.entry, idaPee.entry), repo.observeEntries().first())
        assertTrue(noaFeed is LogResult.Logged && noaPee is LogResult.Logged)
    }

    @Test
    fun twinsCanSleepAtTheSameTimeButOneBabyOnlyOnce() = test {
        val noa = repo.startSleep(now - 20 * minute).getOrThrow()
        selected.value = twin
        val ida = repo.startSleep(now - 10 * minute).getOrThrow()
        assertEquals(twin, ida.babyId)
        val again = repo.startSleep(now).exceptionOrNull() as AlreadyRunningException
        assertEquals(ida, again.running)
        assertEquals(listOf(noa.id, ida.id), runningIds())
        // Ending works by id, on any baby.
        assertTrue(repo.endSleep(noa.id, now) is EndResult.Ended)
        assertEquals(listOf(ida.id), runningIds())
    }

    @Test
    fun addIsForTheCurrentBaby() = test {
        repo.startSleep(now - 30 * minute).getOrThrow()
        selected.value = twin
        val added = repo.add(Entry.sleep(now - 20 * minute, babyId = "somebody-else"))
        assertEquals(twin, added.babyId)
        assertTrue("Noa's sleep does not block Ida's", added.isRunning)
        assertEquals(added, repo.get(added.id))
        assertThrows(AlreadyRunningException::class.java) { runBlocking { repo.add(Entry.sleep(now - 10 * minute)) } }
    }

    @Test
    fun anEditNeverMovesAnEntryToAnotherBaby() = test {
        val feed = repo.logFeed(FeedType.FORMULA, now - 10 * minute)
        val old = repo.startSleep(now - 60 * minute).getOrThrow()
        repo.endSleep(old.id, now - 40 * minute)
        val running = repo.startSleep(now - 30 * minute).getOrThrow()

        selected.value = twin
        val moved = repo.update(feed.copy(startMillis = now - 12 * minute, babyId = twin))
        assertEquals(Baby.FIRST_ID, moved.babyId)
        assertEquals(Baby.FIRST_ID, repo.get(feed.id)!!.babyId)

        // Reopening Noa's old sleep conflicts with Noa's running sleep, not with Ida's.
        repo.startSleep(now - 5 * minute).getOrThrow()
        val conflict = assertThrows(AlreadyRunningException::class.java) {
            runBlocking { repo.update(repo.get(old.id)!!.copy(endMillis = null)) }
        }
        assertEquals(running.id, conflict.running.id)
        repo.delete(running.id)
        assertTrue(repo.update(repo.get(old.id)!!.copy(endMillis = null)).isRunning)
    }

    @Test
    fun aMergeNeverMovesAnEntryToAnotherBaby() = test {
        // An older app on the other device drops the unknown babyId: its versions read as the first baby's.
        selected.value = twin
        val sleep = repo.startSleep(now - 30 * minute).getOrThrow()
        val feed = repo.logFeed(FeedType.FORMULA, now - 20 * minute)
        assertEquals(twin, sleep.babyId)

        val ended = sleep.copy(endMillis = now - 5 * minute, updatedAtMillis = now + 1, babyId = Baby.FIRST_ID)
        assertEquals(1, repo.applyRemoteAll(listOf(ended)))
        assertEquals(ended.copy(babyId = twin), repo.get(sleep.id))

        val fromCloud = feed.copy(startMillis = now - 21 * minute, updatedAtMillis = now + 2, babyId = Baby.FIRST_ID)
        assertEquals(1, repo.applyCloudEntries(listOf(fromCloud)))
        assertEquals(fromCloud.copy(babyId = twin), repo.get(feed.id))

        // The same version again, without its baby, is identical: nothing is written.
        assertEquals(0, repo.applyRemoteAll(listOf(ended)))
        assertEquals(setOf(sleep.id, feed.id), repo.observeEntries().first().map { it.id }.toSet())
        selected.value = Baby.FIRST_ID
        assertTrue(repo.observeEntries().first().isEmpty())
    }

    @Test
    fun undoRestoresAnEntryToItsOwnBaby() = test {
        val sleep = repo.startSleep(now - 20 * minute).getOrThrow()
        val snapshot = repo.delete(sleep.id)!!
        selected.value = twin
        repo.startSleep(now - 10 * minute).getOrThrow()
        val restored = repo.restore(snapshot)
        assertEquals(Baby.FIRST_ID, restored.babyId)
        assertTrue(restored.isRunning)
        assertEquals(2, runningIds().size)

        // A restore that would give Noa a second running sleep is refused.
        val ended = repo.endSleep(sleep.id, now - 5 * minute) as EndResult.Ended
        selected.value = Baby.FIRST_ID
        repo.startSleep(now - minute).getOrThrow()
        assertThrows(AlreadyRunningException::class.java) { runBlocking { repo.restore(ended.previous) } }
    }

    @Test
    fun crossDeviceConflictsAreResolvedPerBaby() = test {
        val noaSleep = repo.startSleep(now - 7 * minute).getOrThrow()
        val noaPee = repo.logDiaper(now - 3 * minute, pee = true, poop = false)
        sync.pushed.clear()

        // The watch logged for Ida meanwhile: an earlier sleep and the same diaper a second later. Nothing conflicts.
        val idaSleep = Entry.sleep(now - 9 * minute, id = "w-sleep", updatedAtMillis = now, babyId = twin)
        val idaPee = Entry.diaper(now - 3 * minute + 1_000, pee = true, poop = false, id = "w-pee", updatedAtMillis = now, babyId = twin)
        assertEquals(2, repo.applyRemoteAll(listOf(idaSleep, idaPee)))
        assertEquals(listOf(idaSleep.id, noaSleep.id), runningIds())
        assertFalse(repo.get(noaPee.id)!!.deleted)
        assertTrue(sync.pushed.isEmpty())

        // An earlier sleep and the same diaper for Noa: ours lose, are deleted and pushed.
        val earlier = Entry.sleep(now - 8 * minute, id = "w-noa-sleep", updatedAtMillis = now)
        val samePee = Entry.diaper(now - 3 * minute - 1_000, pee = true, poop = false, id = "w-noa-pee", updatedAtMillis = now)
        assertEquals(2, repo.applyRemoteAll(listOf(earlier, samePee)))
        assertEquals(listOf(idaSleep.id, earlier.id), runningIds())
        assertTrue(repo.get(noaSleep.id)!!.deleted)
        assertTrue(repo.get(noaPee.id)!!.deleted)
        assertEquals(setOf(noaSleep.id, noaPee.id), sync.pushed.map { it.id }.toSet())
    }

    @Test
    fun babyIdsAreListedInTheOrderOfTheirFirstEntry() = test {
        assertEquals(emptyList<String>(), repo.observeBabyIds().first())
        selected.value = twin
        repo.logFeed(FeedType.FOOD, now - 10 * minute)
        selected.value = Baby.FIRST_ID
        val deleted = repo.logFeed(FeedType.FOOD, now - 60 * minute)
        repo.delete(deleted.id)
        repo.applyRemote(Entry.diaper(now - 30 * minute, pee = true, poop = false, id = "r", babyId = "baby-3"))
        assertEquals(listOf(Baby.FIRST_ID, "baby-3", twin), repo.observeBabyIds().first())
    }

    @Test
    fun aRowWithoutABabyIdReadsAsTheFirstBaby() = test {
        db.entryDao().write(EntryEntity.from(Entry.feed(FeedType.FOOD, now, id = "blank").copy(babyId = " "), pendingSync = false))
        assertEquals(Baby.FIRST_ID, repo.get("blank")!!.babyId)
        // A remote entry with a blank baby id is merged as the first baby's, so it shows up.
        assertTrue(repo.applyRemote(Entry.feed(FeedType.FORMULA, now - minute, id = "remote-blank").copy(babyId = "")))
        assertEquals(listOf("remote-blank"), repo.observeEntries().first().map { it.id })
    }
}
