package com.ffmathy.pooparazzi.core.data

import android.util.Log
import androidx.room.withTransaction
import com.ffmathy.pooparazzi.core.model.AlreadyRunningException
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * @param syncScope where pushes run. Null pushes inline before the call returns (deterministic for tests).
 */
class RoomEntryRepository(
    private val database: PooparazziDatabase,
    private val clock: Clock = SystemClock,
    private val sync: EntrySync = NoopEntrySync,
    private val syncScope: CoroutineScope? = null,
) : EntryRepository {

    private val dao = database.entryDao()

    /** One push at a time, in the order the writes asked for them (see [pushCurrent]). */
    private val pushMutex = Mutex()

    override fun observeEntries(): Flow<List<Entry>> =
        dao.observeActive().map { rows -> rows.map(EntryEntity::toEntry) }.distinctUntilChanged()

    override suspend fun get(id: String): Entry? = dao.get(id)?.toEntry()

    // ---- Feeds --------------------------------------------------------------------------------

    override suspend fun logFeedChecked(type: FeedType?, at: Long, side: BreastSide?): LogResult {
        checkNotInFuture(at) { Entry.feed(type, at, side, updatedAtMillis = 0) }
        val result = database.withTransaction {
            val window = EntryRules.DEDUPE_WINDOW_MILLIS - 1
            val duplicate = feedsBetween(type, at - window, at + window).firstOrNull()
            if (duplicate != null) {
                LogResult.Duplicate(duplicate.toEntry())
            } else {
                val entry = Entry.feed(type, at, side, updatedAtMillis = clock.now()).requireValid()
                writeLocal(entry, stored = null)
                LogResult.Logged(entry)
            }
        }
        if (result is LogResult.Logged) push(result.entry.id)
        return result
    }

    // ---- Sleep --------------------------------------------------------------------------------

    override suspend fun startSleep(at: Long): Result<Entry> =
        startInterval(at, running = { dao.runningSleeps() }) { stamp -> Entry.sleep(at, updatedAtMillis = stamp) }

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
        val candidate = Entry.diaper(at, pee, poop, color, texture, updatedAtMillis = 0)
        candidate.requireValid()
        checkNotInFuture(at) { candidate }
        val result = database.withTransaction {
            val window = EntryRules.DEDUPE_WINDOW_MILLIS - 1
            val duplicate = dao.diapersBetween(pee, poop, at - window, at + window).firstOrNull()
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
        val written = database.withTransaction {
            require(dao.get(entry.id) == null) { "Entry ${entry.id} already exists: use update()" }
            if (entry.isRunning) runningConflict(entry)?.let { throw AlreadyRunningException(it) }
            val stamped = entry.copy(updatedAtMillis = clock.now())
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
            if (entry.isRunning) runningConflict(entry)?.let { throw AlreadyRunningException(it) }
            val stamped = entry.copy(updatedAtMillis = nextStamp(stored.updatedAtMillis))
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

    override suspend fun restore(previous: Entry): Entry {
        previous.requireValid()
        val written = database.withTransaction {
            val stored = dao.get(previous.id)
            if (previous.isRunning) runningConflict(previous)?.let { throw AlreadyRunningException(it) }
            val stamped = previous.copy(updatedAtMillis = nextStamp(stored?.updatedAtMillis))
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
     * 2. Cross-device conflicts are resolved once, after the whole batch, by a rule both devices share:
     *    - two running sleeps: the earliest start wins, then the smaller id;
     *    - two instants of the same kind, type and flags less than [EntryRules.DEDUPE_WINDOW_MILLIS] apart
     *      (DESIGN_SPEC §4.4: one entry "from any surface"): the earliest wins, then the smaller id.
     * 3. **Only the device that created a losing entry soft-deletes it** (a new local write, pushed), and the
     *    other device takes that deletion through sync. This device never deletes the other device's entry:
     *    a newer running sleep from the watch that "loses" against our stale running copy usually means the
     *    watch has already ended ours, and that end is still on its way. Both rows are kept until it arrives.
     */
    override suspend fun applyRemoteAll(entries: List<Entry>): Int {
        var applied = 0
        val inserted = mutableListOf<Entry>()
        // A timed breastfeed from an older app version is merged as an instant (see Entry.normalized).
        val ordered = entries.map(Entry::normalized).sortedWith(compareBy<Entry> { it.updatedAtMillis }.thenBy { it.id })
        for (entry in ordered) {
            val violation = validate(entry)
            if (violation != null) {
                Log.w(TAG, "Ignoring invalid remote entry ${entry.id}: $violation")
                continue
            }
            val outcome = try {
                database.withTransaction {
                    if (dao.isPurged(entry.id)) return@withTransaction Merge.SKIPPED
                    val existed = dao.get(entry.id) != null
                    when {
                        !dao.upsert(EntryEntity.from(entry, pendingSync = false, createdLocally = false)) -> Merge.SKIPPED
                        existed -> Merge.UPDATED
                        else -> Merge.INSERTED
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not merge remote entry ${entry.id}", e)
                Merge.SKIPPED
            }
            if (outcome != Merge.SKIPPED) applied++
            if (outcome == Merge.INSERTED) inserted += entry
        }
        if (applied == 0) return 0
        val losers = database.withTransaction { resolveConflicts(inserted) }
        push(losers)
        return applied
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

    // ---- Internals ----------------------------------------------------------------------------

    private enum class Merge { SKIPPED, INSERTED, UPDATED }

    /** Writes a local version of an entry, pending sync. A new row is created here; an existing row keeps its origin. */
    private suspend fun writeLocal(entry: Entry, stored: EntryEntity?) {
        dao.write(EntryEntity.from(entry, pendingSync = true, createdLocally = stored?.createdLocally ?: true))
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

    /** Another running entry of the same type as [entry], if any. Call inside a transaction. */
    private suspend fun runningConflict(entry: Entry): Entry? {
        val running = if (entry.isSleep) dao.runningSleeps() else emptyList()
        return running.firstOrNull { it.id != entry.id }?.toEntry()
    }

    /**
     * The shared conflict rule of [applyRemoteAll]: soft-deletes this device's own losers and returns their ids.
     * Call inside a transaction.
     */
    private suspend fun resolveConflicts(inserted: List<Entry>): List<String> {
        val losers = linkedSetOf<String>()
        fun dropOwnLosers(rows: List<EntryEntity>) {
            if (rows.size < 2) return
            val winner = rows.minWith(EARLIEST_FIRST)
            rows.filter { it.id != winner.id && it.createdLocally && it.id !in losers }.forEach { losers += it.id }
        }
        dropOwnLosers(dao.runningSleeps())
        for (entry in inserted) {
            if (entry.deleted || entry.isInterval) continue
            dropOwnLosers(duplicatesAround(entry))
        }
        for (id in losers) {
            val row = dao.get(id) ?: continue
            dao.write(row.copy(deleted = true, updatedAtMillis = nextStamp(row.updatedAtMillis), pendingSync = true))
        }
        return losers.toList()
    }

    /** Non-deleted instants that count as the same log as [entry] (same kind, type and flags, within the dedupe window). */
    private suspend fun duplicatesAround(entry: Entry): List<EntryEntity> {
        val window = EntryRules.DEDUPE_WINDOW_MILLIS - 1
        val from = entry.startMillis - window
        val to = entry.startMillis + window
        return when (entry.kind) {
            EntryKind.FEED -> feedsBetween(entry.feedType, from, to)
            EntryKind.DIAPER -> dao.diapersBetween(entry.pee, entry.poop, from, to)
            EntryKind.SLEEP -> emptyList()
        }
    }

    /** Non-deleted instant feeds of [type] (null: untyped feeds) between the two times. */
    private suspend fun feedsBetween(type: FeedType?, from: Long, to: Long): List<EntryEntity> =
        if (type == null) dao.untypedFeedsBetween(from, to) else dao.feedsBetween(type, from, to)

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
