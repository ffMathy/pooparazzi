package com.ffmathy.pooparazzi.core.data

import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.InvalidEntryException
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import kotlinx.coroutines.flow.Flow

/** Timing rules shared by the repository and the apps (DESIGN_SPEC §5.12, V1_ADDITIONS §1). */
object EntryRules {
    /** A sleep that ends less than this after its start is a mis-tap and is discarded. */
    const val MIS_TAP_MILLIS: Long = 60_000L

    /** Two logs of the same kind (and type / flags) whose times are closer than this are one entry. */
    const val DEDUPE_WINDOW_MILLIS: Long = 5_000L

    /** Soft-deleted entries are purged this long after their deletion. */
    const val SOFT_DELETE_RETENTION_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

    /** Local writes may lie at most this far in the future (clock jitter between tap and write). */
    const val FUTURE_TOLERANCE_MILLIS: Long = 60_000L
}

/** Outcome of ending a running sleep. */
sealed interface EndResult {
    /** The interval ended normally. [previous] is the running snapshot, for Undo via [EntryRepository.restore]. */
    data class Ended(val entry: Entry, val previous: Entry) : EndResult

    /**
     * The sleep ended less than [EntryRules.MIS_TAP_MILLIS] after its start, so it was soft-deleted
     * ("Sleep cancelled"). [entry] is the discarded entry (deleted, with its end);
     * [previous] is the running snapshot to pass to [EntryRepository.restore] for Undo.
     */
    data class Discarded(val entry: Entry, val previous: Entry) : EndResult

    /**
     * Nothing was running under that id (already ended, e.g. on the other device, deleted, unknown or of
     * another kind). [current] is the stored entry when it exists and is not deleted, for "Already awake since …".
     */
    data class NotRunning(val current: Entry?) : EndResult
}

/** Outcome of a one-tap log that is deduplicated (DESIGN_SPEC §4.4). */
sealed interface LogResult {
    val entry: Entry

    /** A new entry was written. */
    data class Logged(override val entry: Entry) : LogResult

    /** The same log already exists within [EntryRules.DEDUPE_WINDOW_MILLIS]; nothing was written. Show "Already logged …". */
    data class Duplicate(override val entry: Entry) : LogResult
}

/**
 * The single source of truth for entries on this device. Every local write bumps [Entry.updatedAtMillis],
 * is validated (V1_ADDITIONS §1) and is pushed to the other device through sync.
 *
 * The log is kept per baby (BABIES_AND_BACKUP §2.3). The device's *current* baby is the one [observeEntries] shows
 * and the one-tap logs, [startSleep] and [add] write for; the rules (one running sleep, the dedupe window) apply
 * within one baby. Functions that take an id work on the entry of any baby, and an edit never moves an entry to
 * another baby.
 *
 * Invalid writes throw [InvalidEntryException] (or return it as a failure for the `Result` functions);
 * a second running sleep is rejected with [AlreadyRunningException].
 * Local times may not lie in the future (beyond [EntryRules.FUTURE_TOLERANCE_MILLIS]).
 */
interface EntryRepository {
    /** The current baby's non-deleted entries, oldest first. Follows a change of the current baby. */
    fun observeEntries(): Flow<List<Entry>>

    /**
     * Non-deleted entries of every baby, oldest first: for what must not lose sight of a baby that is not the current
     * one (the running-sleep notification, Auto appearance).
     */
    fun observeAllEntries(): Flow<List<Entry>>

    /** The stored entry with [id], including deleted ones, or null. */
    suspend fun get(id: String): Entry?

    /**
     * Logs a feed instant of [type] (null: just the time) at [at]; returns the existing entry for a duplicate
     * (the same type within [EntryRules.DEDUPE_WINDOW_MILLIS]). [side] is the optional side order of a
     * [FeedType.BREAST] feed and is ignored for other types; [amountMl] the optional amount of a [FeedType.FORMULA]
     * feed, ignored for other types.
     */
    suspend fun logFeed(type: FeedType?, at: Long, side: BreastSide? = null, amountMl: Int? = null): Entry =
        logFeedChecked(type, at, side, amountMl).entry

