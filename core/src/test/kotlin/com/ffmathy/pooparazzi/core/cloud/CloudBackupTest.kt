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
import com.ffmathy.pooparazzi.core.settings.DataStoreSettingsRepository
import com.ffmathy.pooparazzi.core.settings.SettingsJson
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
 * The backup engine against an in-memory backend, in virtual time (BABIES_AND_BACKUP §4, FAMILIES). Each [Phone] has
 * its own database, settings, watch and account; every phone shares [store], and phones whose accounts are in one
 * family share its backup.
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
    private val alice = CloudAccount("uid-alice", "alice@example.com", "family-alice")
    private val bob = CloudAccount("uid-bob", "bob@example.com", "family-bob")
    private val twin = "baby-2"

    private val scopes = mutableListOf<CoroutineScope>()

    /** The account's family (every test account has one, unless a test takes it away). */
    private val CloudAccount.fid: String get() = checkNotNull(familyId)

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
        val repo = RoomEntryRepository(db, clock, watch, selectedBaby = baby, cloudBackup = { account.value != null })

        /** The settings, mirrored to the same watch, flagging babies while signed in, as in the app. */
        val settings = DataStoreSettingsRepository(InMemoryPreferencesDataStore(), clock, watch, cloudBackup = { account.value != null })

        /**
         * The app's lifetime: on the test's dispatcher, but not the test's background scope, whose work
         * `advanceUntilIdle` does not wait for. Cancelled after the test.
         */
        private var scope = newScope()
        var backup = newBackup()
            private set

        private fun newScope() = CoroutineScope(test.coroutineContext + SupervisorJob()).also { scopes += it }

        private fun newBackup() =
            CloudBackup(repo, settings, store, account, bookkeeping, clock, scope, monotonicMillis = { test.testScheduler.currentTime })

        init {
            adoptBabies()
        }

        /** The phone adopts babies from their entries, as `CoreGraph.adoptBabiesFromEntries` does. */
        private fun adoptBabies() {
            scope.launch { repo.observeBabyIds().collect { settings.ensureBabies(it) } }
        }

        /** The app starting again: a new backup on the same database and bookkeeping (whatever it kept in memory is lost). */
        fun restart() {
            scope.cancel()
            scope = newScope()
            adoptBabies()
            backup = newBackup()
            backup.start()
        }

        /** Moves the signed-in account to [familyId] (joining or leaving a family; null: not known yet). */
        fun moveTo(familyId: String?) {
            account.value = checkNotNull(account.value).copy(familyId = familyId)
        }

        suspend fun babies(): List<Baby> = settings.current().babies
        suspend fun pendingBabies(): List<Baby> = settings.cloudPendingBabies()

        /** The babies as this phone's watch last got them, through the JSON of the `/settings` item. */
        fun watchBabies(): List<Baby>? = watch.pushedSettings.lastOrNull()?.let { SettingsJson.decodeOrNull(SettingsJson.encode(it)) }?.babies

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
        assertEquals(mapOf(feed.id to feed, old.id to old, diaper.id to diaper), store.entries(alice.fid))
        assertEquals("the baby's id is uploaded, never a name", twin, store.entries(alice.fid).getValue(diaper.id).babyId)
        assertTrue(phone.pending().isEmpty())
        assertEquals("one pull (nothing there yet), one upload", 1 to 1, store.pulls to store.uploads.size)
        assertEquals(alice.fid, phone.bookkeeping.values.fullUploadFamilyId)
        assertEquals(now, phone.bookkeeping.values.lastSyncFor(alice.fid))
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
        store.upload(bob.fid, listOf(bobs))
        phone.account.value = bob
        advanceUntilIdle()
        assertEquals(setOf(a.id, b.id, bobs.id), store.entries(bob.fid).keys)
        assertEquals(bobs, phone.repo.get(bobs.id))
        assertEquals(bob.fid, phone.bookkeeping.values.fullUploadFamilyId)
        assertEquals(bob.fid, phone.bookkeeping.values.cursorFamilyId)

        // Back to alice: pulled from the start (the cursor is bob's), which clears what her backup holds.
        val sent = store.uploadedCount
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(listOf(bobs.id), store.uploads.last())
        assertEquals(sent + 1, store.uploadedCount)
        assertEquals(setOf(a.id, b.id, bobs.id), store.entries(alice.fid).keys)
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
        entries.chunked(CloudStore.UPLOAD_BATCH).forEach { store.upload(alice.fid, it) }
        store.putRaw(alice.fid, "unreadable", "{not an entry")
        val seeded = store.uploads.size

        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(entries.map { it.id }.toSet(), phone.all().map { it.id }.toSet())
        assertEquals("500 + 500 + 203", 3, store.pulls)
        assertEquals(1_203, store.documentsRead)
        assertEquals("nothing to send back", seeded, store.uploads.size)
        assertEquals("unreadable", phone.bookkeeping.values.cursorFor(alice.fid)!!.documentId)

        // Nothing new: one empty page.
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(4, store.pulls)
        assertEquals(1_203, store.documentsRead)

        // Only what changed after the cursor is read.
        val deleted = entries[0].copy(deleted = true, updatedAtMillis = now)
        store.upload(alice.fid, listOf(deleted))
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
        entries.chunked(CloudStore.UPLOAD_BATCH).forEach { store.upload(alice.fid, it) }
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
        assertFalse(store.entries(alice.fid).containsKey(feed.id))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(feed, store.entries(alice.fid)[feed.id])
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
        assertEquals(fromWatch, store.entries(alice.fid)["w-pee"])
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
        assertTrue(store.entries(alice.fid).getValue(feed.id).deleted)
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
        assertEquals("flagged before anything was sent", alice.fid, phone.bookkeeping.values.fullUploadFamilyId)
        assertNull(phone.bookkeeping.values.lastSyncFor(alice.fid))

        phone.repo.logFeed(FeedType.FOOD, now - 2 * minute)
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, phone.status)
        assertEquals(2, phone.pending().size)

        store.failure = null
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(2, store.entries(alice.fid).size)
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
        assertEquals(later, store.entries(alice.fid)[later.id])
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun theStatusFollowsTheAccountAndTheSync() = runTest {
        store.latencyMillis = 1_000
        val phone = Phone(this, alice, InMemoryCloudBookkeepingStore(CloudBookkeeping(lastSyncFamilyId = alice.fid, lastSyncAtMillis = 42)))
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
        assertEquals(setOf(a.id), store.entries(alice.fid).keys)

        assertTrue(phone.backup.deleteBackup())
        assertTrue(store.entries(alice.fid).isEmpty())
        assertEquals("nothing is backed up any more", CloudStatus.Deleted, phone.status)
        // Between deleting the data and deleting the account, nothing goes up again.
        val b = phone.repo.logFeed(FeedType.FOOD, now - 20 * minute)
        advanceUntilIdle()
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertTrue(store.entries(alice.fid).isEmpty())
        assertEquals(CloudStatus.Deleted, phone.status)

        // Should the account deletion fail and the parent sign in again, everything is backed up again.
        phone.account.value = null
        advanceUntilIdle()
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(setOf(a.id, b.id), store.entries(alice.fid).keys)
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    @Test
    fun aDeletedBackupStaysDeletedAfterARestartUntilBackUpNow() = runTest {
        val phone = Phone(this, alice)
        val a = phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.backup.start()
        advanceUntilIdle()
        assertTrue(phone.backup.deleteBackup())
        assertEquals(alice.fid, phone.bookkeeping.values.deletedFamilyId)

        // The account deletion failed, and the app is started again later, still signed in: nothing goes up by itself.
        val b = phone.repo.logFeed(FeedType.FOOD, now - 20 * minute)
        phone.restart()
        advanceUntilIdle()
        assertTrue(store.entries(alice.fid).isEmpty())
        assertEquals(CloudStatus.Deleted, phone.status)

        // "Back up now" is the parent asking for it: everything is backed up again.
        phone.backup.syncNow()
        assertEquals(setOf(a.id, b.id), store.entries(alice.fid).keys)
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertNull(phone.bookkeeping.values.deletedFamilyId)
    }

    @Test
    fun aFailedDeletionSaysSo() = runTest {
        val phone = Phone(this, alice)
        phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.backup.start()
        advanceUntilIdle()
        store.failure = CloudError.OFFLINE
        assertFalse(phone.backup.deleteBackup())
        assertEquals(1, store.entries(alice.fid).size)

        // Uploads carry on, and the next sync backs everything up again in case the deletion stopped halfway.
        store.failure = null
        phone.backup.syncNow()
        assertEquals(alice.fid, phone.bookkeeping.values.fullUploadFamilyId)
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
        assertEquals(newer, store.entries(alice.fid)[feed.id])

        // Phone A comes back: the push after its next write pulls B's newer edit first, so A's older one is never sent.
        val diaper = a.repo.logDiaper(now - minute, pee = true, poop = false)
        advanceUntilIdle()
        assertEquals(newer, store.entries(alice.fid)[feed.id])
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
        assertEquals(ended, store.entries(alice.fid)[sleep.id])
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
        assertTrue("the upload is on its way", store.entries(alice.fid).isEmpty())
        val edited = phone.repo.update(feed.copy(startMillis = now - 12 * minute))
        advanceUntilIdle()
        assertEquals(edited, store.entries(alice.fid)[feed.id])
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
        assertEquals(alice.fid, phone.bookkeeping.values.cursorFamilyId)
        assertEquals(2, store.uploadedCount)

        // Bob's backup is empty: everything goes up to him.
        phone.account.value = bob
        advanceUntilIdle()
        assertEquals(2, store.entries(bob.fid).size)
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
        entries.chunked(CloudStore.UPLOAD_BATCH).forEach { store.upload(alice.fid, it) }
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
        store.upload(alice.fid, entries)
        val phone = Phone(this, alice)
        val sql = phone.db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER full BEFORE INSERT ON entries WHEN NEW.id = 'e-1' BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(CloudStatus.Error, phone.status)
        assertEquals(setOf("e-0", "e-2"), phone.all().map { it.id }.toSet())
        assertNull("the cursor stays before the page", phone.bookkeeping.values.cursorFor(alice.fid))

        sql.execSQL("DROP TRIGGER full")
        phone.backup.syncNow()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(entries.toSet(), phone.all().toSet())
    }

    @Test
    fun aSyncStopsSendingToAnAccountThatSignedOutMidway() = runTest {
        val phone = Phone(this, alice)
        val rows = (0 until 900).map { Entry.feed(FeedType.FOOD, now - (it + 1) * 10_000L, id = "r-%03d".format(it)) }
        rows.forEach { phone.db.entryDao().write(EntryEntity.from(it, pendingSync = false)) }
        store.latencyMillis = 1_000
        phone.backup.start()
        runCurrent()
        // The pulls (babies, entries) and the babies' upload take a second each, then the first batch of entries is on
        // its way when the account changes.
        advanceTimeBy(3_500)
        runCurrent()
        phone.account.value = bob
        advanceUntilIdle()
        assertEquals("only the batch already sent", CloudStore.UPLOAD_BATCH, store.entries(alice.fid).size)
        assertEquals(900, store.entries(bob.fid).size)
        assertTrue(phone.pending().isEmpty())
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    // ---- Families -------------------------------------------------------------------------------

    @Test
    fun twoPhonesInOneFamilySeeEachOthersEntriesAndBabies() = runTest {
        val family = "family-shared"
        val a = Phone(this, alice.copy(familyId = family))
        val b = Phone(this, bob.copy(familyId = family))
        a.settings.setBabyName("Noa")
        val ida = a.settings.addBaby()
        a.settings.renameBaby(ida.id, "Ida")
        val noasFeed = a.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        a.baby.value = ida.id
        val idasDiaper = a.repo.logDiaper(now - 20 * minute, pee = true, poop = false)
        a.backup.start()
        advanceUntilIdle()
        assertEquals(listOf("Noa", "Ida"), store.babies(family).values.sortedBy { it.updatedAtMillis }.map { it.name })
        assertEquals(a.babies().toSet(), store.babies(family).values.toSet())
        assertTrue(a.pendingBabies().isEmpty())

        // The second member's phone, its first baby unnamed: the family's name for it is taken, nothing sent back.
        val bobsFeed = b.repo.logFeed(FeedType.FOOD, now - 10 * minute)
        val babyUploads = store.babyUploads.size
        b.backup.start()
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), b.status)
        assertEquals(a.babies(), b.babies())
        assertEquals("the names reach the watch", b.babies(), b.watchBabies())
        assertEquals(setOf(noasFeed.id, idasDiaper.id, bobsFeed.id), b.all().map { it.id }.toSet())
        assertEquals("the family had every baby already", babyUploads, store.babyUploads.size)
        assertTrue(b.pendingBabies().isEmpty())
        assertTrue(b.pending().isEmpty())

        // A rename on one phone reaches the other phone and its watch.
        clock.advanceBy(minute)
        b.settings.renameBaby(ida.id, "Idun")
        advanceUntilIdle()
        assertEquals("Idun", store.babies(family).getValue(ida.id).name)
        val sent = store.babyUploads.size
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        a.backup.requestSync()
        advanceUntilIdle()
        assertEquals(listOf("Noa", "Idun"), a.babies().map { it.name })
        assertEquals(a.babies(), a.watchBabies())
        assertEquals(b.babies(), a.babies())
        assertTrue("taken, not echoed", a.pendingBabies().isEmpty())
        assertEquals(sent, store.babyUploads.size)
        assertEquals(b.all(), a.all())
        assertEquals(bobsFeed, a.watch.items[bobsFeed.id])
    }

    @Test
    fun anOlderRenameFromAnOfflinePhoneLosesToANewerOne() = runTest {
        val family = "family-shared"
        val a = Phone(this, alice.copy(familyId = family))
        val b = Phone(this, bob.copy(familyId = family))
        val ida = a.settings.addBaby()
        a.backup.start()
        advanceUntilIdle()
        b.backup.start()
        advanceUntilIdle()

        store.failure = CloudError.OFFLINE
        clock.advanceBy(minute)
        a.settings.renameBaby(ida.id, "Older")
        advanceUntilIdle()
        assertEquals(CloudStatus.Offline, a.status)
        assertEquals(listOf("Older"), a.pendingBabies().map { it.name })

        store.failure = null
        clock.advanceBy(minute)
        b.settings.renameBaby(ida.id, "Newer")
        advanceUntilIdle()

        // A comes back: its sync pulls the newer name first, so its older one is never sent.
        a.backup.syncNow()
        assertEquals("Newer", store.babies(family).getValue(ida.id).name)
        assertEquals("Newer", a.babies().single { it.id == ida.id }.name)
        assertTrue(a.pendingBabies().isEmpty())
        assertTrue(store.babyUploads.none { batch -> batch.any { it.name == "Older" } })
    }

    @Test
    fun joiningAFamilyMergesBothPhonesBabiesAndLogs() = runTest {
        val shared = alice.fid
        val a = Phone(this, alice)
        a.settings.setBabyName("Noa")
        val ida = a.settings.addBaby()
        a.settings.renameBaby(ida.id, "Ida")
        val alicesFeed = a.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        a.backup.start()
        advanceUntilIdle()

        // Bob has a family of his own, with his first baby named later than alice's.
        clock.advanceBy(minute)
        val b = Phone(this, bob)
        b.settings.setBabyName("Noah")
        val bo = b.settings.addBaby()
        b.settings.renameBaby(bo.id, "Bo")
        b.baby.value = bo.id
        val bobsDiaper = b.repo.logDiaper(now - 5 * minute, pee = false, poop = true)
        b.backup.start()
        advanceUntilIdle()
        assertEquals(setOf(bobsDiaper.id), store.entries(bob.fid).keys)

        // He joins alice's family: like another account, everything of his goes up to it, and its log comes down.
        b.moveTo(shared)
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), b.status)
        assertEquals(setOf(alicesFeed.id, bobsDiaper.id), store.entries(shared).keys)
        assertEquals(setOf(Baby.FIRST_ID, ida.id, bo.id), store.babies(shared).keys)
        assertEquals("the two first babies are one; the later name wins", "Noah", store.babies(shared).getValue(Baby.FIRST_ID).name)
        assertEquals(listOf("Noah", "Bo", "Ida"), b.babies().map { it.name })
        assertEquals(b.babies(), b.watchBabies())
        assertEquals(alicesFeed, b.watch.items[alicesFeed.id])
        assertEquals(shared, b.bookkeeping.values.fullUploadFamilyId)
        assertEquals(shared, b.bookkeeping.values.cursorFamilyId)
        assertTrue(b.pending().isEmpty())
        assertTrue(b.pendingBabies().isEmpty())
        assertEquals("his old family keeps its copy", setOf(bobsDiaper.id), store.entries(bob.fid).keys)

        // Alice gets his babies and log at her next pull.
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        a.backup.requestSync()
        advanceUntilIdle()
        assertEquals(b.all(), a.all())
        assertEquals(listOf("Noah", "Ida", "Bo"), a.babies().map { it.name })
        assertEquals(a.babies(), a.watchBabies())
        assertTrue(a.pendingBabies().isEmpty())
    }

    @Test
    fun leavingAFamilyBacksEverythingUpToTheNewOne() = runTest {
        val shared = "family-shared"
        val a = Phone(this, alice.copy(familyId = shared))
        a.settings.setBabyName("Noa")
        val feed = a.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        a.backup.start()
        advanceUntilIdle()
        val b = Phone(this, bob.copy(familyId = shared))
        val diaper = b.repo.logDiaper(now - 20 * minute, pee = true, poop = false)
        b.backup.start()
        advanceUntilIdle()

        // Bob leaves: he gets a new, empty family of his own, and his phone keeps the log and uploads it there.
        val own = "family-bob-new"
        b.moveTo(own)
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), b.status)
        assertEquals(setOf(feed.id, diaper.id), store.entries(own).keys)
        assertEquals(listOf("Noa"), store.babies(own).values.map { it.name })
        assertEquals(own, b.bookkeeping.values.fullUploadFamilyId)

        // From now on what he logs goes to his new family only.
        val later = b.repo.logFeed(FeedType.FOOD, now - minute)
        advanceUntilIdle()
        assertEquals(later, store.entries(own)[later.id])
        assertFalse(store.entries(shared).containsKey(later.id))
    }

    @Test
    fun aFamilySwitchMidSyncIsSafe() = runTest {
        val phone = Phone(this, alice)
        val rows = (0 until 900).map { Entry.feed(FeedType.FOOD, now - (it + 1) * 10_000L, id = "r-%03d".format(it)) }
        rows.forEach { phone.db.entryDao().write(EntryEntity.from(it, pendingSync = false)) }
        phone.settings.setBabyName("Noa")
        store.latencyMillis = 1_000
        phone.backup.start()
        runCurrent()
        // The pulls (babies, entries) and the babies' upload take a second each, then the first batch of entries is on
        // its way when the family changes.
        advanceTimeBy(3_500)
        runCurrent()
        phone.moveTo("family-other")
        advanceUntilIdle()
        assertEquals("only the batch already sent", CloudStore.UPLOAD_BATCH, store.entries(alice.fid).size)
        assertEquals(900, store.entries("family-other").size)
        assertEquals(listOf("Noa"), store.babies("family-other").values.map { it.name })
        assertEquals("family-other", phone.bookkeeping.values.cursorFamilyId)
        assertEquals("family-other", phone.bookkeeping.values.lastSyncFamilyId)
        assertTrue(phone.pending().isEmpty())
        assertTrue(phone.pendingBabies().isEmpty())
        assertEquals(CloudStatus.Synced(now), phone.status)
    }

    @Test
    fun withoutAFamilyYetNothingIsSentAndTheFlagsStay() = runTest {
        val phone = Phone(this, alice.copy(familyId = null))
        val feed = phone.repo.logFeed(FeedType.FORMULA, now - minute)
        phone.settings.setBabyName("Noa")
        phone.backup.start()
        advanceUntilIdle()
        phone.backup.syncNow()
        advanceUntilIdle()
        assertEquals("the family is on its way", CloudStatus.Syncing, phone.status)
        assertEquals(0 to 0, store.pulls + store.babyPulls to store.uploads.size + store.babyUploads.size)
        assertEquals(listOf(feed), phone.pending())
        assertEquals(listOf("Noa"), phone.pendingBabies().map { it.name })
        assertFalse("no family, nothing to delete", phone.backup.deleteBackup())

        phone.moveTo(alice.fid)
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(feed, store.entries(alice.fid)[feed.id])
        assertEquals("Noa", store.babies(alice.fid).getValue(Baby.FIRST_ID).name)
        assertTrue(phone.pendingBabies().isEmpty())
    }

    @Test
    fun takingTheAccountOutOfItsFamilyStopsUploadsToItUntilAnotherFamily() = runTest {
        val phone = Phone(this, alice)
        store.addMember(alice.fid, alice.uid)
        store.addMember(alice.fid, bob.uid)
        val a = phone.repo.logFeed(FeedType.FORMULA, now - 30 * minute)
        phone.backup.start()
        advanceUntilIdle()

        assertTrue(phone.backup.deleteBackup())
        assertEquals(listOf(alice.uid to alice.fid), store.deletions)
        assertEquals("bob is still in it: the family's log stays", setOf(bob.uid), store.members(alice.fid))
        assertEquals(setOf(a.id), store.entries(alice.fid).keys)
        assertEquals(CloudStatus.Deleted, phone.status)
        assertEquals(alice.fid, phone.bookkeeping.values.deletedFamilyId)

        // The app starts again: the family is unknown at first, then the same one again. Nothing goes up by itself.
        val b = phone.repo.logFeed(FeedType.FOOD, now - 20 * minute)
        phone.moveTo(null)
        phone.restart()
        advanceUntilIdle()
        phone.moveTo(alice.fid)
        advanceUntilIdle()
        assertEquals(CloudStatus.Deleted, phone.status)
        assertFalse(store.entries(alice.fid).containsKey(b.id))
        assertEquals(alice.fid, phone.bookkeeping.values.deletedFamilyId)

        // Another family is another backup: everything goes up to it.
        phone.moveTo("family-new")
        advanceUntilIdle()
        assertEquals(CloudStatus.Synced(now), phone.status)
        assertEquals(setOf(a.id, b.id), store.entries("family-new").keys)
        assertNull(phone.bookkeeping.values.deletedFamilyId)
    }

    @Test
    fun signingOutClearsTheBabiesFlagsAndSigningInFlagsThemAgain() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        store.failure = CloudError.OFFLINE
        phone.settings.setBabyName("Noa")
        advanceUntilIdle()
        assertEquals(listOf("Noa"), phone.pendingBabies().map { it.name })

        phone.account.value = null
        advanceUntilIdle()
        assertTrue("signed out, nothing is pending for a backend", phone.pendingBabies().isEmpty())
        val ida = phone.settings.addBaby()
        assertTrue(phone.pendingBabies().isEmpty())

        store.failure = null
        phone.account.value = alice
        advanceUntilIdle()
        assertEquals(setOf(Baby.FIRST_ID, ida.id), store.babies(alice.fid).keys)
        assertEquals("Noa", store.babies(alice.fid).getValue(Baby.FIRST_ID).name)
        assertTrue(phone.pendingBabies().isEmpty())
    }

    @Test
    fun aBabyKnownOnlyFromItsEntriesIsAdoptedAndTakesItsNameLater() = runTest {
        store.upload(alice.fid, listOf(Entry.feed(FeedType.FOOD, now - 10 * minute, id = "twins-feed", babyId = twin)))
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        assertEquals(listOf(Baby.FIRST_ID, twin), phone.babies().map { it.id })
        assertEquals("adopted unstamped", Baby(twin), phone.babies()[1])
        assertFalse("nothing the family lacks: not uploaded", store.babies(alice.fid).containsKey(twin))
        assertTrue(phone.pendingBabies().isEmpty())

        // Another phone backs up its name: it wins over the adopted, unnamed baby.
        store.uploadBabies(alice.fid, listOf(Baby(twin, "Ida", now)))
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(Baby(twin, "Ida", now), phone.babies()[1])
        assertEquals(phone.babies(), phone.watchBabies())
        assertTrue(phone.pendingBabies().isEmpty())
    }

    @Test
    fun babiesArePulledByTheirOwnCursor() = runTest {
        val phone = Phone(this, alice)
        phone.backup.start()
        advanceUntilIdle()
        assertNull("pulled before anything was there", phone.bookkeeping.values.babiesCursorFor(alice.fid))
        assertEquals(listOf(listOf(Baby(Baby.FIRST_ID))), store.babyUploads)

        store.putRawBaby(alice.fid, "broken", "{not a baby")
        store.uploadBabies(alice.fid, listOf(Baby("b-new", "Alma", now)))
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(listOf(Baby.FIRST_ID, "b-new"), phone.babies().map { it.id })
        assertEquals("b-new", phone.bookkeeping.values.babiesCursorFor(alice.fid)?.documentId)
        assertEquals("its own first baby, read back unchanged, is not sent again", 2, store.babyUploads.size)

        // Nothing new: one empty page, no upload.
        val pulls = store.babyPulls
        advanceTimeBy(CloudBackup.PULL_INTERVAL_MILLIS)
        phone.backup.requestSync()
        advanceUntilIdle()
        assertEquals(pulls + 1, store.babyPulls)
        assertEquals(2, store.babyUploads.size)
        assertEquals("b-new", phone.bookkeeping.values.babiesCursorFor(alice.fid)?.documentId)
        assertEquals("the entries' cursor is its own", phone.bookkeeping.values.cursorFor(alice.fid), phone.bookkeeping.values.cursor)
    }
}
