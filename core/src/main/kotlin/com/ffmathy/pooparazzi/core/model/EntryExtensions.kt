package com.ffmathy.pooparazzi.core.model

/** True for SLEEP, the only entry with a start and an end. Feeds (breastfeeds included) and diapers are instants. */
val Entry.isInterval: Boolean
    get() = kind == EntryKind.SLEEP

/** True for a sleep that has not ended and is not deleted. */
val Entry.isRunning: Boolean
    get() = isInterval && endMillis == null && !deleted

val Entry.isSleep: Boolean get() = kind == EntryKind.SLEEP
val Entry.isFeed: Boolean get() = kind == EntryKind.FEED
val Entry.isDiaper: Boolean get() = kind == EntryKind.DIAPER
val Entry.isBreastfeed: Boolean get() = kind == EntryKind.FEED && feedType == FeedType.BREAST

/** The diaper kind of a DIAPER entry, or null for other kinds (or an invalid diaper with no flag). */
val Entry.diaperKind: DiaperKind?
    get() = if (kind == EntryKind.DIAPER) DiaperKind.of(pee, poop) else null

/**
 * Real duration of an interval in ms: up to [endMillis], or up to [now] while running. Never negative.
 * Instants have no duration (0). For anything shown on screen use [clockDurationMillis].
 */
fun Entry.durationMillis(now: Long): Long {
    if (!isInterval) return 0L
    val end = endMillis ?: now
    return (end - startMillis).coerceAtLeast(0L)
}

/**
 * The duration to **display** for an interval: whole clock minutes from its start to its end (or [now] while
 * running), so "12:40 – 13:27" always reads 47m whatever the seconds were ([clockMinutesBetween]).
 * Instants have no duration (0).
 */
fun Entry.clockDurationMillis(now: Long): Long {
    if (!isInterval) return 0L
    return clockDurationMillis(startMillis, endMillis ?: now)
}

/** The end used for display and day maths: [endMillis], or [now] while the interval runs; [startMillis] for instants. */
fun Entry.effectiveEnd(now: Long): Long = when {
    !isInterval -> startMillis
    else -> maxOf(endMillis ?: now, startMillis)
}
