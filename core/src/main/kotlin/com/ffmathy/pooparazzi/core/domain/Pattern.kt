package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * A typical stretch: half of the past stretches lasted [lowMillis]..[highMillis] (the interquartile range) and
 * [medianMillis] is the middle one. [samples] is how many stretches it rests on, after the ones unlike the rest were
 * left out (see [dailyPattern]).
 */
data class PatternInterval(val medianMillis: Long, val lowMillis: Long, val highMillis: Long, val samples: Int)

/** The typical stretches of one part of the day (by day or by night, by when each stretch starts). */
data class PatternIntervals(
    /** From one feed to the next. */
    val betweenFeeds: PatternInterval?,
    /** From one diaper to the next. */
    val betweenDiapers: PatternInterval?,
    /** How long a sleep lasts. */
    val sleep: PatternInterval?,
    /** Awake from the end of a sleep to the start of the next. */
    val awake: PatternInterval?,
) {
    val isEmpty: Boolean get() = betweenFeeds == null && betweenDiapers == null && sleep == null && awake == null
}

/**
 * The typical day over the logged days [from]..[to] (Statistics' "Typical day" card): when in the day the baby usually
 * sleeps, feeds and gets a diaper, and the usual time between them.
 *
 * Each of [sleep], [feeds] and [diapers] has one share 0..1 per [PatternRules.SLOT_MINUTES] slot of the local day
 * (slot 0 starts at 00:00): for sleep, the share of the slot spent asleep on an average day; for feeds and diapers,
 * the share of days with one starting in the slot. Only days on which that kind was logged count (like Statistics),
 * so a forgotten day does not water the pattern down, and of those only the days that look like the others (see
 * [dailyPattern]); a lane is empty (all 0) when no day counts.
 *
 * @property days the days of [from]..[to] on which anything was logged.
 */
data class DailyPattern(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val sleep: List<Float>,
    val feeds: List<Float>,
    val diapers: List<Float>,
    val day: PatternIntervals,
    val night: PatternIntervals,
) {
    /** Start of slot [index] as a fraction 0..1 of a day of [PatternRules.SLOTS] slots. */
    fun slotFraction(index: Int): Float = index.toFloat() / PatternRules.SLOTS
}

/** Tuning of [dailyPattern]. */
object PatternRules {
    /** Fewer logged days than this and there is no pattern yet. */
    const val MIN_DAYS = 3

    /** An interval needs at least this many stretches. */
    const val MIN_SAMPLES = 3

    const val SLOT_MINUTES = 30
    const val SLOTS = 24 * 60 / SLOT_MINUTES

    /**
     * How far from the middle a value may be and still look like the others, in median absolute deviations (scaled
     * to a standard deviation's size). Further out it is noise and is left out of the pattern.
     */
    const val SIMILAR_DEVIATIONS = 3.0

    /** A day's sleep may be this share of the usual day's off and still count. */
    const val MIN_SLEEP_TOLERANCE_SHARE = 0.25

    /** A day's feeds or diapers may be this share of the usual count off (and at least 1) and still count. */
    const val MIN_COUNT_TOLERANCE_SHARE = 0.4

    /** A stretch may be this share of the middle stretch off (and at least [MIN_STRETCH_TOLERANCE_MILLIS]) and still count. */
    const val MIN_STRETCH_TOLERANCE_SHARE = 0.25
    const val MIN_STRETCH_TOLERANCE_MILLIS = 15 * 60_000L
}

/**
 * The [DailyPattern] of the days [from]..[to] (Statistics' range; today counts only up to [now]), or null when fewer
 * than [PatternRules.MIN_DAYS] of them have anything logged. A stretch is by night
 * when it starts in [nightStart]..[nightEnd] (the night window, 19:00–07:00 by default), otherwise by day. Feeds or
 * diapers under [ForecastRules.MIN_GAP_MILLIS] apart are one (a feed per side), and gaps over
 * [ForecastRules.MAX_GAP_MILLIS] are a forgotten log, not a stretch. Entries after [now] are ignored.
 *
 * Noise is left out, so the pattern rests on the values that look alike: a day whose sleep in all is far from the usual
 * day's, or whose count of feeds or diapers is far from the usual count (a half-logged or a
 * double-logged day), leaves that lane and its stretches; and of the stretches left, one far from the middle one (a
 * forgotten wake-up, a one-off long nap) is left out of its interval. "Far" is over [PatternRules.SIMILAR_DEVIATIONS]
 * median absolute deviations from the middle, and never closer than the rules' minimum tolerances, so ordinary spread
 * stays in.
 */
