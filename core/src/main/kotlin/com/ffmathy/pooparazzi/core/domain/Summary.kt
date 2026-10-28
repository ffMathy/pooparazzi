package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.clockMinutesBetween
import com.ffmathy.pooparazzi.core.model.effectiveEnd
import com.ffmathy.pooparazzi.core.model.isRunning
import java.time.LocalDate
import java.time.ZoneId

/** Counts for one local day (V1_ADDITIONS §1 "Today counts"). */
data class DayCounts(
    /** Every FEED of any type that starts in the day. */
    val feeds: Int,
    /** Diapers with pee. */
    val pee: Int,
    /** Diapers with poop (a pee + poop diaper counts once here and once in [pee]). */
    val poop: Int,
    /** Clock minutes of sleep inside the day, including a running sleep up to now (each sleep counted as displayed). */
    val sleepMinutes: Long,
)

/**
 * Everything the status surfaces need, derived from the entries at [now] in [zone].
 *
 * @property entries non-deleted entries that start at or before [now], oldest first.
 * @property lastFeed the feed with the latest time (any type). Time since the last feed is measured from it.
 * @property lastDiaper the latest diaper.
 * @property runningSleep the running sleep, if any.
 * @property lastSleep the completed sleep with the latest end; [lastSleepEnd] is "awake since" when no sleep runs.
 * @property today counts for the local day containing [now].
 */
data class Summary(
    val now: Long,
    val zone: ZoneId,
    val entries: List<Entry>,
    val lastFeed: Entry?,
    val lastDiaper: Entry?,
    val runningSleep: Entry?,
    val lastSleep: Entry?,
    val today: DayCounts,
) {
    /** End of the most recent completed sleep ("Awake … since 13:27"). */
    val lastSleepEnd: Long? get() = lastSleep?.endMillis

    /** The local date of [now]. */
    val todayDate: LocalDate get() = localDate(now, zone)

    /**
     * Time since the last feed started, or null when there is none. Like every displayed duration here it counts
     * whole clock minutes ([clockDurationMillis]), so it always agrees with the clock times printed next to it.
     */
    val sinceLastFeedMillis: Long? get() = lastFeed?.let { clockDurationMillis(it.startMillis, now) }

    /** Time since the last diaper (whole clock minutes), or null. */
    val sinceLastDiaperMillis: Long? get() = lastDiaper?.let { clockDurationMillis(it.startMillis, now) }

    /** Time awake since [lastSleepEnd] (whole clock minutes); null while a sleep runs or when no sleep has ended. */
    val awakeMillis: Long? get() = if (runningSleep != null) null else lastSleepEnd?.let { clockDurationMillis(it, now) }

    /** Entries overlapping [day] (instants in it, intervals that touch it), newest start first. For History. */
    fun dayEntries(day: LocalDate): List<Entry> {
        val bounds = DayBounds.of(day, zone)
        return entries
            .filter { overlaps(it, bounds) }
            .sortedWith(compareByDescending<Entry> { it.startMillis }.thenByDescending { it.id })
    }

    /** Counts for any [day] (clipped to [now]). */
    fun dayCounts(day: LocalDate): DayCounts = countsFor(entries, DayBounds.of(day, zone), now)

    /** Ribbon geometry for [day] (History). */
    fun ribbon(day: LocalDate): Ribbon = Ribbon.build(entries, day, zone, now)

    private fun overlaps(entry: Entry, day: DayBounds): Boolean {
        if (entry.startMillis >= day.endMillis) return false
        val end = entry.effectiveEnd(now)
        return if (end == entry.startMillis) entry.startMillis >= day.startMillis else end > day.startMillis
    }
}

/**
 * Derives the [Summary] for [now] in [zone] (V1_ADDITIONS §1, §6). Deleted entries and entries that start
 * after [now] are ignored; intervals are clipped at [now].
 */
fun summarize(entries: List<Entry>, now: Long, zone: ZoneId): Summary {
    val visible = entries
        .filter { !it.deleted && it.startMillis <= now }
        .sortedWith(compareBy<Entry> { it.startMillis }.thenBy { it.id })
    val feeds = visible.filter { it.kind == EntryKind.FEED }
    val diapers = visible.filter { it.kind == EntryKind.DIAPER }
    val sleeps = visible.filter { it.kind == EntryKind.SLEEP }
    return Summary(
        now = now,
        zone = zone,
        entries = visible,
        lastFeed = feeds.lastOrNull(),
        lastDiaper = diapers.lastOrNull(),
        runningSleep = sleeps.lastOrNull { it.isRunning },
        lastSleep = sleeps.filter { it.endMillis != null && it.endMillis <= now }.maxByOrNull { it.endMillis!! },
        today = countsFor(visible, DayBounds.containing(now, zone), now),
    )
}

internal fun countsFor(entries: List<Entry>, day: DayBounds, now: Long): DayCounts {
    var feeds = 0
    var pee = 0
    var poop = 0
    var sleepMinutes = 0L
    val until = minOf(day.endMillis, now)
    for (entry in entries) {
        if (entry.deleted) continue
        val instantInDay = entry.startMillis in day && entry.startMillis <= now
        when (entry.kind) {
            EntryKind.FEED -> if (instantInDay) feeds++
            EntryKind.DIAPER -> if (instantInDay) {
                if (entry.pee) pee++
                if (entry.poop) poop++
            }
            EntryKind.SLEEP -> {
                val end = minOf(entry.endMillis ?: now, until)
                sleepMinutes += clockMinutesBetween(maxOf(entry.startMillis, day.startMillis), end)
            }
        }
    }
    return DayCounts(feeds = feeds, pee = pee, poop = poop, sleepMinutes = sleepMinutes)
}
