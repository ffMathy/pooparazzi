package com.ffmathy.pooparazzi.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.normalized
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture

/**
 * Room row for an [Entry]. Enums are stored by name. Apps work with [Entry]; this type is storage only.
 *
 * A row has two pending flags (BABIES_AND_BACKUP §4.2): [pendingSync] for the other device and [cloudPending] for the
 * backend. A soft-deleted row is purged only once both are clear.
 */
@Entity(
    tableName = "entries",
    indices = [Index("startMillis"), Index("deleted", "kind"), Index("babyId", "deleted", "kind")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val kind: EntryKind,
    val startMillis: Long,
    val endMillis: Long?,
    val feedType: FeedType?,
    val breastSide: BreastSide?,
    val pee: Boolean,
    val poop: Boolean,
    val poopColor: PoopColor?,
    val poopTexture: PoopTexture?,
    val updatedAtMillis: Long,
    val deleted: Boolean,
    /** True while this row holds a local write that has not been handed to the Wearable Data Layer yet. */
    val pendingSync: Boolean,
    /**
     * True when this device created the entry (false when it arrived from the other device). Only the creating
     * device auto-deletes an entry that lost a cross-device conflict, so a stale copy here can never delete
     * the other device's newer data (see [RoomEntryRepository.applyRemoteAll]).
     */
    val createdLocally: Boolean = false,
    /** The baby the entry belongs to ([Entry.babyId]). Rows from before several babies are the first baby's. */
    @ColumnInfo(defaultValue = Baby.FIRST_ID)
    val babyId: String = Baby.FIRST_ID,
    /**
     * True while the backend lacks this row's current version. Always false on a device without a backend (the watch,
     * builds without one), so it never holds up a purge there.
     */
    @ColumnInfo(defaultValue = "0")
    val cloudPending: Boolean = false,
) {
    /** The entry this row holds, [normalized] (a timed breastfeed stored by an older version reads as an instant). */
    fun toEntry(): Entry = Entry(
        id = id,
        kind = kind,
        startMillis = startMillis,
        endMillis = endMillis,
        feedType = feedType,
        breastSide = breastSide,
        pee = pee,
        poop = poop,
        poopColor = poopColor,
        poopTexture = poopTexture,
        updatedAtMillis = updatedAtMillis,
        deleted = deleted,
        babyId = babyId,
    ).normalized()

    companion object {
        fun from(
            entry: Entry,
            pendingSync: Boolean,
            createdLocally: Boolean = false,
            cloudPending: Boolean = false,
        ) = EntryEntity(
            id = entry.id,
            kind = entry.kind,
            startMillis = entry.startMillis,
            endMillis = entry.endMillis,
            feedType = entry.feedType,
            breastSide = entry.breastSide,
            pee = entry.pee,
            poop = entry.poop,
            poopColor = entry.poopColor,
            poopTexture = entry.poopTexture,
            updatedAtMillis = entry.updatedAtMillis,
            deleted = entry.deleted,
            pendingSync = pendingSync,
            createdLocally = createdLocally,
            babyId = entry.babyId,
            cloudPending = cloudPending,
        )
    }
}

/**
 * Tombstone of a purged entry. Remote copies of a purged id are ignored, so an old Data Layer item
 * on the other device cannot bring a deleted entry back after the 7-day purge.
 */
@Entity(tableName = "purged_entries")
data class PurgedEntryEntity(
    @PrimaryKey val id: String,
    val purgedAtMillis: Long,
)