fun dailyPattern(
    entries: List<Entry>,
    from: LocalDate,
    to: LocalDate,
    now: Long,
    zone: ZoneId,
    nightStart: LocalTime = LocalTime.of(19, 0),
    nightEnd: LocalTime = LocalTime.of(7, 0),
): DailyPattern? {
    val lanes = Lanes.of(entries, from, to, now, zone)
    if (lanes.loggedDays.size < PatternRules.MIN_DAYS) return null
    val windowStart = lanes.windowStart
    val feeds = lanes.feeds
    val diapers = lanes.diapers
    val sleeps = lanes.sleeps
    val sleepByDay = lanes.sleepByDay
    val typicalSleepDays = lanes.typicalSleepDays
    val typicalFeedDays = lanes.typicalFeedDays
    val typicalDiaperDays = lanes.typicalDiaperDays
    val sleepShares = List(PatternRules.SLOTS) { slot ->
        if (typicalSleepDays.isEmpty()) 0f else typicalSleepDays.sumOf { sleepByDay.getValue(it)[slot].toDouble() }.toFloat() / typicalSleepDays.size
    }

    fun instantShares(instants: List<Entry>, days: Set<LocalDate>): List<Float> {
        if (days.isEmpty()) return List(PatternRules.SLOTS) { 0f }
        val hits = Array(PatternRules.SLOTS) { mutableSetOf<LocalDate>() }
        for (entry in instants) {
            val date = localDate(entry.startMillis, zone)
            if (date in days) hits[slotOf(entry.startMillis, zone)] += date
        }
        return hits.map { it.size.toFloat() / days.size }
    }

    fun List<Pair<Long, Long>>.onDays(days: Set<LocalDate>) = filter { (start, _) -> localDate(start, zone) in days }

    val completedSleeps = sleeps.filter { it.endMillis != null && it.endMillis <= now && it.startMillis >= windowStart }
    val feedGaps = gaps(feeds).onDays(typicalFeedDays)
    val diaperGaps = gaps(diapers).onDays(typicalDiaperDays)
    val sleepLengths = completedSleeps.map { it.startMillis to it.endMillis!! - it.startMillis }.onDays(typicalSleepDays)
    val wakeWindows = completedSleeps.zipWithNext { a, b -> a.endMillis!! to b.startMillis - a.endMillis }
        .filter { (_, length) -> length in ForecastRules.MIN_GAP_MILLIS..ForecastRules.MAX_GAP_MILLIS }
        .onDays(typicalSleepDays)

    fun intervals(night: Boolean): PatternIntervals {
        fun pick(stretches: List<Pair<Long, Long>>) =
            interval(stretches.filter { (start, _) -> inNight(localTime(start, zone), nightStart, nightEnd) == night }.map { it.second })
        return PatternIntervals(pick(feedGaps), pick(diaperGaps), pick(sleepLengths), pick(wakeWindows))
    }

    return DailyPattern(
        from = from,
        to = to,
        days = lanes.loggedDays.size,
        sleep = sleepShares,
        feeds = instantShares(feeds, typicalFeedDays),
        diapers = instantShares(diapers, typicalDiaperDays),
        day = intervals(night = false),
        night = intervals(night = true),
    )
}

/**
 * The days of [from]..[to] that are unlike the others for a kind (Statistics' noise): left out of the typical day, the
 * averages and the bars' values, and drawn apart on the chart. Each set holds only days on which that kind was logged.
 *
 * @property feeds days whose count of feeds is far from the usual count (a half-logged or double-logged day).
 * @property diapers the same for diapers; pee and poop go by it.
 * @property sleep days whose sleep in all is far from the usual day's (a forgotten night, a sleep left running).
 */
data class UnusualDays(val feeds: Set<LocalDate>, val diapers: Set<LocalDate>, val sleep: Set<LocalDate>) {
    companion object {
        val NONE = UnusualDays(emptySet(), emptySet(), emptySet())
    }
}

