package com.ffmathy.xaternity.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ffmathy.xaternity.core.model.FeedType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EntryDao {

    /** Non-deleted entries, oldest first. */
    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY startMillis ASC, id ASC")
    abstract fun observeActive(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    abstract suspend fun get(id: String): EntryEntity?

    @Query("SELECT * FROM entries ORDER BY startMillis ASC, id ASC")
    abstract suspend fun all(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND endMillis IS NULL AND kind = 'SLEEP' ORDER BY startMillis ASC, id ASC")
    abstract suspend fun runningSleeps(): List<EntryEntity>

    @Query(
        "SELECT * FROM entries WHERE deleted = 0 AND endMillis IS NULL AND kind = 'FEED' AND feedType = 'BREAST' " +
            "ORDER BY startMillis ASC, id ASC",
    )
    abstract suspend fun runningBreastfeeds(): List<EntryEntity>

    @Query(
        "SELECT * FROM entries WHERE deleted = 0 AND kind = 'FEED' AND feedType = :type " +
            "AND startMillis BETWEEN :fromMillis AND :toMillis ORDER BY startMillis ASC",
    )
    abstract suspend fun feedsBetween(type: FeedType, fromMillis: Long, toMillis: Long): List<EntryEntity>

    @Query(
        "SELECT * FROM entries WHERE deleted = 0 AND kind = 'DIAPER' AND pee = :pee AND poop = :poop " +
            "AND startMillis BETWEEN :fromMillis AND :toMillis ORDER BY startMillis ASC",
    )
    abstract suspend fun diapersBetween(pee: Boolean, poop: Boolean, fromMillis: Long, toMillis: Long): List<EntryEntity>

    /** Rows with local writes not yet handed to sync. */
    @Query("SELECT * FROM entries WHERE pendingSync = 1")
    abstract suspend fun pendingSync(): List<EntryEntity>

    /** Clears the pending flag, but only if the row has not been written again since [updatedAtMillis]. */
    @Query("UPDATE entries SET pendingSync = 0 WHERE id = :id AND updatedAtMillis = :updatedAtMillis")
    abstract suspend fun markSynced(id: String, updatedAtMillis: Long): Int

    /** Unconditional write. Prefer [upsert] for anything that may race with sync. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun write(entity: EntryEntity)

    /**
     * Last-writer-wins upsert ([LastWriterWins]): inserts [entity], or replaces the stored row only when the
     * incoming version wins (a newer `updatedAtMillis`, or the same one with different content that wins the
     * deterministic tie-break). An identical version is not rewritten, so re-merging the same item (startup
     * pull, echoes) costs a read and no write. The stored row keeps its [EntryEntity.createdLocally].
     * Returns true when the row was written.
     */
    @Transaction
    open suspend fun upsert(entity: EntryEntity): Boolean {
        val stored = get(entity.id)
        if (stored == null) {
            write(entity)
            return true
        }
        if (!LastWriterWins.incomingWins(stored = stored.toEntry(), incoming = entity.toEntry())) return false
        write(entity.copy(createdLocally = stored.createdLocally))
        return true
    }

    /**
     * Soft-deleted rows whose deletion is older than [cutoffMillis] and has reached sync (`pendingSync = 0`):
     * a deletion that never left this device must not be purged, or the other device would never learn of it.
     */
    @Query("SELECT id FROM entries WHERE deleted = 1 AND pendingSync = 0 AND updatedAtMillis < :cutoffMillis")
    abstract suspend fun deletedIdsBefore(cutoffMillis: Long): List<String>

    @Query("DELETE FROM entries WHERE id IN (:ids)")
    abstract suspend fun deleteRows(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTombstones(tombstones: List<PurgedEntryEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM purged_entries WHERE id = :id)")
    abstract suspend fun isPurged(id: String): Boolean

    /**
     * Removes soft-deleted rows whose deletion ([EntryEntity.updatedAtMillis]) is older than [cutoffMillis]
     * and records tombstones for them. Returns the purged ids.
     */
    @Transaction
    open suspend fun purgeDeletedBefore(cutoffMillis: Long, nowMillis: Long): List<String> {
        val ids = deletedIdsBefore(cutoffMillis)
        if (ids.isEmpty()) return ids
        ids.chunked(MAX_SQL_ARGS).forEach { deleteRows(it) }
        insertTombstones(ids.map { PurgedEntryEntity(it, nowMillis) })
        return ids
    }

    private companion object {
        const val MAX_SQL_ARGS = 500
    }
}
