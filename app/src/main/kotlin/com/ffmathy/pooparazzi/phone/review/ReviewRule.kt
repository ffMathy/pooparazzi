package com.ffmathy.pooparazzi.phone.review

import com.ffmathy.pooparazzi.core.model.Entry
import java.time.Instant
import java.time.ZoneId

/**
 * What the phone remembers about the review prompt (DESIGN_SPEC §3.3.1, "Enjoying Pooparazzi?").
 *
 * @param firstSeenMillis when Home was first shown on this phone, or null before that.
 * @param done the parent chose "Rate on Google Play" or "Don't ask again": never asked again.
 * @param timesAsked how many times the parent answered "Not now" (or dismissed the dialog).
 * @param askAgainAtMillis after "Not now", not before this time.
 */
data class ReviewMemory(
    val firstSeenMillis: Long? = null,
    val done: Boolean = false,
    val timesAsked: Int = 0,
    val askAgainAtMillis: Long = 0L,
)

/**
 * When to ask for a review: only once the app is in real use. Pure Kotlin, unit-tested without Android.
 *
 * Asks when all of these hold:
 * - the parent has not rated or said "Don't ask again", and has said "Not now" fewer than [MAX_ASKS] times, the last
 *   time at least [ASK_AGAIN_DAYS] days ago;
 * - Home was first seen at least [MIN_DAYS] days ago;
 * - since then (counting entries backdated to the day before), at least [MIN_ENTRIES] entries are in the log, logged on
 *   at least [MIN_LOG_DAYS] different days. Entries from an import or a restored backup, older than that, do not count.
 */
object ReviewRule {
    const val MIN_DAYS = 3
    const val MIN_ENTRIES = 15
    const val MIN_LOG_DAYS = 3
    const val ASK_AGAIN_DAYS = 30
    const val MAX_ASKS = 2

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun shouldAsk(memory: ReviewMemory, entries: List<Entry>, now: Long, zone: ZoneId): Boolean {
        val firstSeen = memory.firstSeenMillis ?: return false
        if (memory.done || memory.timesAsked >= MAX_ASKS || now < memory.askAgainAtMillis) return false
        if (now - firstSeen < MIN_DAYS * DAY_MILLIS) return false
        val real = entries.filter { !it.deleted && it.startMillis >= firstSeen - DAY_MILLIS && it.startMillis <= now }
        if (real.size < MIN_ENTRIES) return false
        val days = real.mapTo(HashSet()) { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() }
        return days.size >= MIN_LOG_DAYS
    }

    /** The memory after "Not now": asked once more at most, [ASK_AGAIN_DAYS] days later. */
    fun later(memory: ReviewMemory, now: Long): ReviewMemory =
        memory.copy(timesAsked = memory.timesAsked + 1, askAgainAtMillis = now + ASK_AGAIN_DAYS * DAY_MILLIS)
}