/**
 * The [UnusualDays] of [from]..[to] (today only up to [now]), by the same rules as [dailyPattern]: a day is unusual for
 * a kind when it is over [PatternRules.SIMILAR_DEVIATIONS] median absolute deviations from the usual day, and never
 * closer than the rules' minimum tolerances. Under [PatternRules.MIN_DAYS] days of a kind, none of them is unusual.
 */
fun unusualDays(entries: List<Entry>, from: LocalDate, to: LocalDate, now: Long, zone: ZoneId): UnusualDays {
    val lanes = Lanes.of(entries, from, to, now, zone)
    return UnusualDays(
        feeds = lanes.feedDays - lanes.typicalFeedDays,
        diapers = lanes.diaperDays - lanes.typicalDiaperDays,
        sleep = lanes.sleepByDay.keys - lanes.typicalSleepDays,
    )
}

/** What [dailyPattern] and [unusualDays] share: the window's entries by kind, and which days look like the others. */
private class Lanes(
    val windowStart: Long,
    val loggedDays: Set<LocalDate>,
    val feeds: List<Entry>,
    val diapers: List<Entry>,
    /** Sleeps that overlap the window, so a night sleep from the evening before still covers the first morning. */
    val sleeps: List<Entry>,
    /** Each day with a sleep starting on it: the share of each half hour spent asleep. */
    val sleepByDay: Map<LocalDate, List<Float>>,
    val zone: ZoneId,
) {
    val feedDays: Set<LocalDate> get() = feeds.map { localDate(it.startMillis, zone) }.toSet()
    val diaperDays: Set<LocalDate> get() = diapers.map { localDate(it.startMillis, zone) }.toSet()
    val typicalSleepDays: Set<LocalDate> = similarSleepDays(sleepByDay)
    val typicalFeedDays: Set<LocalDate> = similarCountDays(feeds, zone)
    val typicalDiaperDays: Set<LocalDate> = similarCountDays(diapers, zone)

    companion object {
        fun of(entries: List<Entry>, from: LocalDate, to: LocalDate, now: Long, zone: ZoneId): Lanes {
            val windowStart = DayBounds.of(from, zone).startMillis
            val windowEnd = minOf(DayBounds.of(to, zone).endMillis, now)
            val visible = entries
                .filter { !it.deleted && it.startMillis <= now && it.startMillis < windowEnd }
                .sortedWith(compareBy<Entry> { it.startMillis }.thenBy { it.id })
            val inWindow = visible.filter { it.startMillis >= windowStart }
            val sleeps = visible.filter { it.kind == EntryKind.SLEEP && it.effectiveEndIn(now) > windowStart }
            val sleepDays = inWindow.filter { it.kind == EntryKind.SLEEP }.map { localDate(it.startMillis, zone) }.toSortedSet()
            val sleepByDay = sleepDays.associateWith { date ->
                // Only the sleeps that touch the day: a range may be months long.
                val bounds = DayBounds.of(date, zone)
                val onDay = sleeps.filter { it.startMillis < bounds.endMillis && it.effectiveEndIn(now) > bounds.startMillis }
                List(PatternRules.SLOTS) { slot ->
                    val (start, end) = slotBounds(date, slot, zone)
                    val asleep = onDay.sumOf { sleep -> (minOf(sleep.effectiveEndIn(now), end) - maxOf(sleep.startMillis, start)).coerceAtLeast(0L) }
                    (asleep.toFloat() / (end - start)).coerceAtMost(1f)
                }
            }
            return Lanes(
                windowStart = windowStart,
                loggedDays = inWindow.map { localDate(it.startMillis, zone) }.toSet(),
                feeds = merged(inWindow.filter { it.kind == EntryKind.FEED }),
                diapers = merged(inWindow.filter { it.kind == EntryKind.DIAPER }),
                sleeps = sleeps,
                sleepByDay = sleepByDay,
                zone = zone,
            )
        }
    }
}

/** Drops an instant under [ForecastRules.MIN_GAP_MILLIS] after the one kept before it (one event logged twice). */
private fun merged(instants: List<Entry>): List<Entry> = instants.fold(mutableListOf()) { kept, entry ->
    if (kept.isEmpty() || entry.startMillis - kept.last().startMillis >= ForecastRules.MIN_GAP_MILLIS) kept += entry
    kept
}

