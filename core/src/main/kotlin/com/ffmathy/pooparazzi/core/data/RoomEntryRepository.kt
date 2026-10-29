package com.ffmathy.pooparazzi.core.data

import android.util.Log
import androidx.room.withTransaction
import com.ffmathy.pooparazzi.core.model.AlreadyRunningException
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.EntryViolation
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.InvalidEntryException
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.model.isInterval
import com.ffmathy.pooparazzi.core.model.normalized
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.model.requireValid
import com.ffmathy.pooparazzi.core.model.validate
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * [EntryRepository] on Room.
 *
 * Every check-then-write runs in one database transaction, so two quick taps (or a tap racing a
 * remote merge) cannot create two running sleeps. Local writes are flagged `pendingSync` and handed to
 * [sync] after the transaction; the flag clears once the Data Layer has accepted the item.
 *
 * Pushes are serialized and always send the row's **current** version, then clear `pendingSync` only for
 * that version. A slow push of an older version can therefore never land after a newer one and leave the
 * Data Layer holding stale data with nothing left to retry.
 *
 * While [cloudBackup] says so (an account is signed in), rows also carry `cloudPending` for the backend
 * (BABIES_AND_BACKUP §4.2):
 *
 * | Write | `pendingSync` (other device) | `cloudPending` (backend) |
 * |---|---|---|
 * | Local write on this device (conflict losers included) | true | [cloudBackup] |
 * | Merged from the other device ([applyRemoteAll]) | false | [cloudBackup] |
 * | Merged from the backend ([applyCloudEntries]) | true | false |
 * | A row identical to the backend's version | unchanged | cleared |
 * | A row that beats the backend's version | unchanged | [cloudBackup] |
 *
 * @param syncScope where pushes run. Null pushes inline before the call returns (deterministic for tests).
 * @param selectedBaby the id of the device's current baby (`Settings.selectedBaby`). Writes read it when they run;
 *   [observeEntries] follows it.
 * @param cloudBackup true while an account is signed in (a phone build with a backend): local writes and merges from
 *   the other device are then flagged for upload. Read on every write, so it must be cheap. Signed out, nothing is
 *   flagged, so soft deletes are purged as without a backend; the first sync for an account flags everything.
 */
