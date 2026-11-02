package com.ffmathy.pooparazzi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** What an [Entry] records. */
@Serializable
enum class EntryKind { SLEEP, FEED, DIAPER }

/**
 * The kind of feed. Every feed is an instant (one time); a [BREAST] feed may also record the side order, a [BOTTLE]
 * feed what was in the bottle ([BottleContents]) and the amount. The type is optional: a FEED with no type
 * ([Entry.feedType] == null) records only its time.
 */
@Serializable
enum class FeedType {
    BREAST,

    /** A bottle feed. Stored as "FORMULA" (its name before the bottle could hold breast milk), so old data reads. */
    @SerialName(STORED_BOTTLE)
    BOTTLE,
    FOOD;

    /** The stored name: [name], except "FORMULA" for [BOTTLE]. Room and the JSON keep this. */
    val storedName: String get() = if (this == BOTTLE) STORED_BOTTLE else name

    companion object {
        private const val STORED_BOTTLE = "FORMULA"

        /** The type stored as [stored] ([storedName]); null for null or an unknown name. */
        fun fromStored(stored: String?): FeedType? = entries.firstOrNull { it.storedName == stored }
    }
}

/** What a [FeedType.BOTTLE] feed held (optional): expressed breast milk or formula. */
@Serializable
enum class BottleContents { MILK, FORMULA }

/** One breast, as ticked in the side checkboxes of a breast feed. */
enum class Breast { LEFT, RIGHT }

/**
 * The breast side(s) of a breastfeed, picked (optionally) when it is logged. The order of the two-sided values
 * matters. The UI ticks [Breast]es in order; [fromOrder] maps that order to the stored value and [order] back.
 */
@Serializable
enum class BreastSide {
    LEFT, RIGHT, LEFT_THEN_RIGHT, RIGHT_THEN_LEFT;

    /** The breasts in the order they were fed: `[LEFT, RIGHT]` for [LEFT_THEN_RIGHT]. */
    val order: List<Breast>
        get() = when (this) {
            LEFT -> listOf(Breast.LEFT)
            RIGHT -> listOf(Breast.RIGHT)
            LEFT_THEN_RIGHT -> listOf(Breast.LEFT, Breast.RIGHT)
            RIGHT_THEN_LEFT -> listOf(Breast.RIGHT, Breast.LEFT)
        }

    companion object {
        /**
         * The side for breasts ticked in [order] (repeats after the first are ignored): Left then Right is
         * [LEFT_THEN_RIGHT], one breast is that side, and nothing ticked is null (no side recorded).
         */
        fun fromOrder(order: List<Breast>): BreastSide? {
            val distinct = order.distinct()
            return when (distinct.size) {
                0 -> null
                1 -> if (distinct[0] == Breast.LEFT) LEFT else RIGHT
                else -> if (distinct[0] == Breast.LEFT) LEFT_THEN_RIGHT else RIGHT_THEN_LEFT
            }
        }

        /** The new tick order after the user taps [breast]: unticking removes it, ticking appends it. */
        fun toggle(order: List<Breast>, breast: Breast): List<Breast> =
            if (breast in order) order - breast else order.distinct() + breast
    }
}

/** Optional poop colour of a diaper with poop. */
@Serializable
enum class PoopColor { YELLOW, GREEN, BROWN, BLACK, RED, PALE }

/** Optional poop texture of a diaper with poop. */
@Serializable
enum class PoopTexture { WATERY, SEEDY, MUSHY, FORMED, HARD }

/**
 * Display grouping of a diaper's flags. Not stored: derived from [Entry.pee] and [Entry.poop].
 * Status surfaces call [BOTH] "Both"; receipts and History call it "Pee + poop". [PLAIN] is a diaper change
 * logged with neither flag ("Diaper").
 */
enum class DiaperKind {
    PEE, POOP, BOTH, PLAIN;

    companion object {
        /** The kind for the given flags; [PLAIN] when neither is set. */
        fun of(pee: Boolean, poop: Boolean): DiaperKind = when {
            pee && poop -> BOTH
            pee -> PEE
            poop -> POOP
            else -> PLAIN
        }
    }
}

