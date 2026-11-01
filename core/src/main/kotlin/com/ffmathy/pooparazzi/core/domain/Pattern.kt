package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A typical stretch: half of the past stretches lasted [lowMillis]..[highMillis] (the interquartile range) and
 * [medianMillis] is the middle one. [samples] is how many stretches it rests on.
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
 * The typical day over the logged days [from]..[to] (History's "Pattern" card): when in the day the baby usually
 * sleeps, feeds and gets a diaper, and the usual time between them.
 *
 * Each of [sleep], [feeds] and [diapers] has one share 0..1 per [PatternRules.SLOT_MINUTES] slot of the local day
 * (slot 0 starts at 00:00): for sleep, the share of the slot spent asleep on an average day; for feeds and diapers,
 * the share of days with one starting in the slot. Only days on which that kind was logged count (like Statistics),
 * so a forgotten day does not water the pattern down; a lane is empty (all 0) when no day counts.
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
    /** How many days before the shown day the pattern looks at: a week follows a growing baby. */
    const val DAYS = 7L

    /** Fewer logged days than this and there is no pattern yet. */
    const val MIN_DAYS = 3

    /** An interval needs at least this many stretches. */
    const val MIN_SAMPLES = 3

    const val SLOT_MINUTES = 30
    const val SLOTS = 24 * 60 / SLOT_MINUTES
}

/**
 * The [DailyPattern] of the [PatternRules.DAYS] days before [day] (so today's pattern does not count today, which has
 * not finished), or null when fewer than [PatternRules.MIN_DAYS] of them have anything logged. A stretch is by night
 * when it starts in [nightStart]..[nightEnd] (the night window, 19:00–07:00 by default), otherwise by day. Feeds or
 * diapers under [ForecastRules.MIN_GAP_MILLIS] apart are one (a feed per side), and gaps over
 * [ForecastRules.MAX_GAP_MILLIS] are a forgotten log, not a stretch. Entries after [now] are ignored.
 */
fun dailyPattern(
    entries: List<Entry>,
    day: LocalDate,
    now: Long,
    zone: ZoneId,
    nightStart: LocalTime = LocalTime.of(19, 0),
    nightEnd: LocalTime = LocalTime.of(7, 0),
): DailyPattern? {
    val from = day.minusDays(PatternRules.DAYS)
    val to = day.minusDays(1)
    val windowStart = DayBounds.of(from, zone).startMillis
    val windowEnd = minOf(DayBounds.of(to, zone).endMillis, now)
    val visible = entries
        .filter { !it.deleted && it.startMillis <= now && it.startMillis < windowEnd }
        .sortedWith(compareBy<Entry> { it.startMillis }.thenBy { it.id })
    val inWindow = visible.filter { it.startMillis >= windowStart }
    val loggedDays = inWindow.map { localDate(it.startMillis, zone) }.toSet()
    if (loggedDays.size < PatternRules.MIN_DAYS) return null

    val feeds = merged(inWindow.filter { it.kind == EntryKind.FEED })
    val diapers = merged(inWindow.filter { it.kind == EntryKind.DIAPER })
    // Sleeps that overlap the window, so a night sleep from the evening before still covers the first morning.
    val sleeps = visible.filter { it.kind == EntryKind.SLEEP && it.effectiveEndIn(now) > windowStart }

    val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
    val sleepDays = dates.filter { date -> inWindow.any { it.kind == EntryKind.SLEEP && localDate(it.startMillis, zone) == date } }
    val sleepShares = FloatArray(PatternRules.SLOTS)
    for (date in sleepDays) {
        for (slot in 0 until PatternRules.SLOTS) {
            val (start, end) = slotBounds(date, slot, zone)
            val asleep = sleeps.sumOf { sleep -> (minOf(sleep.effectiveEndIn(now), end) - maxOf(sleep.startMillis, start)).coerceAtLeast(0L) }
            sleepShares[slot] += (asleep.toFloat() / (end - start)).coerceAtMost(1f)
        }
    }

    fun instantShares(instants: List<Entry>): List<Float> {
        val counted = instants.map { localDate(it.startMillis, zone) }.toSet()
        if (counted.isEmpty()) return List(PatternRules.SLOTS) { 0f }
        val hits = Array(PatternRules.SLOTS) { mutableSetOf<LocalDate>() }
        for (entry in instants) hits[slotOf(entry.startMillis, zone)] += localDate(entry.startMillis, zone)
        return hits.map { it.size.toFloat() / counted.size }
    }

    val completedSleeps = sleeps.filter { it.endMillis != null && it.endMillis <= now && it.startMillis >= windowStart }
    val feedGaps = gaps(feeds)
    val diaperGaps = gaps(diapers)
    val sleepLengths = completedSleeps.map { it.startMillis to it.endMillis!! - it.startMillis }
    val wakeWindows = completedSleeps.zipWithNext { a, b -> a.endMillis!! to b.startMillis - a.endMillis }
        .filter { (_, length) -> length in ForecastRules.MIN_GAP_MILLIS..ForecastRules.MAX_GAP_MILLIS }

    fun intervals(night: Boolean): PatternIntervals {
        fun pick(stretches: List<Pair<Long, Long>>) =
            interval(stretches.filter { (start, _) -> inNight(localTime(start, zone), nightStart, nightEnd) == night }.map { it.second })
        return PatternIntervals(pick(feedGaps), pick(diaperGaps), pick(sleepLengths), pick(wakeWindows))
    }

    return DailyPattern(
        from = from,
        to = to,
        days = loggedDays.size,
        sleep = sleepShares.map { if (sleepDays.isEmpty()) 0f else it / sleepDays.size },
        feeds = instantShares(feeds),
        diapers = instantShares(diapers),
        day = intervals(night = false),
        night = intervals(night = true),
    )
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

private fun interval(lengths: List<Long>): PatternInterval? {
    if (lengths.size < PatternRules.MIN_SAMPLES) return null
    val sorted = lengths.sorted()
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
