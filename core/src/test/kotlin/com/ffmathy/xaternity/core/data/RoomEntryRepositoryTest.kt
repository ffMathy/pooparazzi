package com.ffmathy.xaternity.core.data

import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.model.AlreadyRunningException
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryJson
import com.ffmathy.xaternity.core.model.EntryViolation
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.InvalidEntryException
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.sync.FakeEntrySync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomEntryRepositoryTest {
    private lateinit var db: XaternityDatabase
    private lateinit var clock: FixedClock
    private lateinit var sync: FakeEntrySync
    private lateinit var repo: RoomEntryRepository

    private val now get() = clock.now()
    private val minute = 60_000L

    @Before
    fun setUp() {
        db = XaternityDatabase.inMemory(ApplicationProvider.getApplicationContext())
        clock = FixedClock(SampleData.NOW_1432)
        sync = FakeEntrySync()
        repo = RoomEntryRepository(db, clock, sync)
    }

    @After
    fun tearDown() = db.close()

    private fun test(block: suspend () -> Unit) = runBlocking { block() }

    // ---- Sleep ------------------------------------------------------------------------------

    @Test
    fun startAndEndSleep() = test {
        val start = now - 47 * minute
        val sleep = repo.startSleep(start).getOrThrow()
        assertTrue(sleep.isRunning)
        assertEquals(start, sleep.startMillis)
        assertEquals(listOf(sleep), repo.observeEntries().first())

        clock.advanceBy(1_000)
        val result = repo.endSleep(sleep.id, now) as EndResult.Ended
        assertEquals(now, result.entry.endMillis)
        assertFalse(result.entry.isRunning)
        assertEquals(sleep, result.previous)
        assertTrue(result.entry.updatedAtMillis > sleep.updatedAtMillis)
        assertEquals(result.entry, repo.get(sleep.id))
    }

    @Test
    fun secondRunningSleepIsRejected() = test {
        val first = repo.startSleep(now - 10 * minute).getOrThrow()
        val second = repo.startSleep(now)
        val error = second.exceptionOrNull() as AlreadyRunningException
        assertEquals(first, error.running)
        assertEquals(1, repo.observeEntries().first().size)
    }

    @Test
    fun sleepEndedUnder60SecondsIsDiscardedAndUndoRestoresIt() = test {
        val sleep = repo.startSleep(now).getOrThrow()
        clock.advanceBy(59_999)
        val result = repo.endSleep(sleep.id, now) as EndResult.Discarded
        assertTrue(result.entry.deleted)
        assertEquals(now, result.entry.endMillis)
        assertEquals(sleep, result.previous)
        assertTrue(repo.observeEntries().first().isEmpty())

        val restored = repo.restore(result.previous)
        assertTrue(restored.isRunning)
        assertTrue(restored.updatedAtMillis > result.entry.updatedAtMillis)
        assertEquals(listOf(restored), repo.observeEntries().first())
    }

    @Test
    fun sleepEndedAtExactly60SecondsIsKept() = test {
        val sleep = repo.startSleep(now - minute).getOrThrow()
        assertTrue(repo.endSleep(sleep.id, now) is EndResult.Ended)
    }

    @Test
    fun endingWhatIsNotRunning() = test {
        assertEquals(EndResult.NotRunning(null), repo.endSleep("unknown", now))
        val sleep = repo.startSleep(now - 5 * minute).getOrThrow()
        val ended = (repo.endSleep(sleep.id, now) as EndResult.Ended).entry
        // The other device already ended it: "Already awake since …".
        assertEquals(EndResult.NotRunning(ended), repo.endSleep(sleep.id, now))
        // A sleep id is not a breastfeed.
        val running = repo.startSleep(now).getOrThrow()
        assertTrue(repo.endBreastfeed(running.id, now, BreastSide.LEFT) is EndResult.NotRunning)
    }

    @Test
    fun endBeforeStartIsRejected() = test {
        val sleep = repo.startSleep(now - minute).getOrThrow()
        val error = assertThrows(InvalidEntryException::class.java) { runBlocking { repo.endSleep(sleep.id, now - 2 * minute) } }
        assertEquals(EntryViolation.END_BEFORE_START, error.violation)
        assertTrue(repo.get(sleep.id)!!.isRunning)
    }

    @Test
    fun futureTimesAreRejected() = test {
        val failure = repo.startSleep(now + 2 * minute).exceptionOrNull() as InvalidEntryException
        assertEquals(EntryViolation.IN_FUTURE, failure.violation)
        assertThrows(InvalidEntryException::class.java) { runBlocking { repo.logFeed(FeedType.FOOD, now + 10 * minute) } }
        // Within the tolerance is fine (tap-to-write jitter).
        assertTrue(repo.startSleep(now + 30_000).isSuccess)
    }

    // ---- Breastfeeds --------------------------------------------------------------------------

    @Test
    fun breastfeedRunsWithoutSideAndEndsWithOne() = test {
        val feed = repo.startBreastfeed(now - 18 * minute).getOrThrow()
        assertEquals(FeedType.BREAST, feed.feedType)
        assertNull(feed.breastSide)
        assertTrue(feed.isRunning)

        val ended = repo.endBreastfeed(feed.id, now, BreastSide.LEFT_THEN_RIGHT) as EndResult.Ended
        assertEquals(BreastSide.LEFT_THEN_RIGHT, ended.entry.breastSide)
        assertEquals(now, ended.entry.endMillis)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, repo.get(feed.id)!!.breastSide)
    }

    @Test
    fun breastfeedSideIsOptional() = test {
        val feed = repo.startBreastfeed(now - 18 * minute).getOrThrow()
        // Ending with nothing ticked records only the times.
        val ended = (repo.endBreastfeed(feed.id, now, side = null) as EndResult.Ended).entry
        assertNull(ended.breastSide)
        assertEquals(now, repo.get(feed.id)!!.endMillis)
        // A side can be added (and removed again) later; a running feed still cannot have one.
        assertEquals(BreastSide.RIGHT, repo.update(ended.copy(breastSide = BreastSide.RIGHT)).breastSide)
        assertNull(repo.update(repo.get(feed.id)!!.copy(breastSide = null)).breastSide)
        val sideWhileRunning = assertThrows(InvalidEntryException::class.java) {
            runBlocking { repo.update(ended.copy(endMillis = null, breastSide = BreastSide.LEFT)) }
        }
        assertEquals(EntryViolation.SIDE_WHILE_RUNNING, sideWhileRunning.violation)
    }

    @Test
    fun feedWithJustTheTime() = test {
        val feed = repo.logFeed(null, now)
        assertNull(feed.feedType)
        assertNull(feed.endMillis)
        assertEquals(feed, repo.get(feed.id))
        // Deduplicated like any instant, apart from typed feeds at the same time.
        val again = repo.logFeedChecked(null, now + 2_000)
        assertTrue(again is LogResult.Duplicate)
        assertEquals(feed, again.entry)
        assertTrue(repo.logFeedChecked(FeedType.FORMULA, now) is LogResult.Logged)
        assertTrue(repo.logFeedChecked(null, now - 5_000) is LogResult.Logged)
        // A type can be set later, and cleared again.
        assertEquals(FeedType.FOOD, repo.update(feed.copy(feedType = FeedType.FOOD)).feedType)
        assertNull(repo.update(repo.get(feed.id)!!.copy(feedType = null)).feedType)
    }

    @Test
    fun oneBreastfeedAtATimeButItMayOverlapASleep() = test {
        repo.startSleep(now - 30 * minute).getOrThrow()
        val feed = repo.startBreastfeed(now - 5 * minute).getOrThrow()
        val second = repo.startBreastfeed(now).exceptionOrNull() as AlreadyRunningException
        assertEquals(feed, second.running)
    }

    @Test
    fun shortBreastfeedIsDiscarded() = test {
        val feed = repo.startBreastfeed(now - 30_000).getOrThrow()
        val result = repo.endBreastfeed(feed.id, now, BreastSide.LEFT) as EndResult.Discarded
        assertTrue(result.entry.deleted)
        assertEquals(BreastSide.LEFT, result.entry.breastSide)
        assertTrue(repo.observeEntries().first().isEmpty())
    }

    // ---- Instant feeds and diapers ------------------------------------------------------------

    @Test
    fun formulaAndFoodAreInstants() = test {
        val formula = repo.logFeed(FeedType.FORMULA, now)
        val food = repo.logFeed(FeedType.FOOD, now - 5 * minute)
        assertNull(formula.endMillis)
        assertEquals(FeedType.FOOD, food.feedType)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.logFeed(FeedType.BREAST, now) } }
    }

    @Test
    fun repeatedLogsWithin5SecondsAreOneEntry() = test {
        val first = repo.logFeedChecked(FeedType.FORMULA, now)
        assertTrue(first is LogResult.Logged)
        val again = repo.logFeedChecked(FeedType.FORMULA, now + 4_000)
        assertTrue(again is LogResult.Duplicate)
        assertEquals(first.entry, again.entry)
        // Another type, or 5 s apart, is a new entry.
        assertTrue(repo.logFeedChecked(FeedType.FOOD, now) is LogResult.Logged)
        assertTrue(repo.logFeedChecked(FeedType.FORMULA, now - 5_000) is LogResult.Logged)

        val pee = repo.logDiaper(now, pee = true, poop = false)
        assertEquals(pee, repo.logDiaper(now - 1_000, pee = true, poop = false))
        assertNotEquals(pee.id, repo.logDiaper(now, pee = true, poop = true).id)
        assertEquals(5, repo.observeEntries().first().size)
    }

    @Test
    fun diaperInvariants() = test {
        // Pee and poop are optional: nothing ticked is a plain change, deduplicated like the others.
        val plain = repo.logDiaper(now - 10 * minute, pee = false, poop = false, color = PoopColor.RED)
        assertFalse(plain.pee || plain.poop)
        assertNull("colour is dropped without poop", plain.poopColor)
        assertEquals(plain, repo.logDiaper(now - 10 * minute + 1_000, pee = false, poop = false))

        val pee = repo.logDiaper(now, pee = true, poop = false, color = PoopColor.YELLOW, texture = PoopTexture.SEEDY)
        assertNull("colour is dropped without poop", pee.poopColor)
        assertNull(pee.poopTexture)

        val poop = repo.logDiaper(now - minute, pee = false, poop = true, color = PoopColor.YELLOW, texture = PoopTexture.SEEDY)
        assertEquals(PoopColor.YELLOW, repo.get(poop.id)!!.poopColor)
        assertEquals(PoopTexture.SEEDY, repo.get(poop.id)!!.poopTexture)

        val cleared = assertThrows(InvalidEntryException::class.java) {
            runBlocking { repo.update(poop.copy(poop = false, pee = true)) }
        }
        assertEquals(EntryViolation.POOP_DETAILS_WITHOUT_POOP, cleared.violation)
        val emptied = repo.update(poop.copy(poop = false, poopColor = null, poopTexture = null))
        assertFalse(emptied.pee || emptied.poop)
        repo.update(emptied.copy(poop = true, poopColor = PoopColor.YELLOW, poopTexture = PoopTexture.SEEDY))

        val updated = repo.update(poop.copy(pee = true, poopColor = PoopColor.GREEN))
        assertEquals(PoopColor.GREEN, repo.get(poop.id)!!.poopColor)
        assertTrue(updated.pee)
    }

    // ---- Update, delete, restore, add ---------------------------------------------------------

    @Test
    fun updateBumpsTheClockEvenWhenTimeStandsStill() = test {
        val feed = repo.logFeed(FeedType.FORMULA, now)
        val moved = repo.update(feed.copy(startMillis = now - 10 * minute))
        assertEquals(feed.updatedAtMillis + 1, moved.updatedAtMillis)
        val again = repo.update(moved.copy(startMillis = now - 15 * minute))
        assertTrue(again.updatedAtMillis > moved.updatedAtMillis)
        assertThrows(NoSuchElementException::class.java) { runBlocking { repo.update(Entry.sleep(now, now)) } }
    }

    @Test
    fun updateCannotMakeASecondRunningSleep() = test {
        val old = repo.startSleep(now - 60 * minute).getOrThrow()
        (repo.endSleep(old.id, now - 30 * minute) as EndResult.Ended)
        repo.startSleep(now - 10 * minute).getOrThrow()
        assertThrows(AlreadyRunningException::class.java) { runBlocking { repo.update(repo.get(old.id)!!.copy(endMillis = null)) } }
    }

    @Test
    fun softDeleteAndRestore() = test {
        val feed = repo.logFeed(FeedType.FOOD, now)
        val snapshot = repo.delete(feed.id)!!
        assertEquals(feed, snapshot)
        assertTrue(repo.get(feed.id)!!.deleted)
        assertTrue(repo.observeEntries().first().isEmpty())
        val deletedStamp = repo.get(feed.id)!!.updatedAtMillis

        val restored = repo.restore(snapshot)
        assertFalse(restored.deleted)
        assertTrue(restored.updatedAtMillis > deletedStamp)
        assertEquals(listOf(restored), repo.observeEntries().first())
        assertNull(repo.delete("unknown"))
    }

    @Test
    fun restoreRefusesASecondRunningInterval() = test {
        val first = repo.startSleep(now - 20 * minute).getOrThrow()
        val ended = repo.endSleep(first.id, now - 10 * minute) as EndResult.Ended
        repo.startSleep(now - 5 * minute).getOrThrow()
        assertThrows(AlreadyRunningException::class.java) { runBlocking { repo.restore(ended.previous) } }
    }

    @Test
    fun addPastEntry() = test {
        val past = Entry.breastfeed(now - 60 * minute, now - 45 * minute, BreastSide.LEFT)
        val added = repo.add(past)
        assertEquals(past.copy(updatedAtMillis = now), added)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.add(past) } }
    }

    @Test
    fun purgeRemovesOnlyDeletionsOlderThan7Days() = test {
        val old = repo.logFeed(FeedType.FORMULA, now - 60 * minute)
        val recent = repo.logFeed(FeedType.FOOD, now - 30 * minute)
        val kept = repo.logFeed(FeedType.FORMULA, now - 10 * minute)
        repo.delete(old.id)
        clock.advanceBy(2 * 24 * 60 * minute)
        repo.delete(recent.id)
        clock.advanceBy(5 * 24 * 60 * minute + minute)

        assertEquals(1, repo.purgeDeleted())
        assertNull(repo.get(old.id))
        assertTrue(repo.get(recent.id)!!.deleted)
        assertFalse(repo.get(kept.id)!!.deleted)
        // The Data Layer item stays, as a deletion, so a device that was away all week still learns of it.
        assertTrue(sync.items.getValue(old.id).deleted)
        // A stale copy from the other device cannot bring a purged entry back.
        assertFalse(repo.applyRemote(old))
        assertNull(repo.get(old.id))
    }

    // ---- Remote merge (LWW) ---------------------------------------------------------------------

    @Test
    fun applyRemoteIsLastWriterWinsWithoutEcho() = test {
        val local = repo.logFeed(FeedType.FORMULA, now - 10 * minute)
        sync.pushed.clear()

        val older = local.copy(startMillis = now - 20 * minute, updatedAtMillis = local.updatedAtMillis - 1)
        assertFalse(repo.applyRemote(older))
        assertEquals(local, repo.get(local.id))

        val newer = local.copy(startMillis = now - 30 * minute, updatedAtMillis = local.updatedAtMillis + 1_000)
        assertTrue(repo.applyRemote(newer))
        assertEquals(newer, repo.get(local.id))

        assertFalse("an identical version is not rewritten", repo.applyRemote(newer))

        val brandNew = Entry.diaper(now - 2 * minute, pee = true, poop = false, id = "remote-1")
        assertTrue(repo.applyRemote(brandNew))
        assertEquals(brandNew, repo.get("remote-1"))

        assertTrue("remote merges are never pushed back", sync.pushed.isEmpty())
        assertTrue(repo.pendingSync().none { it.id == "remote-1" })
    }

    @Test
    fun applyRemoteDeletionWins() = test {
        val local = repo.logFeed(FeedType.FOOD, now)
        assertTrue(repo.applyRemote(local.copy(deleted = true, updatedAtMillis = local.updatedAtMillis + 1)))
        assertTrue(repo.observeEntries().first().isEmpty())
    }

    @Test
    fun applyRemoteRejectsInvalidEntries() = test {
        val bad = Entry.diaper(now, pee = true, poop = false, id = "bad").copy(poopColor = PoopColor.RED)
        assertFalse(repo.applyRemote(bad))
        assertNull(repo.get("bad"))
    }

    @Test
    fun applyRemoteAcceptsTheMinimalEntries() = test {
        // A plain diaper change, a feed with just its time and a breastfeed ended without a side.
        val plain = Entry.diaper(now, pee = false, poop = false, id = "plain")
        val untyped = Entry.feed(null, now - minute, id = "untyped")
        val noSide = Entry.breastfeed(now - 30 * minute, now - 10 * minute, side = null, id = "no-side")
        assertEquals(3, repo.applyRemoteAll(listOf(plain, untyped, noSide)))
        assertEquals(plain, repo.get("plain"))
        assertEquals(untyped, repo.get("untyped"))
        assertEquals(noSide, repo.get("no-side"))
    }

    @Test
    fun aRemoteRunningSleepThatLosesIsLeftToTheDeviceThatCreatedIt() = test {
        val local = repo.startSleep(now - 7 * minute).getOrThrow()
        sync.pushed.clear()
        val remote = Entry.sleep(now - 5 * minute, id = "remote-sleep", updatedAtMillis = now)
        assertTrue(repo.applyRemote(remote))

        // Ours started first and wins, but only the watch deletes the watch's entry: a newer running sleep from
        // the watch may simply mean the watch already ended ours and that end is still on its way.
        assertFalse(repo.get("remote-sleep")!!.deleted)
        assertTrue(sync.pushed.isEmpty())
        assertTrue(repo.get(local.id)!!.isRunning)
    }

    @Test
    fun aLocalRunningSleepThatLosesIsDeletedAndPushed() = test {
        val local = repo.startSleep(now - 7 * minute).getOrThrow()
        sync.pushed.clear()
        val earlier = Entry.sleep(now - 9 * minute, id = "remote-earlier", updatedAtMillis = now)
        assertTrue(repo.applyRemote(earlier))

        assertEquals(listOf("remote-earlier"), repo.observeEntries().first().filter { it.isRunning }.map { it.id })
        assertTrue(repo.get(local.id)!!.deleted)
        // The loser's deletion is a local write, pushed so the other device converges.
        assertEquals(listOf(local.id), sync.pushed.map { it.id })
        assertTrue(sync.pushed.single().deleted)
    }

    // ---- Sync bookkeeping ---------------------------------------------------------------------

    @Test
    fun localWritesArePushedAndMarkedSynced() = test {
        val feed = repo.logFeed(FeedType.FORMULA, now)
        assertEquals(listOf(feed), sync.pushed)
        assertTrue(repo.pendingSync().isEmpty())

        sync.accept = false
        val food = repo.logFeed(FeedType.FOOD, now - minute)
        assertEquals(listOf(food), repo.pendingSync())
        sync.accept = true
        repo.markSynced(food)
        assertTrue(repo.pendingSync().isEmpty())
    }

    @Test
    fun duplicatesAndRejectedStartsAreNotPushed() = test {
        repo.startSleep(now).getOrThrow()
        repo.startSleep(now)
        repo.logFeed(FeedType.FOOD, now)
        repo.logFeed(FeedType.FOOD, now)
        assertEquals(2, sync.pushed.size)
    }

    @Test
    fun daoUpsertIsLastWriterWins() = test {
        val dao = db.entryDao()
        val entry = Entry.feed(FeedType.FOOD, now, id = "x", updatedAtMillis = 100)
        assertTrue(dao.upsert(EntryEntity.from(entry, pendingSync = false)))
        assertFalse(dao.upsert(EntryEntity.from(entry.copy(startMillis = 1, updatedAtMillis = 99), pendingSync = false)))
        assertEquals(entry, dao.get("x")!!.toEntry())
        assertFalse("identical: no rewrite", dao.upsert(EntryEntity.from(entry, pendingSync = false)))
        assertTrue(dao.upsert(EntryEntity.from(entry.copy(startMillis = 2, updatedAtMillis = 101), pendingSync = false)))
        assertEquals(2L, dao.get("x")!!.startMillis)
    }

    @Test
    fun equalClocksWithDifferentContentPickTheSameWinnerInEitherOrder() = test {
        val a = Entry.feed(FeedType.FOOD, now - 2 * minute, id = "tie", updatedAtMillis = 500)
        val b = a.copy(startMillis = now - 3 * minute)
        val winner = maxOf(a, b, compareBy { EntryJson.encode(it) })
        val dao = db.entryDao()

        dao.upsert(EntryEntity.from(a, pendingSync = false))
        dao.upsert(EntryEntity.from(b, pendingSync = false))
        assertEquals(winner, dao.get("tie")!!.toEntry())

        dao.deleteRows(listOf("tie"))
        dao.upsert(EntryEntity.from(b, pendingSync = false))
        dao.upsert(EntryEntity.from(a, pendingSync = false))
        assertEquals(winner, dao.get("tie")!!.toEntry())
    }

    @Test
    fun aRemoteUpdateKeepsTheRowsOrigin() = test {
        val local = repo.logFeed(FeedType.FOOD, now)
        repo.applyRemote(local.copy(startMillis = now - minute, updatedAtMillis = local.updatedAtMillis + 1))
        assertTrue(db.entryDao().get(local.id)!!.createdLocally)
        repo.applyRemote(Entry.feed(FeedType.FORMULA, now - 30 * minute, id = "w"))
        assertFalse(db.entryDao().get("w")!!.createdLocally)
    }

    @Test
    fun anUnsyncedDeletionIsNotPurged() = test {
        sync.accept = false
        val feed = repo.logFeed(FeedType.FOOD, now)
        repo.delete(feed.id)
        clock.advanceBy(EntryRules.SOFT_DELETE_RETENTION_MILLIS + minute)
        assertEquals(0, repo.purgeDeleted())
        sync.accept = true
        assertTrue(repo.pushPending())
        assertTrue(sync.items.getValue(feed.id).deleted)
        assertEquals(1, repo.purgeDeleted())
    }

    @Test
    fun observeEntriesIsSortedOldestFirst() = test {
        repo.logFeed(FeedType.FOOD, now - minute)
        repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        repo.logDiaper(now - 10 * minute, pee = true, poop = false)
        val starts = repo.observeEntries().first().map { it.startMillis }
        assertEquals(starts.sorted(), starts)
        assertEquals(3, starts.size)
    }
}