/**
 * One logged event. Times are epoch milliseconds (UTC); display converts to local time.
 *
 * Invariants (V1_ADDITIONS §1) are checked by [validate] and enforced by the repository:
 * - an *interval* is a SLEEP; every other entry (every FEED, breastfeeds included, and every DIAPER) is an instant
 *   with [endMillis] == null;
 * - a sleep is *running* while [endMillis] == null and it is not [deleted];
 * - a FEED's [feedType] is optional (null = just the time);
 * - a DIAPER's [pee] and [poop] are optional (neither = a plain change); [poopColor] and [poopTexture] are null unless [poop];
 * - [breastSide] is optional and only on a BREAST feed;
 * - [bottleContents] and [amountMl] are optional and only on a BOTTLE feed; [amountMl] is positive when set.
 *
 * Every entry belongs to one baby, [babyId] (BABIES_AND_BACKUP §2.1); an edit never moves it to another.
 *
 * Older app versions stored a breastfeed as a timed interval (with an [endMillis], or none while it ran). Such
 * entries are read through [normalized], which turns them into instants at their start.
 */
@Serializable
data class Entry(
    /** UUID, stable across devices. */
    val id: String,
    val kind: EntryKind,
    /** Start (sleep) or time (instants), epoch ms UTC. */
    val startMillis: Long,
    /** End of a sleep, epoch ms UTC; null while it runs and always null for instants. */
    val endMillis: Long? = null,
    /** FEED only; optional (null = a feed logged with just its time). */
    val feedType: FeedType? = null,
    /** FEED + BREAST only; optional, in the order the sides were fed. */
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
    /** The [Baby.id] this entry belongs to. JSON from before several babies has none and reads as the first baby. */
    val babyId: String = Baby.FIRST_ID,
    /** FEED + BOTTLE only; optional, the amount given in millilitres (shown in fluid ounces where those are used). */
    val amountMl: Int? = null,
    /** FEED + BOTTLE only; optional, what the bottle held. */
    val bottleContents: BottleContents? = null,
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
            babyId: String = Baby.FIRST_ID,
        ) = Entry(
            id = id,
            kind = EntryKind.SLEEP,
            startMillis = startMillis,
            endMillis = endMillis,
            updatedAtMillis = updatedAtMillis,
            babyId = babyId,
        )

        /** A breastfeed at [atMillis] with the optional side order [side] (null = just the time). */
        fun breastfeed(
            atMillis: Long,
            side: BreastSide? = null,
            id: String = newId(),
            updatedAtMillis: Long = atMillis,
            babyId: String = Baby.FIRST_ID,
        ) = Entry(
            id = id,
            kind = EntryKind.FEED,
            startMillis = atMillis,
            feedType = FeedType.BREAST,
            breastSide = side,
            updatedAtMillis = updatedAtMillis,
            babyId = babyId,
        )

        /**
         * A feed at [atMillis] of [type] (null = just the time). [side] is kept only for [FeedType.BREAST]
         * (see [breastfeed]), [amountMl] and [contents] only for [FeedType.BOTTLE].
         */
        fun feed(
            type: FeedType?,
            atMillis: Long,
            side: BreastSide? = null,
            id: String = newId(),
            updatedAtMillis: Long = atMillis,
            babyId: String = Baby.FIRST_ID,
            amountMl: Int? = null,
            contents: BottleContents? = null,
        ): Entry = Entry(
            id = id,
            kind = EntryKind.FEED,
            startMillis = atMillis,
            feedType = type,
            breastSide = side.takeIf { type == FeedType.BREAST },
            updatedAtMillis = updatedAtMillis,
            babyId = babyId,
            amountMl = amountMl.takeIf { type == FeedType.BOTTLE },
            bottleContents = contents.takeIf { type == FeedType.BOTTLE },
        )

        /** A diaper at [atMillis]. Colour and texture are dropped unless [poop]. */
        fun diaper(
            atMillis: Long,
            pee: Boolean,
            poop: Boolean,
            color: PoopColor? = null,
            texture: PoopTexture? = null,
            id: String = newId(),
            updatedAtMillis: Long = atMillis,
            babyId: String = Baby.FIRST_ID,
        ) = Entry(
            id = id,
            kind = EntryKind.DIAPER,
            startMillis = atMillis,
            pee = pee,
            poop = poop,
            poopColor = color.takeIf { poop },
            poopTexture = texture.takeIf { poop },
            updatedAtMillis = updatedAtMillis,
            babyId = babyId,
        )
    }
}

/**
 * This entry as the current model reads it. A breastfeed from an older app version was a timed interval: its end
 * (or its "running" state, when it never ended) is dropped, so it becomes an instant at its start. A blank
 * [Entry.babyId] becomes the first baby's ([Baby.FIRST_ID]). Any other entry is returned unchanged. Every read path
 * (Room rows, sync JSON, the backend) goes through this.
 */
fun Entry.normalized(): Entry {
    var entry = this
    if (entry.kind == EntryKind.FEED && entry.endMillis != null) entry = entry.copy(endMillis = null)
    if (entry.babyId.isBlank()) entry = entry.copy(babyId = Baby.FIRST_ID)
    return entry
}