/** (start, length) from each instant to the next, without forgotten logs. */
private fun gaps(instants: List<Entry>): List<Pair<Long, Long>> =
    instants.zipWithNext { a, b -> a.startMillis to b.startMillis - a.startMillis }
        .filter { (_, length) -> length <= ForecastRules.MAX_GAP_MILLIS }

/** The days whose sleep in all (the half hours asleep added up) looks like the other days'. */
private fun similarSleepDays(byDay: Map<LocalDate, List<Float>>): Set<LocalDate> {
    if (byDay.size < PatternRules.MIN_DAYS) return byDay.keys
    val asleep = byDay.mapValues { (_, shares) -> shares.sum().toDouble() }
    val band = similarBand(asleep.values.toList(), PatternRules.MIN_SLEEP_TOLERANCE_SHARE * median(asleep.values.toList()))
    return asleep.filterValues { it in band }.keys
}

/** The days with [instants] whose count of them looks like the other days' (not half or double logged). */
private fun similarCountDays(instants: List<Entry>, zone: ZoneId): Set<LocalDate> {
    val counts = instants.groupingBy { localDate(it.startMillis, zone) }.eachCount()
    if (counts.size < PatternRules.MIN_DAYS) return counts.keys
    val values = counts.values.map { it.toDouble() }
    val band = similarBand(values, maxOf(1.0, PatternRules.MIN_COUNT_TOLERANCE_SHARE * median(values)))
    return counts.filterValues { it.toDouble() in band }.keys
}

/**
 * The values that look like the others: within [PatternRules.SIMILAR_DEVIATIONS] scaled median absolute deviations
 * of the median, and never closer than [minTolerance]. At least half the values always fall inside.
 */
private fun similarBand(values: List<Double>, minTolerance: Double): ClosedFloatingPointRange<Double> {
    val middle = median(values)
    val deviation = median(values.map { abs(it - middle) })
    val tolerance = maxOf(PatternRules.SIMILAR_DEVIATIONS * MAD_TO_SD * deviation, minTolerance)
    return (middle - tolerance)..(middle + tolerance)
}

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/** A median absolute deviation times this is a standard deviation, for normally spread values. */
private const val MAD_TO_SD = 1.4826

private fun interval(lengths: List<Long>): PatternInterval? {
    if (lengths.size < PatternRules.MIN_SAMPLES) return null
    val values = lengths.map { it.toDouble() }
    val minTolerance = maxOf(PatternRules.MIN_STRETCH_TOLERANCE_SHARE * median(values), PatternRules.MIN_STRETCH_TOLERANCE_MILLIS.toDouble())
    val band = similarBand(values, minTolerance)
    val sorted = lengths.filter { it.toDouble() in band }.sorted()
    if (sorted.size < PatternRules.MIN_SAMPLES) return null
    return PatternInterval(
        medianMillis = quantile(sorted, 0.5).toLong(),
        lowMillis = quantile(sorted, 0.25).toLong(),
        highMillis = quantile(sorted, 0.75).toLong(),
        samples = sorted.size,
    )
}

private fun Entry.effectiveEndIn(now: Long): Long = minOf(endMillis ?: now, now)

private fun slotOf(millis: Long, zone: ZoneId): Int = localTime(millis, zone).let { (it.hour * 60 + it.minute) / PatternRules.SLOT_MINUTES }

/** The slot's epoch-ms range on [date]; a DST change makes one slot shorter or longer, never empty. */
private fun slotBounds(date: LocalDate, slot: Int, zone: ZoneId): Pair<Long, Long> {
    val minutes = slot * PatternRules.SLOT_MINUTES
    val start = epochMillis(date, minutes / 60, minutes % 60, zone)
    val next = minutes + PatternRules.SLOT_MINUTES
    val end = if (next >= 24 * 60) DayBounds.of(date, zone).endMillis else epochMillis(date, next / 60, next % 60, zone)
    return start to maxOf(end, start + 1)
}

/** True when [time] lies in [start, end), wrapping midnight when [start] is after [end]. */
private fun inNight(time: LocalTime, start: LocalTime, end: LocalTime): Boolean = when {
    start == end -> false
    start < end -> time >= start && time < end
    else -> time >= start || time < end
}
