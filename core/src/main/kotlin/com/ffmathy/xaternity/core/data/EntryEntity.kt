package com.ffmathy.xaternity.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture

/** Room row for an [Entry]. Enums are stored by name. Apps work with [Entry]; this type is storage only. */
@Entity(
    tableName = "entries",
    indices = [Index("startMillis"), Index("deleted", "kind")],
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
) {
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
    )

    companion object {
        fun from(entry: Entry, pendingSync: Boolean, createdLocally: Boolean = false) = EntryEntity(
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
