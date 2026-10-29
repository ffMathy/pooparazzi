package com.ffmathy.pooparazzi.core.model

/** A broken invariant of an [Entry] (V1_ADDITIONS §1). */
enum class EntryViolation {
    /** The id is blank. */
    BLANK_ID,

    /** A start, end or update time is negative. */
    NEGATIVE_TIME,

    /** An interval ends before it starts. */
    END_BEFORE_START,

    /** An instant (any FEED, breastfeeds included, or a DIAPER) has an end time. */
    INSTANT_HAS_END,

    /** A non-FEED has a feed type or breast side. */
    FEED_FIELDS_ON_NON_FEED,

    /** A breast side on a FORMULA, FOOD or untyped feed. */
    SIDE_ON_NON_BREAST,

    /** An amount on a non-FORMULA entry. */
    AMOUNT_ON_NON_FORMULA,

    /** A formula amount of zero or less. */
    AMOUNT_NOT_POSITIVE,

    /** A non-DIAPER has diaper flags, colour or texture. */
    DIAPER_FIELDS_ON_NON_DIAPER,

    /** Colour or texture on a diaper without poop. */
    POOP_DETAILS_WITHOUT_POOP,

    /** A time lies in the future (checked by the repository for local writes, not by [validate]). */
    IN_FUTURE,
}

/** Thrown by repository writes that would break an [Entry] invariant. */
class InvalidEntryException(val violation: EntryViolation, val entry: Entry) :
    IllegalArgumentException("Invalid entry ${entry.id}: $violation")

/** Thrown when starting a sleep while one is already running. */
class AlreadyRunningException(val running: Entry) :
    IllegalStateException("A ${running.kind} is already running since ${running.startMillis} (${running.id})")

/**
 * Checks the structural invariants of [entry] (V1_ADDITIONS §1). Returns the first violation, or null when valid.
 * The single-running rule and future times need the repository's state and clock, so they are checked there.
 */
fun validate(entry: Entry): EntryViolation? {
    if (entry.id.isBlank()) return EntryViolation.BLANK_ID
    if (entry.startMillis < 0 || (entry.endMillis ?: 0) < 0 || entry.updatedAtMillis < 0) return EntryViolation.NEGATIVE_TIME

    val end = entry.endMillis
    if (entry.isInterval) {
        if (end != null && end < entry.startMillis) return EntryViolation.END_BEFORE_START
    } else if (end != null) {
        return EntryViolation.INSTANT_HAS_END
    }

    when (entry.kind) {
        EntryKind.FEED -> {
            if (entry.feedType != FeedType.BREAST && entry.breastSide != null) return EntryViolation.SIDE_ON_NON_BREAST
        }
        EntryKind.SLEEP, EntryKind.DIAPER -> {
            if (entry.feedType != null || entry.breastSide != null) return EntryViolation.FEED_FIELDS_ON_NON_FEED
        }
    }

    entry.amountMl?.let { amount ->
        if (entry.kind != EntryKind.FEED || entry.feedType != FeedType.FORMULA) return EntryViolation.AMOUNT_ON_NON_FORMULA
        if (amount <= 0) return EntryViolation.AMOUNT_NOT_POSITIVE
    }

    if (entry.kind == EntryKind.DIAPER) {
        if (!entry.poop && (entry.poopColor != null || entry.poopTexture != null)) return EntryViolation.POOP_DETAILS_WITHOUT_POOP
    } else if (entry.pee || entry.poop || entry.poopColor != null || entry.poopTexture != null) {
        return EntryViolation.DIAPER_FIELDS_ON_NON_DIAPER
    }
    return null
}

/** True when [validate] finds no violation. */
val Entry.isValid: Boolean get() = validate(this) == null

/** Returns this entry, or throws [InvalidEntryException] when it breaks an invariant. */
fun Entry.requireValid(): Entry {
    validate(this)?.let { throw InvalidEntryException(it, this) }
    return this
}
