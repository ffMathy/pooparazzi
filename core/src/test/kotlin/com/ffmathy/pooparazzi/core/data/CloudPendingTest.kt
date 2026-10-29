package com.ffmathy.pooparazzi.core.data

import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryJson
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.FakeEntrySync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The two pending flags of a row (BABIES_AND_BACKUP §4.2): `pendingSync` for the watch, `cloudPending` for the backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudPendingTest {
    private lateinit var db: PooparazziDatabase
    private val clock = FixedClock(SampleData.NOW_1432)
    private val watch = FakeEntrySync(accept = false)
    private lateinit var phone: RoomEntryRepository

    private val now get() = clock.now()
    private val minute = 60_000L

    @Before
    fun setUp() {
        db = PooparazziDatabase.inMemory(ApplicationProvider.getApplicationContext())
        phone = RoomEntryRepository(db, clock, watch, cloudBackup = { true })
    }

    @After
    fun tearDown() = db.close()

    private fun test(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun flags(id: String): Pair<Boolean, Boolean> =
        db.entryDao().get(id)!!.let { it.pendingSync to it.cloudPending }

    @Test
    fun theFlagTable() = test {
        // A local write: pending for both.
        val local = phone.logFeed(FeedType.FORMULA, now - 10 * minute)
        assertEquals(true to true, flags(local.id))

        // From the other device: not pending for it, pending for the backend (the phone uploads what the watch logged).
        val fromWatch = Entry.diaper(now - 5 * minute, pee = true, poop = false, id = "w", updatedAtMillis = now)
        assertEquals(1, phone.applyRemoteAll(listOf(fromWatch)))
        assertEquals(false to true, flags("w"))

        // From the backend: pending for the watch (it gets what was restored), not for the backend.
        watch.accept = true
        val fromCloud = Entry.feed(FeedType.FOOD, now - 30 * minute, id = "c", updatedAtMillis = now)
        assertEquals(1, phone.applyCloudEntries(listOf(fromCloud)))
        assertEquals(fromCloud, watch.items["c"])
        assertEquals("handed to the watch", false to false, flags("c"))
        watch.accept = false
        val newer = fromCloud.copy(startMillis = now - 31 * minute, updatedAtMillis = now + 1)
        assertEquals(1, phone.applyCloudEntries(listOf(newer)))
        assertEquals(true to false, flags("c"))
        assertEquals(newer, phone.get("c"))
    }

    @Test
    fun withoutABackendNothingIsPendingForIt() = test {
        val watchRepo = RoomEntryRepository(db, clock, watch)
        val local = watchRepo.logFeed(FeedType.FORMULA, now)
        assertEquals(true to false, flags(local.id))
        watchRepo.applyRemoteAll(listOf(Entry.feed(FeedType.FOOD, now - minute, id = "p", updatedAtMillis = now)))
        assertEquals(false to false, flags("p"))
    }

    @Test
    fun aConflictLosersDeletionIsALocalWrite() = test {
        val ours = phone.startSleep(now - 5 * minute).getOrThrow()
        phone.markCloudSynced(listOf(ours))
        val earlier = Entry.sleep(now - 9 * minute, id = "cloud-sleep", updatedAtMillis = now)
        phone.applyCloudEntries(listOf(earlier))
        val loser = db.entryDao().get(ours.id)!!
        assertTrue(loser.deleted)
        assertEquals(true to true, loser.pendingSync to loser.cloudPending)
        assertEquals(false, flags("cloud-sleep").second)
        assertEquals(setOf("cloud-sleep", ours.id), watch.pushed.map { it.id }.toSet())
    }

    @Test
    fun anIdenticalCloudVersionClearsCloudPending() = test {
        val local = phone.logFeed(FeedType.FORMULA, now - 10 * minute)
        val edited = phone.update(local.copy(startMillis = now - 12 * minute))
        assertEquals(true to true, flags(local.id))

        // An older version leaves the row pending (the backend lacks the edit); the same version clears it.
        assertEquals(0, phone.applyCloudEntries(listOf(local)))
        assertEquals(true to true, flags(local.id))
        assertEquals(0, phone.applyCloudEntries(listOf(edited)))
        assertEquals("pendingSync is untouched", true to false, flags(local.id))
        assertEquals(edited, phone.get(local.id))
        assertTrue(phone.cloudPending(10).isEmpty())
    }

    @Test
    fun aCloudVersionThatLosesToTheStoredRowFlagsItForUploadAgain() = test {
        // The row is backed up (not pending), then another phone's stale offline edit overwrites the backup.
        val local = phone.logFeed(FeedType.FORMULA, now - 10 * minute)
        val edited = phone.update(local.copy(startMillis = now - 12 * minute))
        phone.markCloudSynced(listOf(edited))
        assertEquals(true to false, flags(local.id))

        val older = local.copy(startMillis = now - 11 * minute, updatedAtMillis = edited.updatedAtMillis - 1)
        assertEquals(0, phone.applyCloudEntries(listOf(older)))
        assertEquals("the backend's version lost: it gets ours again", true to true, flags(local.id))
        assertEquals(edited, phone.get(local.id))
        assertEquals(listOf(edited), phone.cloudPending(10))

        // The same stamp with content that loses the tie-break (JSON order).
        phone.markCloudSynced(listOf(edited))
        val candidates = listOf(now - 30 * minute, now - 20 * minute).map { edited.copy(startMillis = it) }
        val loser = candidates.first { EntryJson.encode(it) < EntryJson.encode(edited) }
        assertEquals(0, phone.applyCloudEntries(listOf(loser)))
        assertEquals(true to true, flags(local.id))
        assertEquals(edited, phone.get(local.id))

        // Without a backend nothing is flagged for it.
        val watchSide = RoomEntryRepository(db, clock, watch)
        phone.markCloudSynced(listOf(edited))
        watchSide.applyCloudEntries(listOf(older))
        assertEquals(true to false, flags(local.id))
    }

    @Test
    fun pendingRowsAreListedAndClearedOnlyForTheVersionSent() = test {
        val a = phone.logFeed(FeedType.FORMULA, now - 30 * minute)
        clock.advanceBy(1)
        val b = phone.logFeed(FeedType.FOOD, now - 20 * minute)
        clock.advanceBy(1)
        val c = phone.logDiaper(now - 10 * minute, pee = true, poop = false)
        assertEquals(3, phone.observeCloudPendingCount().first())
        assertEquals("oldest write first", listOf(a, b), phone.cloudPending(2))

        val sent = phone.cloudPending(10)
        val bEdited = phone.update(b.copy(startMillis = now - 21 * minute))
        assertEquals(2, phone.markCloudSynced(sent))
        assertEquals("b was written again after it was read", listOf(bEdited), phone.cloudPending(10))
        assertEquals(1, phone.observeCloudPendingCount().first())
        assertEquals(0, phone.markCloudSynced(listOf(a, c)))

        phone.markAllForCloud()
        assertEquals(3, phone.observeCloudPendingCount().first())
    }

    @Test
    fun aDeletionIsPurgedOnlyOnceTheWatchAndTheBackendHaveIt() = test {
        val feed = phone.logFeed(FeedType.FOOD, now)
        phone.delete(feed.id)
        clock.advanceBy(EntryRules.SOFT_DELETE_RETENTION_MILLIS + minute)
        assertEquals(0, phone.purgeDeleted())

        watch.accept = true
        assertTrue(phone.pushPending())
        assertEquals("the watch has it, the backend not yet", 0, phone.purgeDeleted())
        assertEquals(1, phone.markCloudSynced(phone.cloudPending(10)))
        assertEquals(1, phone.purgeDeleted())
        assertNull(phone.get(feed.id))
        assertFalse(phone.applyCloudEntries(listOf(feed.copy(deleted = true))) > 0)
    }

    @Test
    fun aMigratedRowWaitsForTheBackendOnlyAfterTheFirstSync() = test {
        // Rows from before the backup are not pending for it until an account's first sync flags them all.
        val old = Entry.feed(FeedType.FORMULA, now - minute, id = "old")
        db.entryDao().write(EntryEntity.from(old, pendingSync = false))
        assertTrue(phone.cloudPending(10).isEmpty())
        phone.markAllForCloud()
        assertEquals(listOf(old), phone.cloudPending(10))
    }
}