class RoomEntryRepository(
    private val database: PooparazziDatabase,
    private val clock: Clock = SystemClock,
    private val sync: EntrySync = NoopEntrySync,
    private val syncScope: CoroutineScope? = null,
    private val selectedBaby: Flow<String> = flowOf(Baby.FIRST_ID),
    private val cloudBackup: () -> Boolean = { false },
) : EntryRepository {

    private val dao = database.entryDao()

    /** One push at a time, in the order the writes asked for them (see [pushCurrent]). */
    private val pushMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeEntries(): Flow<List<Entry>> =
        selectedBaby.distinctUntilChanged()
            .flatMapLatest { babyId -> dao.observeActive(babyId).map { rows -> rows.map(EntryEntity::toEntry) } }
            .distinctUntilChanged()

    override fun observeAllEntries(): Flow<List<Entry>> =
        dao.observeActive().map { rows -> rows.map(EntryEntity::toEntry) }.distinctUntilChanged()

    override suspend fun get(id: String): Entry? = dao.get(id)?.toEntry()

    // ---- Feeds --------------------------------------------------------------------------------

    override suspend fun logFeedChecked(type: FeedType?, at: Long, side: BreastSide?, amountMl: Int?): LogResult {
        val babyId = currentBaby()
        checkNotInFuture(at) { Entry.feed(type, at, side, updatedAtMillis = 0, babyId = babyId, amountMl = amountMl) }
        val result = database.withTransaction {
            val window = EntryRules.DEDUPE_WINDOW_MILLIS - 1
            val duplicate = feedsBetween(babyId, type, at - window, at + window).firstOrNull()
            if (duplicate != null) {
                LogResult.Duplicate(duplicate.toEntry())
            } else {
                val entry = Entry.feed(type, at, side, updatedAtMillis = clock.now(), babyId = babyId, amountMl = amountMl).requireValid()
                writeLocal(entry, stored = null)
                LogResult.Logged(entry)
            }
        }
        if (result is LogResult.Logged) push(result.entry.id)
        return result
    }

    // ---- Sleep --------------------------------------------------------------------------------

    override suspend fun startSleep(at: Long): Result<Entry> {
        val babyId = currentBaby()
        return startInterval(at, running = { dao.runningSleeps(babyId) }) { stamp ->
            Entry.sleep(at, updatedAtMillis = stamp, babyId = babyId)
        }
    }

    override suspend fun endSleep(id: String, at: Long): EndResult =
        endInterval(id, at, matches = { it.isSleep })

    // ---- Diapers ------------------------------------------------------------------------------

    override suspend fun logDiaperChecked(
        at: Long,
        pee: Boolean,
        poop: Boolean,
        color: PoopColor?,
        texture: PoopTexture?,
    ): LogResult {
        val babyId = currentBaby()
        val candidate = Entry.diaper(at, pee, poop, color, texture, updatedAtMillis = 0, babyId = babyId)
        candidate.requireValid()
        checkNotInFuture(at) { candidate }
        val result = database.withTransaction {
            val window = EntryRules.DEDUPE_WINDOW_MILLIS - 1
            val duplicate = dao.diapersBetween(babyId, pee, poop, at - window, at + window).firstOrNull()
            if (duplicate != null) {
                LogResult.Duplicate(duplicate.toEntry())
            } else {
                val entry = candidate.copy(updatedAtMillis = clock.now())
                writeLocal(entry, stored = null)
                LogResult.Logged(entry)
            }
        }
        if (result is LogResult.Logged) push(result.entry.id)
        return result
    }

    // ---- Generic edits ------------------------------------------------------------------------

    override suspend fun add(entry: Entry): Entry {
        entry.requireValid()
        checkNotInFuture(entry)
        val forCurrent = entry.copy(babyId = currentBaby())
        val written = database.withTransaction {
            require(dao.get(entry.id) == null) { "Entry ${entry.id} already exists: use update()" }
            if (forCurrent.isRunning) runningConflict(forCurrent)?.let { throw AlreadyRunningException(it) }
            val stamped = forCurrent.copy(updatedAtMillis = clock.now())
            writeLocal(stamped, stored = null)
            stamped
        }
        push(written.id)
        return written
    }

    override suspend fun update(entry: Entry): Entry {
        entry.requireValid()
        checkNotInFuture(entry)
        val written = database.withTransaction {
            val stored = dao.get(entry.id) ?: throw NoSuchElementException("No entry ${entry.id}")
            // An edit never moves an entry to another baby.
            val kept = entry.copy(babyId = stored.babyId)
            if (kept.isRunning) runningConflict(kept)?.let { throw AlreadyRunningException(it) }
            val stamped = kept.copy(updatedAtMillis = nextStamp(stored.updatedAtMillis))
            writeLocal(stamped, stored)
            stamped
        }
        push(written.id)
        return written
    }

    override suspend fun delete(id: String): Entry? {
        var deleted = false
        val before = database.withTransaction {
            val row = dao.get(id) ?: return@withTransaction null
            val stored = row.toEntry()
            if (!stored.deleted) {
                writeLocal(stored.copy(deleted = true, updatedAtMillis = nextStamp(stored.updatedAtMillis)), row)
                deleted = true
            }
            stored
        }
        if (deleted) push(id)
        return before
    }

    override suspend fun deleteBabyEntries(babyId: String): Int {
        val ids = database.withTransaction {
            dao.activeOf(babyId).map { row ->
                val stored = row.toEntry()
                writeLocal(stored.copy(deleted = true, updatedAtMillis = nextStamp(stored.updatedAtMillis)), row)
                row.id
            }
        }
        push(ids)
        return ids.size
    }

    override suspend fun restore(previous: Entry): Entry {
        val restoring = previous.normalized().requireValid()
        val written = database.withTransaction {
            val stored = dao.get(restoring.id)
            if (restoring.isRunning) runningConflict(restoring)?.let { throw AlreadyRunningException(it) }
            val stamped = restoring.copy(updatedAtMillis = nextStamp(stored?.updatedAtMillis))
            writeLocal(stamped, stored)
            stamped
        }
        push(written.id)
        return written
    }

    // ---- Sync ---------------------------------------------------------------------------------

    override suspend fun applyRemote(entry: Entry): Boolean = applyRemoteAll(listOf(entry)) > 0

    /**
     * Merges a batch from the other device (last-writer-wins, never echoed back). The result does not depend
     * on the order in which the Data Layer delivers items:
     *
     * 1. The batch is applied oldest write first.
     * 2. Cross-device conflicts are resolved once, after the whole batch, by a rule both devices share, within
     *    each baby:
     *    - two running sleeps: the earliest start wins, then the smaller id;
     *    - two instants of the same kind, type and flags less than [EntryRules.DEDUPE_WINDOW_MILLIS] apart
     *      (DESIGN_SPEC §4.4: one entry "from any surface"): the earliest wins, then the smaller id.
     * 3. **Only the device that created a losing entry soft-deletes it** (a new local write, pushed), and the
     *    other device takes that deletion through sync. This device never deletes the other device's entry:
     *    a newer running sleep from the watch that "loses" against our stale running copy usually means the
     *    watch has already ended ours, and that end is still on its way. Both rows are kept until it arrives.
     *
     * Stored rows are not pending for the other device (they came from it), but are for the backend with
     * [cloudBackup]: the phone uploads what the watch logged.
     */
    override suspend fun applyRemoteAll(entries: List<Entry>): Int {
        val merged = merge(entries, MergeSource.PEER)
        push(merged.losers)
        return merged.written.size
    }

    /**
     * Merges a page from the backend with the same rules as [applyRemoteAll]. Stored rows are not pending for the
     * backend (it has exactly these versions) and are pushed to the other device, so the watch gets what was restored;
     * a stored row identical to the backend's version has its `cloudPending` cleared, and a stored row that beats the
     * backend's version is flagged so the backend gets it again. Returns how many were stored.
     *
     * @throws IOException when some entries could not be merged (a database failure), after the others were: the
     *   backup then keeps its cursor, so the page is pulled again.
     */
    suspend fun applyCloudEntries(entries: List<Entry>): Int {
        val merged = merge(entries, MergeSource.CLOUD)
        push(merged.written + merged.losers)
        if (merged.failed > 0) throw IOException("Could not merge ${merged.failed} of ${entries.size} entries from the backend")
        return merged.written.size
    }

    override suspend fun purgeDeleted(): Int {
        val now = clock.now()
        return dao.purgeDeletedBefore(now - EntryRules.SOFT_DELETE_RETENTION_MILLIS, now).size
    }

    /** Entries with local writes that have not reached sync yet. */
    suspend fun pendingSync(): List<Entry> = dao.pendingSync().map(EntryEntity::toEntry)

    /**
     * Re-pushes every local write that sync has not accepted yet (the current version of each row, one at a
     * time). Returns true when all were accepted. Used by [EntrySync.retry].
     */
    suspend fun pushPending(): Boolean = pushMutex.withLock {
        var all = true
        for (row in dao.pendingSync()) if (!pushCurrent(row.id)) all = false
        all
    }

    /** Marks [entry] as handed to sync, unless it was written again meanwhile. */
    internal suspend fun markSynced(entry: Entry) {
        dao.markSynced(entry.id, entry.updatedAtMillis)
    }

    // ---- Backend ------------------------------------------------------------------------------

    /** Up to [limit] entries (deleted ones included) whose current version the backend lacks, oldest write first. */
    suspend fun cloudPending(limit: Int): List<Entry> = dao.cloudPendingRows(limit).map(EntryEntity::toEntry)

    /**
     * Marks [entries] as held by the backend, each unless it was written again since that version was read.
     * Returns how many rows were cleared.
     */
    suspend fun markCloudSynced(entries: List<Entry>): Int = database.withTransaction {
        entries.sumOf { dao.markCloudSynced(it.id, it.updatedAtMillis) }
    }

    /** Flags every row for the backend: the first sync for an account backs up everything logged so far. */
    suspend fun markAllForCloud() {
        dao.markAllCloudPending()
    }

    /** Clears every row's backend flag: signed out, nothing is pending for any backend (and soft deletes can go). */
    suspend fun clearCloudPending() {
        dao.clearAllCloudPending()
    }

    /** How many rows the backend lacks. */
    fun observeCloudPendingCount(): Flow<Int> = dao.observeCloudPending().map { it.count }.distinctUntilChanged()

    /**
     * The rows the backend lacks, as a value that changes with every write to them (not only when their number
     * changes): the backup pushes a little after each change.
     */
    fun observeCloudPending(): Flow<CloudPendingState> = dao.observeCloudPending().distinctUntilChanged()

    /**
     * The baby ids of all stored entries, in the order of each baby's earliest entry (the phone adopts the ones its
     * settings do not know, BABIES_AND_BACKUP §2.1).
     */
    fun observeBabyIds(): Flow<List<String>> = dao.observeBabyIds().distinctUntilChanged()

    // ---- Internals ----------------------------------------------------------------------------

    /** Where a merged batch comes from; decides the pending flags of the rows it writes (see the class table). */
    private enum class MergeSource { PEER, CLOUD }

    /**
     * What a merge did: the ids it wrote, this device's conflict losers it soft-deleted, and how many entries could
     * not be merged (a database failure, as opposed to an invalid entry or a losing version).
     */
    private class Merged(val written: List<String>, val losers: List<String>, val failed: Int)

    private enum class Merge { SKIPPED, INSERTED, UPDATED }

    /**
     * The merge shared by [applyRemoteAll] and [applyCloudEntries]: entries are applied oldest write first
     * (last-writer-wins, invalid and purged ones skipped), then conflicts are resolved once ([resolveConflicts]).
     */
    private suspend fun merge(entries: List<Entry>, source: MergeSource): Merged {
        val written = mutableListOf<String>()
        val inserted = mutableListOf<Entry>()
        var failed = 0
        // A timed breastfeed from an older app version is merged as an instant (see Entry.normalized).
        val ordered = entries.map(Entry::normalized).sortedWith(compareBy<Entry> { it.updatedAtMillis }.thenBy { it.id })
        for (entry in ordered) {
            val violation = validate(entry)
            if (violation != null) {
                Log.w(TAG, "Ignoring invalid remote entry ${entry.id}: $violation")
                continue
            }
            val outcome = try {
                database.withTransaction { mergeOne(entry, source) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not merge remote entry ${entry.id}", e)
                failed++
                Merge.SKIPPED
            }
            if (outcome != Merge.SKIPPED) written += entry.id
            if (outcome == Merge.INSERTED) inserted += entry
        }
        val losers = if (written.isEmpty()) emptyList() else database.withTransaction { resolveConflicts(inserted) }
        return Merged(written, losers, failed)
    }

    /** Merges one entry with the flags of [source]. Call inside a transaction. */
    private suspend fun mergeOne(entry: Entry, source: MergeSource): Merge {
        if (dao.isPurged(entry.id)) return Merge.SKIPPED
        val stored = dao.get(entry.id)
        // A merge never moves an entry to another baby (as update()): a different baby id on a stored entry can only
        // come from a device whose version dropped the field (an older app re-encoding the entry as the first baby's).
        val kept = if (stored != null) entry.copy(babyId = stored.babyId) else entry
        val fromCloud = source == MergeSource.CLOUD
        val incoming = EntryEntity.from(
            kept,
            pendingSync = fromCloud,
            createdLocally = false,
            cloudPending = !fromCloud && cloudBackup(),
        )
        return when {
            dao.upsert(incoming) -> if (stored == null) Merge.INSERTED else Merge.UPDATED
            else -> {
                if (fromCloud && stored != null) {
                    // The backend holds exactly this row's version: nothing is left to upload. Otherwise the stored row
                    // beats the backend's version (an older one, or a tie it loses), so the backend must get it again,
                    // or the backup and the other phones keep the losing version for good.
                    val cloudPending = stored.toEntry() != kept && cloudBackup()
                    if (stored.cloudPending != cloudPending) dao.write(stored.copy(cloudPending = cloudPending))
                }
                Merge.SKIPPED
            }
        }
    }

    /** The current baby's id, read when a write runs. */
    private suspend fun currentBaby(): String = selectedBaby.first().ifBlank { Baby.FIRST_ID }

    /**
     * Writes a local version of an entry, pending sync (and the backend, with [cloudBackup]). A new row is created
     * here; an existing row keeps its origin.
     */
    private suspend fun writeLocal(entry: Entry, stored: EntryEntity?) {
        dao.write(
            EntryEntity.from(
                entry,
                pendingSync = true,
                createdLocally = stored?.createdLocally ?: true,
                cloudPending = cloudBackup(),
            ),
        )
    }

    private suspend fun startInterval(
        at: Long,
        running: suspend () -> List<EntryEntity>,
        build: (stamp: Long) -> Entry,
    ): Result<Entry> {
        try {
            checkNotInFuture(at) { build(0) }
        } catch (e: InvalidEntryException) {
            return Result.failure(e)
        }
        val result = database.withTransaction {
            val current = running().firstOrNull()
            if (current != null) {
                Result.failure(AlreadyRunningException(current.toEntry()))
            } else {
                val entry = build(clock.now()).requireValid()
                writeLocal(entry, stored = null)
                Result.success(entry)
            }
        }
        result.getOrNull()?.let { push(it.id) }
        return result
    }

    private suspend fun endInterval(
        id: String,
        at: Long,
        matches: (Entry) -> Boolean,
    ): EndResult {
        val result = database.withTransaction {
            val row = dao.get(id)
            val stored = row?.toEntry()
            if (row == null || stored == null || !matches(stored) || !stored.isRunning) {
                return@withTransaction EndResult.NotRunning(stored?.takeUnless { it.deleted })
            }
            val ended = stored.copy(endMillis = at, updatedAtMillis = nextStamp(stored.updatedAtMillis))
            if (at < stored.startMillis) throw InvalidEntryException(EntryViolation.END_BEFORE_START, ended)
            checkNotInFuture(at) { ended }
            if (at - stored.startMillis < EntryRules.MIS_TAP_MILLIS) {
                val discarded = ended.copy(deleted = true).requireValid()
                writeLocal(discarded, row)
                EndResult.Discarded(discarded, stored)
            } else {
                ended.requireValid()
                writeLocal(ended, row)
                EndResult.Ended(ended, stored)
            }
        }
        when (result) {
            is EndResult.Ended -> push(result.entry.id)
            is EndResult.Discarded -> push(result.entry.id)
            is EndResult.NotRunning -> Unit
        }
        return result
    }

    /** Another running entry of the same type and baby as [entry], if any. Call inside a transaction. */
    private suspend fun runningConflict(entry: Entry): Entry? {
        val running = if (entry.isSleep) dao.runningSleeps(entry.babyId) else emptyList()
        return running.firstOrNull { it.id != entry.id }?.toEntry()
    }

    /**
     * The shared conflict rule of [applyRemoteAll] and [applyCloudEntries], within each baby: soft-deletes this
     * device's own losers and returns their ids. Call inside a transaction.
     */
    private suspend fun resolveConflicts(inserted: List<Entry>): List<String> {
        val losers = linkedSetOf<String>()
        fun dropOwnLosers(rows: List<EntryEntity>) {
            if (rows.size < 2) return
            val winner = rows.minWith(EARLIEST_FIRST)
            rows.filter { it.id != winner.id && it.createdLocally && it.id !in losers }.forEach { losers += it.id }
        }
        dao.runningSleeps().groupBy { it.babyId }.values.forEach(::dropOwnLosers)
        for (entry in inserted) {
            if (entry.deleted || entry.isInterval) continue
            dropOwnLosers(duplicatesAround(entry))
        }
        for (id in losers) {
            val row = dao.get(id) ?: continue
            dao.write(
                row.copy(
                    deleted = true,
                    updatedAtMillis = nextStamp(row.updatedAtMillis),
                    pendingSync = true,
                    cloudPending = cloudBackup(),
                ),
            )
        }
        return losers.toList()
    }

    /**
     * Non-deleted instants that count as the same log as [entry] (same baby, kind, type and flags, within the dedupe
     * window).
     */
    private suspend fun duplicatesAround(entry: Entry): List<EntryEntity> {
        val window = EntryRules.DEDUPE_WINDOW_MILLIS - 1
        val from = entry.startMillis - window
        val to = entry.startMillis + window
        return when (entry.kind) {
            EntryKind.FEED -> feedsBetween(entry.babyId, entry.feedType, from, to)
            EntryKind.DIAPER -> dao.diapersBetween(entry.babyId, entry.pee, entry.poop, from, to)
            EntryKind.SLEEP -> emptyList()
        }
    }

    /** Non-deleted instant feeds of [babyId] and [type] (null: untyped feeds) between the two times. */
    private suspend fun feedsBetween(babyId: String, type: FeedType?, from: Long, to: Long): List<EntryEntity> =
        if (type == null) dao.untypedFeedsBetween(babyId, from, to) else dao.feedsBetween(babyId, type, from, to)

    /** A strictly increasing write clock per row, even when the wall clock stands still or goes back. */
    private fun nextStamp(previous: Long?): Long = maxOf(clock.now(), (previous ?: Long.MIN_VALUE / 2) + 1)

    private fun checkNotInFuture(entry: Entry) {
        checkNotInFuture(entry.startMillis) { entry }
        entry.endMillis?.let { end -> checkNotInFuture(end) { entry } }
    }

    private inline fun checkNotInFuture(at: Long, entry: () -> Entry) {
        if (at > clock.now() + EntryRules.FUTURE_TOLERANCE_MILLIS) {
            throw InvalidEntryException(EntryViolation.IN_FUTURE, entry())
        }
    }

    private suspend fun push(vararg ids: String) = push(ids.asList())

    /** Schedules pushes of [ids] (their current versions), after every push requested before them. */
    private suspend fun push(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val unique = ids.distinct()
        launchOrRun { pushMutex.withLock { unique.forEach { pushCurrent(it) } } }
    }

    /**
     * Pushes the row's current version if it still holds an unsynced local write, then clears the flag for
     * exactly that version. Call with [pushMutex] held. Returns false when sync did not accept it.
     */
    private suspend fun pushCurrent(id: String): Boolean {
        val row = dao.get(id) ?: return true
        if (!row.pendingSync) return true
        val pushed = try {
            sync.push(row.toEntry())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Push failed for $id", e)
            false
        }
        if (pushed) dao.markSynced(id, row.updatedAtMillis)
        return pushed
    }

    private suspend fun launchOrRun(block: suspend () -> Unit) {
        val scope = syncScope
        if (scope == null) block() else scope.launch { block() }
    }

    private companion object {
        const val TAG = "EntryRepository"

        /** The conflict winner comes first: earliest start, then the smallest id. */
        val EARLIEST_FIRST: Comparator<EntryEntity> = compareBy<EntryEntity> { it.startMillis }.thenBy { it.id }
    }
}
