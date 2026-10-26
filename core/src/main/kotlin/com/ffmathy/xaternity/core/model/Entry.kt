package com.ffmathy.xaternity.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** What an [Entry] records. */
@Serializable
enum class EntryKind { SLEEP, FEED, DIAPER }

/** The kind of feed. Only [BREAST] is an interval (a timed breastfeed); the others are instants. */
@Serializable
enum class FeedType { BREAST, FORMULA, FOOD }

/** The breast side(s) of a breastfeed. Picked when the feed ends; the order of the two-sided values matters. */
@Serializable
enum class BreastSide { LEFT, RIGHT, LEFT_THEN_RIGHT, RIGHT_THEN_LEFT }

/** Optional poop colour of a diaper with poop. */
@Serializable
enum class PoopColor { YELLOW, GREEN, BROWN, BLACK, RED, PALE }

/** Optional poop texture of a diaper with poop. */
@Serializable
enum class PoopTexture { WATERY, SEEDY, MUSHY, FORMED, HARD }

/**
 * Display grouping of a diaper's flags. Not stored: derived from [Entry.pee] and [Entry.poop].
 * Status surfaces call [BOTH] "Both"; receipts and History call it "Pee + poop".
 */
enum class DiaperKind {
    PEE, POOP, BOTH;

    companion object {
        /** The kind for the given flags, or null when neither is set. */
        fun of(pee: Boolean, poop: Boolean): DiaperKind? = when {
            pee && poop -> BOTH
            pee -> PEE
            poop -> POOP
            else -> null
        }
    }
}

/**
 * One logged event. Times are epoch milliseconds (UTC); display converts to local time.
 *
 * Invariants (V1_ADDITIONS §1) are checked by [validate] and enforced by the repository:
 * - an *interval* is a SLEEP or a FEED with [feedType] == BREAST; every other entry is an instant with [endMillis] == null;
 * - an interval is *running* while [endMillis] == null and it is not [deleted];
 * - a DIAPER has [pee] or [poop]; [poopColor] and [poopTexture] are null unless [poop];
 * - [breastSide] is null while a breastfeed runs and set once it has ended.
 */
@Serializable
data class Entry(
    /** UUID, stable across devices. */
    val id: String,
    val kind: EntryKind,
    /** Start (intervals) or time (instants), epoch ms UTC. */
    val startMillis: Long,
    /** End of an interval, epoch ms UTC; null while running and always null for instants. */
    val endMillis: Long? = null,
    /** FEED only. */
    val feedType: FeedType? = null,
    /** FEED + BREAST only; set when the feed ends. */
    val breastSide: BreastSide? = null,
    /** DIAPER only. */
    val pee: Boolean = false,
    /** DIAPER only. */
    val poop: Boolean = false,
    /** DIAPER with poop only (optional). */
    val poopColor: PoopColor? = null,
    /** DIAPER with poop only (optional). */
    val poopTexture: PoopTexture? = null,
    /** Last-writer-wins clock for sync, epoch ms. Bumped on every local write. */
    val updatedAtMillis: Long,
    /** Soft delete marker (rows are purged 7 days after deletion). */
    val deleted: Boolean = false,
) {
    companion object {
        /** A new random UUID for an entry id. */
        fun newId(): String = UUID.randomUUID().toString()

        /** A sleep starting at [startMillis]; running when [endMillis] is null. */
        fun sleep(
            startMillis: Long,
            endMillis: Long? = null,
            id: String = newId(),
            updatedAtMillis: Long = endMillis ?: startMillis,
        ) = Entry(id = id, kind = EntryKind.SLEEP, startMillis = startMillis, endMillis = endMillis, updatedAtMillis = updatedAtMillis)

        /** A breastfeed starting at [startMillis]; running (and without a side) when [endMillis] is null. */
        fun breastfeed(
            startMillis: Long,
            endMillis: Long? = null,
            side: BreastSide? = null,
            id: String = newId(),
            updatedAtMillis: Long = endMillis ?: startMillis,
        ) = Entry(
            id = id,
            kind = EntryKind.FEED,
            startMillis = startMillis,
            endMillis = endMillis,
            feedType = FeedType.BREAST,
            breastSide = side,
            updatedAtMillis = updatedAtMillis,
        )

        /** An instant feed (FORMULA or FOOD) at [atMillis]. Use [breastfeed] for BREAST. */
        fun feed(
            type: FeedType,
            atMillis: Long,
            id: String = newId(),
            updatedAtMillis: Long = atMillis,
        ): Entry {
            require(type != FeedType.BREAST) { "A breastfeed is an interval: use Entry.breastfeed()" }
            return Entry(id = id, kind = EntryKind.FEED, startMillis = atMillis, feedType = type, updatedAtMillis = updatedAtMillis)
        }

        /** A diaper at [atMillis]. Colour and texture are dropped unless [poop]. */
        fun diaper(
            atMillis: Long,
            pee: Boolean,
            poop: Boolean,
            color: PoopColor? = null,
            texture: PoopTexture? = null,
            id: String = newId(),
            updatedAtMillis: Long = atMillis,
        ) = Entry(
            id = id,
            kind = EntryKind.DIAPER,
            startMillis = atMillis,
            pee = pee,
            poop = poop,
            poopColor = color.takeIf { poop },
            poopTexture = texture.takeIf { poop },
            updatedAtMillis = updatedAtMillis,
        )
    }
}
