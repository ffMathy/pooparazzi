package com.ffmathy.pooparazzi.core.cloud

import android.util.Log
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.model.forCloud
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The backup of the log and the babies to the signed-in account's family in [store] (BABIES_AND_BACKUP §4, FAMILIES).
 *
 * A sync runs whenever an account with a known family ([CloudAccount.familyId]) is signed in, one at a time, and
 * always pulls before it pushes:
 *
 * 1. The first sync for a family on this device (or for another family than the last, after joining or leaving one,
 *    or after signing out) flags every row and every baby for upload ("everything logged so far is backed up") and
 *    pulls from the start, so what the family already holds unchanged is cleared again and only differences are
 *    uploaded. Joining a family so merges this phone's log and babies into it.
 * 2. **Pull**: the babies, then the entries; pages of changes after their stored cursors, merged here (babies
 *    last-writer-wins per baby into the settings, which reach the watch; entries last-writer-wins with the per-baby
 *    conflict rules, forwarded to the watch). Each cursor is stored after each page, so an interrupted pull resumes
 *    where it stopped. Pulling first means a newer version another phone backed up is merged before this phone sends
 *    its own: an upload is a plain overwrite, and would otherwise put an older version over it.
 * 3. **Push**: the current version of the pending babies, then of the pending rows, in batches; each is cleared
 *    unless written again meanwhile.
 *    A deleted row goes up as a tombstone without its time and details ([forCloud]).
 *
 * Syncs run when an account signs in or its family changes, on [requestSync] (the app in the foreground, at most once a
 * minute) and [syncNow], and [pushDelayMillis] after a write leaves rows or babies pending. Nothing keeps a listener
 * open on the backend: a pull with nothing new costs two reads (babies and entries).
 *
 * Rows and babies are flagged for the backend only while an account is signed in (the repositories ask [account]);
 * signing out clears every flag, so soft deletes are purged as without a backup, and the next sign-in flags everything
 * again (1.). Signed in while the family is not known yet, the flags stay and nothing is sent.
 *
 * Nothing here throws: failures become [status] ([CloudError.OFFLINE] → [CloudStatus.Offline] and so on) and the rows
 * stay pending for the next sync.
 *
 * @param babies the babies' side on this phone (the settings).
 * @param scope where the background syncs run (the app's lifetime).
 * @param monotonicMillis a clock that never goes back, for the pull throttle.
 */
class CloudBackup(
    private val repository: RoomEntryRepository,
    private val babies: CloudBabies,
    private val store: CloudStore,
    private val account: StateFlow<CloudAccount?>,
    private val bookkeeping: CloudBookkeepingStore,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val pushDelayMillis: Long = PUSH_DELAY_MILLIS,
    private val pullIntervalMillis: Long = PULL_INTERVAL_MILLIS,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : CloudSync {

    private val state = MutableStateFlow<CloudStatus>(CloudStatus.Off)
    override val status: StateFlow<CloudStatus> = state.asStateFlow()

    /** One sync (or backup deletion) at a time. */
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    private val pullLock = Any()

    /** When the last pull started ([monotonicMillis]); guarded by [pullLock]. */
    private var lastPullAt: Long? = null

    /** The family syncs go to: the signed-in account's, or null (signed out, or the family not known yet). */
    private val familyId: String? get() = account.value?.familyId

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { followAccount() }
        scope.launch { followPending() }
    }

    override suspend fun syncNow() {
        // In the backup's own scope: leaving the screen that asked must not cut a sync short.
        scope.launch { sync(backUpAgain = true) }.join()
    }

    override fun requestSync() {
        if (!claimPull()) return
        scope.launch { sync() }
    }

    override suspend fun deleteBackup(): Boolean = scope.async {
        try {
            mutex.withLock {
                val signedIn = account.value ?: return@withLock false
                val family = signedIn.familyId ?: return@withLock false
                // Forgotten first: should the deletion stop halfway, a later sync for this family backs up everything.
                bookkeeping.update { it.forget(family) }
                store.deleteAll(signedIn.uid, family)
                // Kept on disk: should the account deletion fail and the app restart, nothing goes up again by itself.
                bookkeeping.update { it.copy(deletedFamilyId = family) }
                // Nothing is backed up for this account any more: the status must not keep saying it is.
                if (familyId == family) state.value = CloudStatus.Deleted
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete the backup", e)
            false
        }
    }.await()

    /** Shows where the account stands and starts a full sync whenever one signs in or its family changes. */
    private suspend fun followAccount() {
        account.map { it?.let { signedIn -> signedIn.uid to signedIn.familyId } }.distinctUntilChanged().collect { target ->
            val family = target?.second
            when {
                target == null -> state.value = CloudStatus.Off
                // Signed in, the family on its way: nothing to sync yet, and nothing to show but that it is coming.
                family == null -> state.value = CloudStatus.Syncing
                else -> {
                    state.value = lastSyncOf(family)?.let { CloudStatus.Synced(it) } ?: CloudStatus.Syncing
                    claimPull()
                }
            }
            // The bookkeeping first, under the sync lock, so the sync after it sees the account as it now is.
            scope.launch {
                mutex.withLock { accountChanged(target != null, family) }
                if (family != null) sync()
            }
        }
    }

    /**
     * Another account or family, or nobody, is signed in now: a deleted backup of a family no longer signed in is no
     * reason to hold anything back any more (while the family is not known yet, it is kept). Signed out, nothing is
     * pending for any backend: the flags are cleared (so deleted rows can be purged), and the next sign-in counts as the
     * first and flags everything again. Call with [mutex] held.
     */
    private suspend fun accountChanged(signedIn: Boolean, family: String?) {
        try {
            bookkeeping.update { values ->
                when {
                    !signedIn -> values.copy(deletedFamilyId = null, fullUploadFamilyId = null)
                    family == null -> values
                    else -> values.copy(deletedFamilyId = values.deletedFamilyId?.takeIf { it == family })
                }
            }
            if (!signedIn && account.value == null) {
                repository.clearCloudPending()
                babies.clearBabiesCloudPending()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not update the backup's bookkeeping", e)
        }
    }

    /**
     * Syncs [pushDelayMillis] after the last write that left rows or babies pending, while the family is known. Every
     * write to a pending row counts, also one that leaves their number as it was (ending a sleep that is still waiting
     * to go up, or a row written again while its upload was on its way). It pulls first like every sync (cheap when
     * nothing is new), so the upload never puts this phone's older version over a newer one in the backup.
     */
    @OptIn(FlowPreview::class)
    private suspend fun followPending() {
        combine(repository.observeCloudPending(), babies.observeCloudPendingBabies(), account) { rows, pendingBabies, signedIn ->
            val family = signedIn?.familyId
            if ((rows.count > 0 || pendingBabies.isNotEmpty()) && family != null) Triple(rows, pendingBabies, family) else null
        }
            .debounce(pushDelayMillis)
            .collect { due -> if (due != null) sync() }
    }

    private suspend fun lastSyncOf(family: String): Long? = try {
        bookkeeping.get().lastSyncFor(family)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the backup's bookkeeping", e)
        null
    }

    /** True (and counted as the latest pull) when no pull started in the last [pullIntervalMillis]. */
    private fun claimPull(): Boolean = synchronized(pullLock) {
        val now = monotonicMillis()
        val due = lastPullAt.let { it == null || now - it >= pullIntervalMillis }
        if (due) lastPullAt = now
        due
    }

    /** One sync; [backUpAgain] ("Back up now") also backs up a family whose backup was deleted. */
    private suspend fun sync(backUpAgain: Boolean = false) {
        try {
            mutex.withLock { syncLocked(backUpAgain) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backup sync failed", e)
        }
    }

    /** One sync for the current family. Call with [mutex] held. */
    private suspend fun syncLocked(backUpAgain: Boolean) {
        val signedIn = account.value
        if (signedIn == null) {
            state.value = CloudStatus.Off
            return
        }
        // The family is on its way: its arrival starts a sync.
        val family = signedIn.familyId ?: return
        val outcome = try {
            syncFamily(family, backUpAgain)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudException) {
            Log.w(TAG, "Backup sync failed: ${e.error}", e)
            e.error.toStatus()
        } catch (e: Exception) {
            Log.w(TAG, "Backup sync failed", e)
            CloudStatus.Error
        }
        // A sync that outlived its family (signed out, or another family meanwhile) says nothing about the new one.
        if (familyId == family) state.value = outcome
    }

    /** Pulls and pushes for [family], unless its backup was deleted; returns the status to show. */
    private suspend fun syncFamily(family: String, backUpAgain: Boolean): CloudStatus {
        if (bookkeeping.get().isDeleted(family)) {
            // Its backup was deleted: nothing goes up for it again by itself, and the status says so.
            if (!backUpAgain) return CloudStatus.Deleted
            bookkeeping.update { it.copy(deletedFamilyId = null) }
        }
        state.value = CloudStatus.Syncing
        val firstForFamily = bookkeeping.get().fullUploadFamilyId != family
        if (firstForFamily) {
            // Flagged before anything is sent, so an interrupted first sync leaves the rows and babies pending.
            repository.markAllForCloud()
            babies.markAllBabiesForCloud()
            // Pulled from the start: cursors this device kept for the family from an earlier time would skip the
            // unchanged documents, so nothing would be cleared and the whole log would go up again.
            bookkeeping.update {
                it.copy(fullUploadFamilyId = family, cursorFamilyId = family, cursor = null, babiesCursor = null)
            }
        }
        pullBabies(family)
        pull(family)
        // The babies first: another phone then knows a new baby's name by the time its entries arrive.
        pushBabies(family)
        push(family)
        val now = clock.now()
        bookkeeping.update { it.copy(lastSyncFamilyId = family, lastSyncAtMillis = now) }
        return CloudStatus.Synced(now)
    }

    /** Merges every change to the family's babies after the stored cursor, a page at a time. */
    private suspend fun pullBabies(family: String) {
        synchronized(pullLock) { lastPullAt = monotonicMillis() }
        pages(
            family,
            start = bookkeeping.get().babiesCursorFor(family),
            fetch = { cursor -> store.babiesAfter(family, cursor, CloudStore.PAGE_SIZE) },
            apply = { babies.applyCloudBabies(it) },
            save = { at -> bookkeeping.update { it.withBabiesCursor(family, at) } },
        )
    }

    /** Merges every change to the family's log after the stored cursor, a page at a time. */
    private suspend fun pull(family: String) {
        pages(
            family,
            start = bookkeeping.get().cursorFor(family),
            fetch = { cursor -> store.changesAfter(family, cursor, CloudStore.PAGE_SIZE) },
            apply = { repository.applyCloudEntries(it) },
            save = { at -> bookkeeping.update { it.withCursor(family, at) } },
        )
    }

    /** Reads pages after [start] until the last, applying each and then storing its cursor. */
    private suspend fun <T> pages(
        family: String,
        start: CloudCursor?,
        fetch: suspend (CloudCursor?) -> CloudPage<T>,
        apply: suspend (List<T>) -> Unit,
        save: suspend (CloudCursor) -> Unit,
    ) {
        var cursor = start
        repeat(MAX_PAGES) {
            checkFamily(family)
            val page = fetch(cursor)
            if (page.items.isNotEmpty()) apply(page.items)
            val last = page.last
            if (last == null || last == cursor) return
            cursor = last
            save(last)
            if (page.documents < CloudStore.PAGE_SIZE) return
        }
    }

    /** Uploads the pending rows in batches until none are left. */
    private suspend fun push(family: String) {
        repeat(MAX_PUSH_BATCHES) {
            checkFamily(family)
            val batch = repository.cloudPending(CloudStore.UPLOAD_BATCH)
            if (batch.isEmpty()) return
            // A deleted row goes up as a tombstone without what was logged (forCloud); the flags follow the row itself.
            store.upload(family, batch.map { it.forCloud() })
            // Nothing cleared: every row was written again meanwhile. Stop rather than chase them: the next sync goes on.
            if (repository.markCloudSynced(batch) == 0) return
        }
    }

    /** Uploads the pending babies (a handful: one batch, in practice). */
    private suspend fun pushBabies(family: String) {
        repeat(MAX_PUSH_BATCHES) {
            checkFamily(family)
            val batch = babies.cloudPendingBabies().take(CloudStore.UPLOAD_BATCH)
            if (batch.isEmpty()) return
            store.uploadBabies(family, batch)
            if (babies.markBabiesCloudSynced(batch) == 0) return
        }
    }

    /** Stops a sync whose family changed meanwhile (or signed out): nothing more is read or sent for it. */
    private fun checkFamily(family: String) {
        if (familyId != family) throw CloudException(CloudError.SIGNED_OUT)
    }

    companion object {
        private const val TAG = "CloudBackup"

        /** How long after the last write that left rows or babies pending the push runs. */
        const val PUSH_DELAY_MILLIS = 2_000L

        /** [requestSync] pulls at most this often. */
        const val PULL_INTERVAL_MILLIS = 60_000L

        /** Bounds of one sync, far beyond any real log (5 million changes, 400 000 uploads). */
        private const val MAX_PAGES = 10_000
        private const val MAX_PUSH_BATCHES = 1_000

        private fun CloudError.toStatus(): CloudStatus = when (this) {
            CloudError.OFFLINE -> CloudStatus.Offline
            CloudError.SIGNED_OUT -> CloudStatus.Off
            CloudError.OTHER -> CloudStatus.Error
        }
    }
}
