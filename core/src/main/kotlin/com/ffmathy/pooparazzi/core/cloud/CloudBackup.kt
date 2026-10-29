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
 * The backup of the log to the signed-in account's [store] (BABIES_AND_BACKUP §4.3–4.4).
 *
 * A sync runs whenever an account is signed in and has agreed to the backup ([CloudAccount.consented]), one at a time,
 * and always pulls before it pushes. Signed in without the consent, nothing is read or sent ([CloudStatus.Paused]):
 *
 * 1. The first sync for an account on this device (or for another account than the last, or after signing out) flags
 *    every row for upload ("everything logged so far is backed up") and pulls from the start, so rows the backend
 *    already holds unchanged are cleared again and only differences are uploaded.
 * 2. **Pull**: pages of changes after the stored cursor, merged into the database (last-writer-wins and the per-baby
 *    conflict rules, forwarded to the watch). The cursor is stored after each page, so an interrupted pull resumes
 *    where it stopped. Pulling first means a newer version another phone backed up is merged before this phone sends
 *    its own: an upload is a plain overwrite, and would otherwise put an older version over it.
 * 3. **Consent**: before the first push under a consent version, the consent is recorded in the backend
 *    ([CloudStore.recordConsent]); should that fail, nothing is sent.
 * 4. **Push**: the current version of the pending rows, in batches; each row is cleared unless written again meanwhile.
 *    A deleted row goes up as a tombstone without its time and details ([forCloud]).
 *
 * Syncs run when an account signs in, on [requestSync] (the app in the foreground, at most once a minute) and
 * [syncNow], and [pushDelayMillis] after a write leaves rows pending. Nothing keeps a listener open on the backend: a
 * pull with nothing new costs one read.
 *
 * Rows are flagged for the backend only while an agreed account is signed in (the repository asks [account]); signing
 * out (or an account that has not agreed) clears every flag, so soft deletes are purged as without a backup, and the next sign-in flags everything again (1.).
 *
 * Nothing here throws: failures become [status] ([CloudError.OFFLINE] → [CloudStatus.Offline] and so on) and the rows
 * stay pending for the next sync.
 *
 * @param scope where the background syncs run (the app's lifetime).
 * @param monotonicMillis a clock that never goes back, for the pull throttle.
 */
class CloudBackup(
    private val repository: RoomEntryRepository,
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
                // Also for an account that has not agreed to the backup: what it backed up before must go all the same.
                val uid = account.value?.uid ?: return@withLock false
                // Forgotten first: should the deletion stop halfway, a later sync for this account backs up everything.
                bookkeeping.update { it.forget(uid) }
                store.deleteAll(uid)
                // Kept on disk: should the account deletion fail and the app restart, nothing goes up again by itself.
                bookkeeping.update { it.copy(deletedUid = uid) }
                // Nothing is backed up for this account any more: the status must not keep saying it is.
                if (account.value?.uid == uid) state.value = CloudStatus.Deleted
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete the backup", e)
            false
        }
    }.await()

    /** Shows where the account stands and starts a full sync whenever one signs in. */
    private suspend fun followAccount() {
        account.map { it?.uid to (it?.consented == true) }.distinctUntilChanged().collect { (signedIn, consented) ->
            // The account the backup works for: none while signed out, or signed in without the consent.
            val uid = signedIn.takeIf { consented }
            if (signedIn == null) {
                state.value = CloudStatus.Off
            } else if (uid == null) {
                state.value = CloudStatus.Paused
            } else {
                lastSyncOf(uid)?.let { state.value = CloudStatus.Synced(it) }
                claimPull()
            }
            // The bookkeeping first, under the sync lock, so the sync after it sees the account as it now is.
            scope.launch {
                mutex.withLock { accountChanged(uid) }
                if (uid != null) sync()
            }
        }
    }

    /**
     * Another account, or nobody, is signed in now: a deleted backup of an account no longer signed in is no reason to
     * hold anything back any more. Signed out, nothing is pending for any backend: the flags are cleared (so deleted rows
     * can be purged), and the next sign-in counts as the first and flags everything again. Call with [mutex] held.
     */
    private suspend fun accountChanged(uid: String?) {
        try {
            bookkeeping.update { values ->
                val kept = values.deletedUid?.takeIf { it == uid }
                if (uid == null) values.copy(deletedUid = kept, fullUploadUid = null) else values.copy(deletedUid = kept)
            }
            if (uid == null && activeUid() == null) repository.clearCloudPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not update the backup's bookkeeping", e)
        }
    }

    /**
     * Syncs [pushDelayMillis] after the last write that left rows pending, while an account is signed in. Every write
     * to a pending row counts, also one that leaves their number as it was (ending a sleep that is still waiting to go
     * up, or a row written again while its upload was on its way). It pulls first like every sync (one read when
     * nothing is new), so the upload never puts this phone's older version over a newer one in the backup.
     */
    @OptIn(FlowPreview::class)
    private suspend fun followPending() {
        combine(repository.observeCloudPending(), account) { pending, signedIn ->
            if (pending.count > 0 && signedIn?.consented == true) pending to signedIn.uid else null
        }
            .debounce(pushDelayMillis)
            .collect { due -> if (due != null) sync() }
    }

    private suspend fun lastSyncOf(uid: String): Long? = try {
        bookkeeping.get().lastSyncFor(uid)
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

    /** One sync; [backUpAgain] ("Back up now") also backs up an account whose backup was deleted. */
    private suspend fun sync(backUpAgain: Boolean = false) {
        try {
            mutex.withLock { syncLocked(backUpAgain) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backup sync failed", e)
        }
    }

    /** One sync for the current account. Call with [mutex] held. */
    private suspend fun syncLocked(backUpAgain: Boolean) {
        val signedIn = account.value
        val uid = activeUid()
        if (uid == null) {
            state.value = if (signedIn == null) CloudStatus.Off else CloudStatus.Paused
            return
        }
        val outcome = try {
            syncAccount(uid, backUpAgain)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudException) {
            Log.w(TAG, "Backup sync failed: ${e.error}", e)
            // A refused write may mean the consent record is gone (another phone deleted the backup): written again next time.
            if (e.error == CloudError.OTHER) forgetConsent(uid)
            e.error.toStatus()
        } catch (e: Exception) {
            Log.w(TAG, "Backup sync failed", e)
            CloudStatus.Error
        }
        // A sync that outlived its account (signed out meanwhile) says nothing about the new one.
        if (activeUid() == uid) state.value = outcome
    }

    /** The account the backup works for: signed in and agreed to the backup ([CloudAccount.consented]), or null. */
    private fun activeUid(): String? = account.value?.takeIf { it.consented }?.uid

    /** Pulls and pushes for [uid], unless its backup was deleted; returns the status to show. */
    private suspend fun syncAccount(uid: String, backUpAgain: Boolean): CloudStatus {
        if (bookkeeping.get().isDeleted(uid)) {
            // Its backup was deleted: nothing goes up for it again by itself, and the status says so.
            if (!backUpAgain) return CloudStatus.Deleted
            bookkeeping.update { it.copy(deletedUid = null) }
        }
        state.value = CloudStatus.Syncing
        val firstForAccount = bookkeeping.get().fullUploadUid != uid
        if (firstForAccount) {
            // Flagged before anything is sent, so an interrupted first sync leaves the rows pending.
            repository.markAllForCloud()
            // Pulled from the start: a cursor this device kept for the account from an earlier time would skip the
            // unchanged documents, so nothing would be cleared and the whole log would go up again.
            bookkeeping.update { it.copy(fullUploadUid = uid, cursorUid = uid, cursor = null) }
        }
        pull(uid)
        recordConsent(uid)
        push(uid)
        val now = clock.now()
        bookkeeping.update { it.copy(lastSyncUid = uid, lastSyncAtMillis = now) }
        return CloudStatus.Synced(now)
    }

    private suspend fun forgetConsent(uid: String) = try {
        bookkeeping.update { if (it.consentUid == uid) it.copy(consentUid = null, consentVersion = null) else it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not update the backup's bookkeeping", e)
    }

    /** Records the account's consent in the backend once per version, before anything is sent under it. */
    private suspend fun recordConsent(uid: String) {
        val version = account.value?.takeIf { it.uid == uid }?.consentVersion ?: throw CloudException(CloudError.SIGNED_OUT)
        if (bookkeeping.get().consentFor(uid) == version) return
        checkAccount(uid)
        store.recordConsent(uid, version)
        bookkeeping.update { it.copy(consentUid = uid, consentVersion = version) }
    }

    /** Merges every change after the stored cursor, a page at a time. */
    private suspend fun pull(uid: String) {
        synchronized(pullLock) { lastPullAt = monotonicMillis() }
        var cursor = bookkeeping.get().cursorFor(uid)
        repeat(MAX_PAGES) {
            checkAccount(uid)
            val page = store.changesAfter(uid, cursor, CloudStore.PAGE_SIZE)
            if (page.entries.isNotEmpty()) repository.applyCloudEntries(page.entries)
            val last = page.last
            if (last == null || last == cursor) return
            cursor = last
            bookkeeping.update { it.copy(cursorUid = uid, cursor = last) }
            if (page.documents < CloudStore.PAGE_SIZE) return
        }
    }

    /** Uploads the pending rows in batches until none are left. */
    private suspend fun push(uid: String) {
        repeat(MAX_PUSH_BATCHES) {
            checkAccount(uid)
            val batch = repository.cloudPending(CloudStore.UPLOAD_BATCH)
            if (batch.isEmpty()) return
            store.upload(uid, batch.map { it.forCloud() })
            // Nothing cleared: every row was written again meanwhile. Stop rather than chase them: the next sync goes on.
            if (repository.markCloudSynced(batch) == 0) return
        }
    }

    /** Stops a sync whose account signed out, changed or lost its consent meanwhile: nothing more is read or sent for it. */
    private fun checkAccount(uid: String) {
        if (activeUid() != uid) throw CloudException(CloudError.SIGNED_OUT)
    }

    companion object {
        private const val TAG = "CloudBackup"

        /** How long after the last write that left rows pending the push runs. */
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