    /** As [logFeed], but tells a new entry apart from a duplicate. */
    suspend fun logFeedChecked(type: FeedType?, at: Long, side: BreastSide? = null, amountMl: Int? = null): LogResult

    /** Starts a sleep at [at]. Fails with [AlreadyRunningException] while one of the current baby's runs. */
    suspend fun startSleep(at: Long): Result<Entry>

    /** Ends the running sleep [id] at [at]: [EndResult.Ended], or [EndResult.Discarded] under 60 s. */
    suspend fun endSleep(id: String, at: Long): EndResult

    /**
     * Logs a diaper at [at]. [pee] and [poop] are optional (neither logs a plain change); [color] and [texture]
     * are dropped unless [poop]. Returns the existing entry for a duplicate.
     */
    suspend fun logDiaper(
        at: Long,
        pee: Boolean,
        poop: Boolean,
        color: PoopColor? = null,
        texture: PoopTexture? = null,
    ): Entry = logDiaperChecked(at, pee, poop, color, texture).entry

    /** As [logDiaper], but tells a new entry apart from a duplicate. */
    suspend fun logDiaperChecked(
        at: Long,
        pee: Boolean,
        poop: Boolean,
        color: PoopColor? = null,
        texture: PoopTexture? = null,
    ): LogResult

    /**
     * Adds a complete entry (History "Add past entry") for the current baby, whatever its [Entry.babyId]. Its id must
     * be new. Returns it with a fresh [Entry.updatedAtMillis].
     */
    suspend fun add(entry: Entry): Entry

    /**
     * Replaces an existing entry after validation; bumps [Entry.updatedAtMillis]. The entry stays with its baby
     * (the stored [Entry.babyId] is kept). Throws [NoSuchElementException] for an unknown id.
     */
    suspend fun update(entry: Entry): Entry

    /** Soft-deletes [id]. Returns the snapshot before deletion (for Undo via [restore]), or null for an unknown id. */
    suspend fun delete(id: String): Entry?

    /**
     * Soft-deletes every entry of baby [babyId] (removing the baby, BABIES_AND_BACKUP §2.4): each is pushed to the
     * other device and, with an account, goes to the family's backup as a tombstone. Returns how many were deleted.
     */
    suspend fun deleteBabyEntries(babyId: String): Int

    /** Undo: writes [previous] back (re-inserting it when needed, for its own baby) with a new [Entry.updatedAtMillis]. */
    suspend fun restore(previous: Entry): Entry

    /** Merges an entry from the other device (last-writer-wins). Never echoes it back to sync. Returns true when stored. */
    suspend fun applyRemote(entry: Entry): Boolean

    /**
     * Merges a batch from the other device as one unit: applied oldest write first, with cross-device conflicts
     * (two running sleeps of one baby, the same log of one baby within the dedupe window) resolved once after the
     * batch. Returns how many entries were stored.
     */
    suspend fun applyRemoteAll(entries: List<Entry>): Int = entries.count { applyRemote(it) }

    /**
     * Settings › About › "Import data": merges the entries of an export ([com.ffmathy.pooparazzi.core.export.LogExport])
     * with the rules of [applyRemoteAll] (last-writer-wins by [Entry.updatedAtMillis], each baby's conflicts resolved
     * once), so importing the same file twice, or a file older than the log, changes nothing. What is stored goes on
     * to the other device and the backend like a local write. Returns how many entries were stored.
     */
    suspend fun importEntries(entries: List<Entry>): Int = applyRemoteAll(entries)

    /**
     * Removes soft-deleted entries whose deletion is older than [EntryRules.SOFT_DELETE_RETENTION_MILLIS] and has
     * reached sync (and the backend, where there is one). Their Data Layer items are kept (as `deleted = true`), so a device that was away the whole
     * time still learns of the deletion. Returns how many were purged.
     */
    suspend fun purgeDeleted(): Int
}
