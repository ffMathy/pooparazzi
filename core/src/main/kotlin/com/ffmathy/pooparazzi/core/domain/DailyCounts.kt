package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.clockMinutesBetween
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * [DayCounts] for every local day from [from] to [to] (both included, oldest first), in one pass over [entries].
 * Each day's counts equal [Summary.dayCounts] for that day: deleted entries and entries that start after [now] are
 * left out, feeds and diapers count on the day they happen, and a sleep counts the clock minutes it spends inside each
 * day, a running sleep up to [now]. Empty when [to] is before [from].
 */
fun dailyCounts(entries: List<Entry>, from: LocalDate, to: LocalDate, now: Long, zone: ZoneId): List<DayCounts> {
    if (to < from) return emptyList()
    val days = ChronoUnit.DAYS.between(from, to).toInt() + 1
    // starts[i] is the start of day i; starts[days] is the end of the last day (DST days are 23 or 25 hours long).
    val starts = LongArray(days + 1) { from.plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
    val feeds = IntArray(days)
    val pee = IntArray(days)
    val poop = IntArray(days)
    val sleepMinutes = LongArray(days)

    for (entry in entries) {
        if (entry.deleted || entry.startMillis > now) continue
        val first = dayIndex(starts, entry.startMillis)
        when (entry.kind) {
            EntryKind.FEED -> if (first in 0 until days) feeds[first]++
            EntryKind.DIAPER -> if (first in 0 until days) {
                if (entry.pee) pee[first]++
                if (entry.poop) poop[first]++
            }
            EntryKind.SLEEP -> {
                val end = minOf(entry.endMillis ?: now, now)
                val last = minOf(dayIndex(starts, end), days - 1)
                for (i in maxOf(first, 0)..last) {
                    sleepMinutes[i] += clockMinutesBetween(maxOf(entry.startMillis, starts[i]), minOf(end, starts[i + 1]))
                }
            }
        }
    }
    return List(days) { DayCounts(feeds = feeds[it], pee = pee[it], poop = poop[it], sleepMinutes = sleepMinutes[it]) }
}

/** The index of the day in [starts] that contains [millis]: −1 before the first day, the day count after the last. */
private fun dayIndex(starts: LongArray, millis: Long): Int {
    val found = starts.binarySearch(millis)
    return if (found >= 0) found else -found - 2
}
