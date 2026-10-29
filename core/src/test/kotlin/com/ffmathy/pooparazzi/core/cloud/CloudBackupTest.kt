package com.ffmathy.pooparazzi.core.cloud

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.data.EndResult
import com.ffmathy.pooparazzi.core.data.EntryEntity
import com.ffmathy.pooparazzi.core.data.EntryRules
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.PooparazziDatabase
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.FakeEntrySync
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The backup engine against an in-memory backend, in virtual time (BABIES_AND_BACKUP §4). Each [Phone] has its own
 * database, watch and account; phones on one account share [store].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudBackupTest {
    private val clock = FixedClock(SampleData.NOW_1432)
    private val store = FakeCloudStore()
    private val opened = mutableListOf<PooparazziDatabase>()

    private val now get() = clock.now()
    private val minute = 60_000L
    private val alice = CloudAccount("uid-alice", "alice@example.com", BackupConsent.VERSION)
    private val bob = CloudAccount("uid-bob", "bob@example.com", BackupConsent.VERSION)
    private val twin = "baby-2"

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        opened.forEach { it.close() }
    }

    /** A phone with a backend, on the test's virtual time: database queries, pushes and timers all run in it. */
    private inner class Phone(
        private val test: TestScope,
        signedIn: CloudAccount? = null,
        val bookkeeping: InMemoryCloudBookkeepingStore = InMemoryCloudBookkeepingStore(),
    ) {
        val db: PooparazziDatabase = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), PooparazziDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(test.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
            .build()
            .also { opened += it }
        val watch = FakeEntrySync()
        val baby = MutableStateFlow(Baby.FIRST_ID)
        val account = MutableStateFlow(signedIn)

        /** Flagged for the backend while an account is signed in, as in the app. */
        val repo = RoomEntryRepository(db, clock, watch, selectedBaby = baby, cloudBackup = { account.value?.consented == true })

        /**
         * The app's lifetime: on the test's dispatcher, but not the test's background scope, whose work
         * `advanceUntilIdle` does not wait for. Cancelled after the test.
         */
        private var scope = newScope()
        var backup = newBackup()
            private set

        private fun newScope() = CoroutineScope(test.coroutineContext + SupervisorJob()).also { scopes += it }

        private fun newBackup() =
            CloudBackup(repo, store, account, bookkeeping, clock, scope, monotonicMillis = { test.testScheduler.currentTime })

        /** The app starting again: a new backup on the same database and bookkeeping (whatever it kept in memory is lost). */
        fun restart() {
            scope.cancel()
            scope = newScope()
            backup = newBackup()
            backup.start()
        }

        suspend fun all(): List<Entry> = repo.observeAllEntries().first()
        suspend fun pending(): List<Entry> = repo.cloudPending(100_000)
        val status: CloudStatus get() = backup.status.value
    }

    // ---- The first sync ---------------------------------------------------------------------------

    @Test
    fun theFirstSyncBacksUpEverythingLoggedSoFar() = runTest {
        val phone = Phone(this)
        val feed = phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        // A row from before the backup existed: never flagged for it.
        val old = Entry.sleep(now - 120 * minute, now - 90 * minute, id = "old")
        phone.db.entryDao().write(EntryEntity.from(old, pendingSync = false))
        phone.baby.value = twin
        val diaper = phone.repo.logDiaper(now - 10 * minute, pee = true, poop = false)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals("without an account nothing is sent", CloudStatus.Off, phone.status)
        assertEquals(0, store.pulls)
        assertTrue(store.uploads.isEmpty())

        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(mapOf(feed.id to feed, old.id to old, diaper.id to diaper), store.entries(alice.uid))
        assertEquals("the baby's id is uploaded, never a name", twin, store.entries(alice.uid).getValue(diaper.id).babyId)
        assertTrue(phone.pending().isEmpty())
        assertEquals("one pull (nothing there yet), one upload", 1 to 1, store.pulls to store.uploads.size)
        assertEquals(alice.uid, phone.bookkeeping.values.fullUploadUid)
        assertEquals(now, phone.bookkeeping.values.lastSyncFor(alice.uid))
    }

    @Test
    fun aSecondPhoneRestoresTheBackupAndForwardsItToItsWatch() = runTest {
        val first = Phone(this, alice)
        first.repo.logFeed(FeedType.BREAST, now - 40 * minute)
        first.repo.startSleep(now - 20 * minute).getOrThrow()
        first.baby.value = twin
        first.repo.logDiaper(now - 5 * minute, pee = false, poop = true)
        first.backup.start()
        advanceUntilIdle()
        val uploaded = store.uploadedCount

        // A new phone with something of its own, signed in to the same account.
        val second = Phone(this, alice)
        val own = second.repo.logFeed(FeedType.FOOD, now - 50 * minute)
        second.backup.start()
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), second.status)
        assertEquals((first.all() + own).sortedBy { it.startMillis }, second.all())
        assertEquals("the restored log reaches the watch", second.all().map { it.id }.toSet(), second.watch.items.keys)
        assertTrue(second.repo.pendingSync().isEmpty())
        assertEquals(listOf(own.id), store.uploads.last())
        assertEquals("only its own entry was uploaded", uploaded + 1, store.uploadedCount)
        assertEquals(setOf(Baby.FIRST_ID, twin), second.repo.observeBabyIds().first().toSet())

        // The first phone gets the second one's entry at its next pull.
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        first.backup.requestSync()
        advanceUntilIdle()
        assertEquals(second.all(), first.all())
        assertEquals(own, first.watch.items[own.id])
    }

    // ---- Signing in again, and other accounts ------------------------------------------------------

    @Test
    fun signingInAgainUploadsOnlyTheDifferences() = runTest {
        val phone = Phone(this, alice)
        phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.repo.logFeed(FeedType.FOOD, now - 20 * minute)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(2, store.uploadedCount)

        phone.account.value = null
        advanceUntilIdle()
        assertEquals(CloudStatus.Off, phone.status)
        val offline = phone.repo.logDiaper(now - 10 * minute, pee = true, poop = false)
        advanceUntilIdle()
        assertEquals("nothing is sent while signed out", 2, store.uploadedCount)

        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(listOf(offline.id), store.uploads.last())
        assertEquals(3, store.uploadedCount)

        // Even with this phone's bookkeeping lost (cleared app data), the first pull clears what the backup holds.
        phone.account.value = null
        advanceUntilIdle()
        phone.bookkeeping.values = CloudBookkeeping()
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(3, store.uploadedCount)
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun anotherAccountGetsEverythingAndSwitchingBackSendsOnlyWhatItLacks() = runTest {
        val phone = Phone(this, alice)
        val a = phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        val b = phone.repo.logDiaper(now - 20 * minute, pee = true, poop = true)
        phone.backup.start()
        advanceUntilIdle()

        // Bob's backup already holds an entry from his own phone.
        val bobs = Entry.feed(FeedType.FOOD, now - 60 * minute, id = "bobs", updatedAtMillis = now)
        store.upload(bob.uid, listOf(bobs))
        phone.account.value = bob
        advanceUntilIdle()
        assertEquals(setOf(a.id, b.id, bobs.id), store.entries(bob.uid).keys)
        assertEquals(bobs, phone.repo.get(bobs.id))
        assertEquals(bob.uid, phone.bookkeeping.values.fullUploadUid)
        assertEquals(bob.uid, phone.bookkeeping.values.cursorUid)

        // Back to alice: pulled from the start (the cursor is bob's), which clears what her backup holds.
        val sent = store.uploadedCount
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(listOf(bobs.id), store.uploads.last())
        assertEquals(sent + 1, store.uploadedCount)
        assertEquals(setOf(a.id, b.id, bobs.id), store.entries(alice.uid).keys)
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    // ---- Pulling ----------------------------------------------------------------------------------

    @Test
    fun aPullPagesThroughChangesThatShareServerTimes() = runTest {
        // Another phone uploaded 1 202 entries in batches of 400: every entry of a batch has the batch's server time,
        // so pages of 500 end in the middle of a batch.
        val entries = (0 until 1_202).map {
            Entry.feed(FeedType.FOOD, now - (it + 1) * 10_000L, id = "e-%04d".format(it), babyId = if (it % 2 == 0) Baby.FIRST_ID else twin)
        }
        entries.chunked(CloudStore.UPLOAD_BATCH).forEach { store.upload(alice.uid, it) }
        store.putRaw(alice.uid, "unreadable", "{not an entry")
        val seeded = store.uploads.size

        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(entries.map { it.id }.toSet(), phone.all().map { it.id }.toSet())
        assertEquals("500 + 500 + 203", 3, store.pulls)
        assertEquals(1_203, store.documentsRead)
        assertEquals("nothing to send back", seeded, store.uploads.size)
        assertEquals("unreadable", phone.bookkeeping.values.cursorFor(alice.uid)!!.documentId)

        // Nothing new: one empty page.
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(4, store.pulls)
        assertEquals(1_203, store.documentsRead)

        // Only what changed after the cursor is read.
        val deleted = entries[0].copy(deleted = true, updatedAtMillis = now)
        store.upload(alice.uid, listOf(deleted))
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(1_204, store.documentsRead)
        assertEquals(deleted, phone.repo.get(deleted.id))
        assertEquals(deleted, phone.watch.items[deleted.id])
    }

    @Test
    fun aPullCanResumeInsideOneServerTime() = runTest {
        store.frozenTime = true
        val entries = (0 until 600).map { Entry.diaper(now - (it + 1) * 10_000L, pee = true, poop = false, id = "d-%03d".format(it)) }
        entries.chunked(CloudStore.UPLOAD_BATCH).forEach { store.upload(alice.uid, it) }
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(600, phone.all().size)
        assertEquals(2, store.pulls)
        assertEquals(600, store.documentsRead)
    }

    @Test
    fun pullsAreThrottledToOneAMinuteButBackUpNowAlwaysPulls() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(1, store.pulls)

        phone.backup.requestSync()
        advanceUntilIdle()
        advanceTimeBy(30_000)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(1, store.pulls)
        advanceTimeBy(31_000)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(2, store.pulls)

        phone.backup.syncNow()
        assertEquals(3, store.pulls)
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    // ---- Pushing ----------------------------------------------------------------------------------

    @Test
    fun aLocalWriteIsPushedAMomentLaterAfterAPull() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        val pulls = store.pulls

        val feed = phone.repo.logFeed(FeedType.FORMULA, now - minute)
        runCurrent()
        advanceTimeBy(CloudBackup.PUSH_DELAY_MILLIS - 500)
        runCurrent()
        assertFalse(store.entries(alice.uid).containsKey(feed.id))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(feed, store.entries(alice.uid)[feed.id])
        assertEquals("every sync pulls before it pushes: one read when nothing is new", pulls + 1, store.pulls)

        // Writes in quick succession go up together.
        val uploads = store.uploads.size
        repeat(3) {
            phone.repo.logDiaper(now - (10 + it) * minute, pee = true, poop = false)
            runCurrent()
            advanceTimeBy(500)
        }
        advanceUntilIdle()
        assertEquals(uploads + 1, store.uploads.size)
        assertEquals(3, store.uploads.last().size)
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun whatTheWatchLoggedIsBackedUp() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()

        val fromWatch = Entry.diaper(now - 3 * minute, pee = true, poop = false, id = "w-pee", updatedAtMillis = now, babyId = twin)
        phone.repo.applyRemoteAll(listOf(fromWatch))
        advanceUntilIdle()
        assertEquals(fromWatch, store.entries(alice.uid)["w-pee"])
        assertFalse("not echoed to the watch", phone.watch.pushed.any { it.id == "w-pee" })
    }

    @Test
    fun aDeletionIsPurgedOnlyOnceItIsBackedUp() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        store.failure = CloudError.OFFLINE
        val feed = phone.repo.logFeed(FeedType.FOOD, now - minute)
        phone.repo.delete(feed.id)
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, phone.status)
        clock.advanceBy(EntryRules.SOFT_DELETE_RETENTION_MILLIS + minute)
        assertEquals("the watch has it, the backup not", 0, phone.repo.purgeDeleted())

        store.failure = null
        phone.backup.syncNow()
        assertTrue(store.entries(alice.uid).getValue(feed.id).deleted)
        assertEquals(1, phone.repo.purgeDeleted())
        assertNull(phone.repo.get(feed.id))
    }

    // ---- Failures ---------------------------------------------------------------------------------

    @Test
    fun offlineKeepsTheEntriesOnThePhoneUntilTheBackendIsBack() = runTest {
        store.failure = CloudError.OFFLINE
        val phone = Phone(this, alice)
        val feed = phone.repo.logFeed(FeedType.FORMULA, now - minute)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, phone.status)
        assertEquals(listOf(feed), phone.pending())
        assertEquals("flagged before anything was sent", alice.uid, phone.bookkeeping.values.fullUploadUid)
        assertNull(phone.bookkeeping.values.lastSyncFor(alice.uid))

        phone.repo.logFeed(FeedType.FOOD, now - 2 * minute)
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, phone.status)
        assertEquals(2, phone.pending().size)

        store.failure = null
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(2, store.entries(alice.uid).size)
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun failuresBecomeTheStatusAndNeverThrow() = runTest {
        val phone = Phone(this, alice)
        val feed = phone.repo.logFeed(FeedType.FORMULA, now - minute)
        store.uploadFailure = CloudError.OTHER
        phone.backup.start()
        advanceUntilIdle()
        assertEquals("a refused write", CloudStatus.Error, phone.status)
        assertEquals("pulled (at sign-in, and again before the push a moment later), but nothing sent", 2 to listOf(feed), store.pulls to phone.pending())

        store.uploadFailure = null
        store.failure = CloudError.SIGNED_OUT
        phone.backup.syncNow()
        assertEquals("the session ended", CloudStatus.Off, phone.status)

        store.failure = null
        phone.backup.syncNow()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun signedOutNothingIsSent() = runTest {
        val phone = Phone(this)
        phone.repo.logFeed(FeedType.FORMULA, now - minute)
        phone.backup.start()
        advanceUntilIdle()
        phone.backup.requestSync()
        phone.backup.syncNow()
        advanceUntilIdle()
        assertEquals(CloudStatus.Off, phone.status)
        assertEquals(0 to 0, store.pulls to store.uploads.size)
        assertTrue("signed out, nothing is pending for a backend", phone.pending().isEmpty())
        assertFalse(phone.backup.deleteBackup())
    }

    @Test
    fun withoutAnAccountDeletedRowsArePurged() = runTest {
        val phone = Phone(this)
        phone.backup.start()
        val feed = phone.repo.logFeed(FeedType.FOOD, now - minute)
        phone.repo.delete(feed.id)
        advanceUntilIdle()
        clock.advanceBy(EntryRules.SOFT_DELETE_RETENTION_MILLIS + minute)
        assertEquals("purged as without a backend", 1, phone.repo.purgeDeleted())

        // Signed in once, then out: what was waiting to go up is not waited for any more.
        phone.account.value = alice
        advanceUntilIdle()
        store.failure = CloudError.OFFLINE
        val diaper = phone.repo.logDiaper(now - minute, pee = true, poop = false)
        phone.repo.delete(diaper.id)
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, phone.status)
        phone.account.value = null
        advanceUntilIdle()
        assertTrue(phone.pending().isEmpty())
        clock.advanceBy(EntryRules.SOFT_DELETE_RETENTION_MILLIS + minute)
        assertEquals(1, phone.repo.purgeDeleted())

        // Signing in again backs up everything (the first sync for the account again), from the start of the backup.
        store.failure = null
        val later = phone.repo.logFeed(FeedType.FORMULA, now - minute)
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(later, store.entries(alice.uid)[later.id])
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun theStatusFollowsTheAccountAndTheSync() = runTest {
        store.latencyMillis = 1_000
        val phone = Phone(this, alice, InMemoryCloudBookkeepingStore(CloudBookkeeping(lastSyncUid = alice.uid, lastSyncAtMillis = 42)))
        val seen = mutableListOf<CloudStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { phone.backup.status.collect { seen += it } }
        phone.repo.logFeed(FeedType.FORMULA, now - minute)
        phone.backup.start()
        runCurrent()
        assertEquals("the last backup at once, then the sync", listOf(CloudStatus.Off, CloudStatus.Synced(42), CloudStatus.Syncing), seen)

        // Signing out mid-sync: the sync that outlived its account says nothing about the next state.
        phone.account.value = null
        runCurrent()
        assertEquals(CloudStatus.Off, phone.status)
        advanceUntilIdle()
        assertEquals(CloudStatus.Off, phone.status)
    }

    // ---- Deleting the backup ----------------------------------------------------------------------

    @Test
    fun deletingTheBackupStopsUploadsUntilTheAccountChanges() = runTest {
        val phone = Phone(this, alice)
        val a = phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(setOf(a.id), store.entries(alice.uid).keys)

        assertTrue(phone.backup.deleteBackup())
        assertTrue(store.entries(alice.uid).isEmpty())
        assertEquals("nothing is backed up any more", CloudStatus.Deleted, phone.status)
        // Between deleting the data and deleting the account, nothing goes up again.
        val b = phone.repo.logFeed(FeedType.FOOD, now - 20 * minute)
        advanceUntilIdle()
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertTrue(store.entries(alice.uid).isEmpty())
        assertEquals(CloudStatus.Deleted, phone.status)

        // Should the account deletion fail and the parent sign in again, everything is backed up again.
        phone.account.value = null
        advanceUntilIdle()
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(setOf(a.id, b.id), store.entries(alice.uid).keys)
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    @Test
    fun aDeletedBackupStaysDeletedAfterARestartUntilBackUpNow() = runTest {
        val phone = Phone(this, alice)
        val a = phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.backup.start()
        advanceUntilIdle()
        assertTrue(phone.backup.deleteBackup())
        assertEquals(alice.uid, phone.bookkeeping.values.deletedUid)

        // The account deletion failed, and the app is started again later, still signed in: nothing goes up by itself.
        val b = phone.repo.logFeed(FeedType.FOOD, now - 20 * minute)
        phone.restart()
        advanceUntilIdle()
        assertTrue(store.entries(alice.uid).isEmpty())
        assertEquals(CloudStatus.Deleted, phone.status)

        // "Back up now" is the parent asking for it: everything is backed up again.
        phone.backup.syncNow()
        assertEquals(setOf(a.id, b.id), store.entries(alice.uid).keys)
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertNull(phone.bookkeeping.values.deletedUid)
    }

    @Test
    fun aFailedDeletionSaysSo() = runTest {
        val phone = Phone(this, alice)
        phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.backup.start()
        advanceUntilIdle()
        store.failure = CloudError.OFFLINE
        assertFalse(phone.backup.deleteBackup())
        assertEquals(1, store.entries(alice.uid).size)

        // Uploads carry on, and the next sync backs everything up again in case the deletion stopped halfway.
        store.failure = null
        phone.backup.syncNow()
        assertEquals(alice.uid, phone.bookkeeping.values.fullUploadUid)
        assertTrue(phone.pending().isEmpty())
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    // ---- Review fixes -----------------------------------------------------------------------------

    @Test
    fun aPushPullsFirstSoAnOlderEditNeverOverwritesANewerOne() = runTest {
        val a = Phone(this, alice)
        val b = Phone(this, alice)
        val feed = a.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        a.backup.start()
        advanceUntilIdle()
        b.backup.start()
        advanceUntilIdle()
        assertEquals(feed, b.repo.get(feed.id))

        // Phone A edits the feed while offline.
        store.failure = CloudError.OFFLINE
        a.repo.update(feed.copy(startMillis = now - 31 * minute))
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, a.status)

        // Later, phone B edits it too and backs the edit up.
        store.failure = null
        clock.advanceBy(minute)
        val newer = b.repo.update(feed.copy(startMillis = now - 35 * minute))
        advanceUntilIdle()
        assertEquals(newer, store.entries(alice.uid)[feed.id])

        // Phone A comes back: the push after its next write pulls B's newer edit first, so A's older one is never sent.
        val diaper = a.repo.logDiaper(now - minute, pee = true, poop = false)
        advanceUntilIdle()
        assertEquals(newer, store.entries(alice.uid)[feed.id])
        assertEquals(newer, a.repo.get(feed.id))
        assertEquals("only the diaper went up", listOf(diaper.id), store.uploads.last())
        assertEquals(CloudStatus.Synced(now), a.status)
        assertTrue(a.pending().isEmpty())
        assertTrue(b.pending().isEmpty())
    }

    @Test
    fun aWriteToARowStillWaitingToGoUpIsPushedToo() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()

        // Offline, a sleep starts: its push fails.
        store.failure = CloudError.OFFLINE
        val sleep = phone.repo.startSleep(now - 30 * minute).getOrThrow()
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, phone.status)

        // Back online, the sleep ends: still one pending row, but the push follows without anything else asking.
        store.failure = null
        val ended = (phone.repo.endSleep(sleep.id, now - minute) as EndResult.Ended).entry
        advanceUntilIdle()
        assertEquals(ended, store.entries(alice.uid)[sleep.id])
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun aRowWrittenAgainWhileItsUploadIsOnItsWayGoesUpAgain() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()

        store.latencyMillis = 1_000
        val feed = phone.repo.logFeed(FeedType.FORMULA, now - 10 * minute)
        runCurrent()
        advanceTimeBy(CloudBackup.PUSH_DELAY_MILLIS + 500)
        runCurrent()
        assertTrue("the upload is on its way", store.entries(alice.uid).isEmpty())
        val edited = phone.repo.update(feed.copy(startMillis = now - 12 * minute))
        advanceUntilIdle()
        assertEquals(edited, store.entries(alice.uid)[feed.id])
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun switchingBackFromAnEmptyAccountSendsNothingAgain() = runTest {
        val phone = Phone(this, alice)
        phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.repo.logDiaper(now - 20 * minute, pee = true, poop = true)
        phone.backup.start()
        advanceUntilIdle()
        // A later pull stores a cursor for alice.
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(alice.uid, phone.bookkeeping.values.cursorUid)
        assertEquals(2, store.uploadedCount)

        // Bob's backup is empty: everything goes up to him.
        phone.account.value = bob
        advanceUntilIdle()
        assertEquals(2, store.entries(bob.uid).size)
        assertEquals(4, store.uploadedCount)

        // Back to alice: pulled from the start, so what her backup holds is cleared and nothing is sent again.
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(4, store.uploadedCount)
        assertTrue(phone.pending().isEmpty())
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    @Test
    fun anInterruptedPullResumesAfterTheLastStoredPage() = runTest {
        val entries = (0 until 1_202).map { Entry.feed(FeedType.FOOD, now - (it + 1) * 10_000L, id = "e-%04d".format(it)) }
        entries.chunked(CloudStore.UPLOAD_BATCH).forEach { store.upload(alice.uid, it) }
        store.pullsBeforeFailure = 1
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, phone.status)
        assertEquals(500, phone.all().size)
        assertEquals(500, store.documentsRead)

        store.pullsBeforeFailure = null
        phone.backup.syncNow()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(entries.map { it.id }.toSet(), phone.all().map { it.id }.toSet())
        assertEquals("the first page is not read again", 1_202, store.documentsRead)
    }

    @Test
    fun anEntryThatCouldNotBeStoredIsPulledAgain() = runTest {
        val entries = (0 until 3).map { Entry.feed(FeedType.FOOD, now - (it + 1) * 10 * minute, id = "e-$it") }
        store.upload(alice.uid, entries)
        val phone = Phone(this, alice)
        val sql = phone.db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER full BEFORE INSERT ON entries WHEN NEW.id = 'e-1' BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(CloudStatus.Error, phone.status)
        assertEquals(setOf("e-0", "e-2"), phone.all().map { it.id }.toSet())
        assertNull("the cursor stays before the page", phone.bookkeeping.values.cursorFor(alice.uid))

        sql.execSQL("DROP TRIGGER full")
        phone.backup.syncNow()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(entries.toSet(), phone.all().toSet())
    }

    @Test
    fun aSyncStopsSendingToAnAccountThatSignedOutMidway() = runTest {
        // The consent was recorded by an earlier sync, so the first call after the pull is the first batch.
        val phone = Phone(this, alice, InMemoryCloudBookkeepingStore(CloudBookkeeping(consentUid = alice.uid, consentVersion = BackupConsent.VERSION)))
        val rows = (0 until 900).map { Entry.feed(FeedType.FOOD, now - (it + 1) * 10_000L, id = "r-%03d".format(it)) }
        rows.forEach { phone.db.entryDao().write(EntryEntity.from(it, pendingSync = false)) }
        store.latencyMillis = 1_000
        phone.backup.start()
        runCurrent()
        // The pull takes a second, then the first batch is on its way when the account changes.
        advanceTimeBy(1_500)
        runCurrent()
        phone.account.value = bob
        advanceUntilIdle()
        assertEquals("only the batch already sent", CloudStore.UPLOAD_BATCH, store.entries(alice.uid).size)
        assertEquals(900, store.entries(bob.uid).size)
        assertTrue(phone.pending().isEmpty())
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    // ---- Consent and what a deletion leaves (docs/PRIVACY.md) ---------------------------------------

    @Test
    fun anAccountThatHasNotAgreedIsNotBackedUp() = runTest {
        val unagreed = alice.copy(consentVersion = null)
        val phone = Phone(this, unagreed)
        val feed = phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(CloudStatus.Paused, phone.status)
        assertEquals("nothing is read or sent", 0 to 0, store.pulls to store.uploads.size)
        assertTrue("nothing is flagged for the backend", phone.pending().isEmpty())
        assertTrue(store.consents.isEmpty())

        phone.backup.syncNow()
        assertEquals("not even when asked", CloudStatus.Paused, phone.status)
        assertTrue(store.uploads.isEmpty())

        // An older consent version counts as none.
        phone.account.value = alice.copy(consentVersion = BackupConsent.VERSION - 1)
        advanceUntilIdle()
        assertEquals(CloudStatus.Paused, phone.status)

        // Agreeing: the consent is recorded first, then everything logged so far is backed up.
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(mapOf(alice.uid to BackupConsent.VERSION), store.consents)
        assertEquals(setOf(feed.id), store.entries(alice.uid).keys)
    }

    @Test
    fun theConsentIsRecordedOncePerVersionAndDeletedWithTheBackup() = runTest {
        val phone = Phone(this, alice)
        phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.backup.start()
        advanceUntilIdle()
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.syncNow()
        assertEquals(1, store.consentWrites)
        assertEquals(BackupConsent.VERSION, phone.bookkeeping.values.consentFor(alice.uid))

        // A consent that cannot be recorded stops the sync before anything is sent.
        val other = Phone(this, bob)
        other.repo.logFeed(FeedType.FOOD, now - 10 * minute)
        store.consentFailure = CloudError.OTHER
        other.backup.start()
        advanceUntilIdle()
        assertEquals(CloudStatus.Error, other.status)
        assertTrue(store.entries(bob.uid).isEmpty())
        assertEquals("still pending for the next sync", 1, other.pending().size)
        store.consentFailure = null

        assertTrue(phone.backup.deleteBackup())
        assertTrue(store.consents.isEmpty())
        assertNull("recorded again when backing up again", phone.bookkeeping.values.consentFor(alice.uid))

        // Another phone on the account deleted the backup (and its consent record): this one's refused upload writes
        // the consent again at the next sync.
        other.backup.syncNow()
        assertEquals(BackupConsent.VERSION, other.bookkeeping.values.consentFor(bob.uid))
        store.consents.remove(bob.uid)
        store.uploadFailure = CloudError.OTHER
        other.repo.logFeed(FeedType.FORMULA, now - 5 * minute)
        advanceUntilIdle()
        assertEquals(CloudStatus.Error, other.status)
        assertNull(other.bookkeeping.values.consentFor(bob.uid))
        store.uploadFailure = null
        other.backup.syncNow()
        assertEquals(mapOf(bob.uid to BackupConsent.VERSION), store.consents)
        assertEquals(CloudStatus.Synced(now), other.status)
    }

    @Test
    fun aDeletedEntryGoesUpWithoutWhatWasLogged() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        phone.baby.value = twin
        val diaper = phone.repo.logDiaper(now - minute, pee = true, poop = true)
        advanceUntilIdle()
        assertTrue(store.entries(alice.uid).getValue(diaper.id).poop)

        phone.repo.delete(diaper.id)
        advanceUntilIdle()
        val stored = store.entries(alice.uid).getValue(diaper.id)
        val local = phone.repo.get(diaper.id)!!
        assertEquals(
            "only the id, kind, baby and write clock",
            Entry(id = diaper.id, kind = diaper.kind, startMillis = 0, updatedAtMillis = local.updatedAtMillis, deleted = true, babyId = twin),
            stored,
        )
        assertTrue("this phone keeps its own copy until it is purged", local.poop)
        assertTrue(phone.pending().isEmpty())

        // Another phone learns of the deletion.
        val second = Phone(this, alice)
        second.backup.start()
        advanceUntilIdle()
        assertTrue(second.repo.get(diaper.id)!!.deleted)
        assertTrue(second.all().none { it.id == diaper.id && !it.deleted })
    }
}
