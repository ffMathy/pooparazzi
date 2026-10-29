package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** The four averages of Statistics, in History's order (V1_ADDITIONS §4, §8: feeds · pee · poop · sleep). */
enum class StatsMetric { FEEDS, PEE, POOP, SLEEP }

/**
 * The range Statistics compares with the last 7 days (V1_ADDITIONS §8). Presets are relative to today and end
 * yesterday; [Custom] is absolute and is cut to the countable days on every build ([clamped]).
 */
sealed interface StatsRange {
    data object Last30Days : StatsRange
    data object Last90Days : StatsRange
    data object AllDays : StatsRange

    data class Custom(val from: LocalDate, val to: LocalDate) : StatsRange {
        init {
            require(from <= to) { "from ($from) must not be after to ($to)" }
        }
    }
}

/** Why a day does or does not count (V1_ADDITIONS §8). */
enum class DayStatus {
    /** Something starts on the day: a logged day ("n of N days logged"). */
    COUNTED,

    /** Nothing at all starts on the day (forgotten, or the app was not used): left out of every average. */
    NOTHING_LOGGED,

    /** Before the first full day of logging (the first logged day is probably partial). */
    BEFORE_LOGGING,

    /** Today or later. Windows end yesterday, so this does not occur in a [StatsReport]. */
    NOT_OVER,
}

/**
 * One local day of a window: its [counts] (exactly [dailyCounts], so what History shows for the day) and how many
 * diapers and sleeps start on it.
 */
data class StatsDay(
    val date: LocalDate,
    val status: DayStatus,
    val counts: DayCounts,
    val diaperStarts: Int,
    val sleepStarts: Int,
) {
    /**
     * Whether the day counts for [metric]: something was logged on it and an entry of the metric's kind starts on it
     * (a feed for feeds, any diaper, plain changes included, for pee and poop, a sleep for sleep). A day without one
     * was probably forgotten for that kind, so it is left out rather than averaged in as 0.
     */
    fun counted(metric: StatsMetric): Boolean = status == DayStatus.COUNTED && when (metric) {
        StatsMetric.FEEDS -> counts.feeds > 0
        StatsMetric.PEE, StatsMetric.POOP -> diaperStarts > 0
        StatsMetric.SLEEP -> sleepStarts > 0
    }

    /** Feeds, pee or poop on the day, or its sleep in clock minutes. */
    fun value(metric: StatsMetric): Long = when (metric) {
        StatsMetric.FEEDS -> counts.feeds.toLong()
        StatsMetric.PEE -> counts.pee.toLong()
        StatsMetric.POOP -> counts.poop.toLong()
        StatsMetric.SLEEP -> counts.sleepMinutes
    }
}

/** [total] over [days] counted days. */
data class StatsAverage(val total: Long, val days: Int) {
    init {
        require(days >= 1) { "an average needs at least one day" }
    }

    /** A count per day in tenths, rounded half up: 67 is 6.7. */
    val tenths: Int get() = roundedTenths(total, days)

    /** Sleep per day in whole minutes, rounded half up. */
    val minutes: Long get() = roundedMinutes(total, days)

    /** The rounded value that is shown and drawn: minutes for [StatsMetric.SLEEP], otherwise tenths / 10. */
    fun shown(metric: StatsMetric): Double = if (metric == StatsMetric.SLEEP) minutes.toDouble() else tenths / 10.0
}

/** The days [from]..[to] (both included, oldest first). */
data class StatsWindow(val from: LocalDate, val to: LocalDate, val days: List<StatsDay>) {
    val dayCount: Int get() = days.size

    /** Days on which something was logged ("n of N days logged"). */
    val loggedDays: Int = days.count { it.status == DayStatus.COUNTED }

    fun countedDays(metric: StatsMetric): Int = days.count { it.counted(metric) }

    /** The average per counted day, or null when no day counts for [metric]. */
    fun average(metric: StatsMetric): StatsAverage? {
        var total = 0L
        var counted = 0
        for (day in days) {
            if (day.counted(metric)) {
                total += day.value(metric)
                counted++
            }
        }
        return if (counted == 0) null else StatsAverage(total, counted)
    }
}

/**
 * Everything Statistics shows (V1_ADDITIONS §8). [firstDay] is the day after the first logged day, [lastDay] is
 * yesterday; [range] is the effective range (a Custom one already clamped) and [comparison] is its window.
 */
data class StatsReport(
    val today: LocalDate,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val range: StatsRange,
    val lastSevenDays: StatsWindow,
    val lastThirtyDays: StatsWindow,
    val comparison: StatsWindow,
)

/** The local date of the earliest entry that is not deleted and starts at or before [now], or null. */
fun firstLoggedDay(entries: List<Entry>, now: Long, zone: ZoneId): LocalDate? =
    entries.asSequence()
        .filter { !it.deleted && it.startMillis <= now }
        .minOfOrNull { it.startMillis }
        ?.let { localDate(it, zone) }

/**
 * The statistics of [entries] at [now] in [zone], comparing the last 7 days with [range]; null while nothing can be
 * counted yet (nothing logged, or no whole day since the first logged day). Only whole days up to yesterday count, so
 * the numbers change at midnight or when an entry changes, never as today goes on. One [dailyCounts] call plus one
 * pass over [entries]; their order does not matter.
 */
fun statsReport(entries: List<Entry>, now: Long, zone: ZoneId, range: StatsRange): StatsReport? {
    val firstLogged = firstLoggedDay(entries, now, zone) ?: return null
    val today = localDate(now, zone)
    val lastDay = today.minusDays(1)
    val firstDay = firstLogged.plusDays(1)
    if (firstDay > lastDay) return null
    val effective = range.clamped(firstDay, lastDay)
    val (compareFrom, compareTo) = effective.window(firstDay, lastDay)
    val monthFrom = lastDay.minusDays(MONTH_DAYS - 1L)
    val from = minOf(compareFrom, monthFrom)
    val days = statsDays(entries, from, lastDay, firstDay, now, zone)
    fun window(start: LocalDate, end: LocalDate): StatsWindow {
        val first = ChronoUnit.DAYS.between(from, start).toInt()
        val last = ChronoUnit.DAYS.between(from, end).toInt()
        return StatsWindow(start, end, days.subList(first, last + 1))
    }
    return StatsReport(
        today = today,
        firstDay = firstDay,
        lastDay = lastDay,
        range = effective,
        lastSevenDays = window(lastDay.minusDays(WEEK_DAYS - 1L), lastDay),
        lastThirtyDays = window(monthFrom, lastDay),
        comparison = window(compareFrom, compareTo),
    )
}

/** Every day from [from] to [to] with its counts, starts and status. */
private fun statsDays(entries: List<Entry>, from: LocalDate, to: LocalDate, firstDay: LocalDate, now: Long, zone: ZoneId): List<StatsDay> {
    val counts = dailyCounts(entries, from, to, now, zone)
    val anyStarts = IntArray(counts.size)
    val diaperStarts = IntArray(counts.size)
    val sleepStarts = IntArray(counts.size)
    for (entry in entries) {
        if (entry.deleted || entry.startMillis > now) continue
        val index = ChronoUnit.DAYS.between(from, localDate(entry.startMillis, zone))
        if (index < 0 || index >= counts.size) continue
        val i = index.toInt()
        anyStarts[i]++
        when (entry.kind) {
            EntryKind.DIAPER -> diaperStarts[i]++
            EntryKind.SLEEP -> sleepStarts[i]++
            EntryKind.FEED -> Unit
        }
    }
    val today = localDate(now, zone)
    return List(counts.size) { i ->
        val date = from.plusDays(i.toLong())
        val status = when {
            date >= today -> DayStatus.NOT_OVER
            date < firstDay -> DayStatus.BEFORE_LOGGING
            anyStarts[i] == 0 -> DayStatus.NOTHING_LOGGED
            else -> DayStatus.COUNTED
        }
        StatsDay(date, status, counts[i], diaperStarts[i], sleepStarts[i])
    }
}

/** A Custom range cut to [firstDay]..[lastDay], or [StatsRange.Last30Days] when it lies entirely outside. Presets are unchanged. */
fun StatsRange.clamped(firstDay: LocalDate, lastDay: LocalDate): StatsRange = when (this) {
    is StatsRange.Custom ->
        if (to < firstDay || from > lastDay) StatsRange.Last30Days else StatsRange.Custom(maxOf(from, firstDay), minOf(to, lastDay))
    else -> this
}

/** [clamped], then a Custom range with the dates of the last 30 days, the last 90 days or all days becomes that preset. */
fun StatsRange.normalised(firstDay: LocalDate, lastDay: LocalDate): StatsRange {
    val range = clamped(firstDay, lastDay)
    if (range !is StatsRange.Custom) return range
    val dates = range.from to range.to
    return listOf(StatsRange.Last30Days, StatsRange.Last90Days, StatsRange.AllDays)
        .firstOrNull { it.window(firstDay, lastDay) == dates }
        ?: range
}

/** The first and last date this range covers: presets by their nominal dates (even before logging began), Custom clamped. */
fun StatsRange.window(firstDay: LocalDate, lastDay: LocalDate): Pair<LocalDate, LocalDate> = when (val range = clamped(firstDay, lastDay)) {
    StatsRange.Last30Days -> lastDay.minusDays(MONTH_DAYS - 1L) to lastDay
    StatsRange.Last90Days -> lastDay.minusDays(QUARTER_DAYS - 1L) to lastDay
    StatsRange.AllDays -> firstDay to lastDay
    is StatsRange.Custom -> range.from to range.to
}

/** The most bars a chart draws; up to this many days it draws one bar per day. */
const val MAX_DAILY_BARS = 62

/** Days per bar for a window of [dayCount] days: 1 up to 62 days, then 7, 14, … so a chart never has more than 62 bars. */
fun binDays(dayCount: Int): Int =
    if (dayCount <= MAX_DAILY_BARS) 1 else WEEK_DAYS * ((dayCount + MAX_DAILY_BARS * WEEK_DAYS - 1) / (MAX_DAILY_BARS * WEEK_DAYS))

/** [total] / [days] in tenths, rounded half up with integers only (5 / 4 = 1.25 is 13, where half-even would give 12). */
fun roundedTenths(total: Long, days: Int): Int = ((total * 20 + days) / (2L * days)).toInt()

/** [total] / [days] rounded half up to a whole number (859.5 minutes is 860). */
fun roundedMinutes(total: Long, days: Int): Long = (total * 2 + days) / (2L * days)

/** One bar of a chart: the days [from]..[to], of which [countedDays] count for its metric. 0 counted days is a gap. */
data class StatsBar(val from: LocalDate, val to: LocalDate, val total: Long, val countedDays: Int) {
    /** The value per counted day (a whole day's value for a one-day bar), or null for a gap. */
    val value: Double? get() = if (countedDays == 0) null else total.toDouble() / countedDays

    val average: StatsAverage? get() = if (countedDays == 0) null else StatsAverage(total, countedDays)
}

/**
 * The bar chart of [metric] over the comparison window (V1_ADDITIONS §8).
 *
 * @property binDays days per bar; bars are cut from the end, so only the oldest can be shorter.
 * @property bandBars the bars of the last 7 days, drawn on a band, when the window ends yesterday and is longer than a week.
 * @property weekLine the rounded 7-day average, drawn across the band (one-day bars only).
 * @property rangeLine the rounded average of the whole window, drawn dashed.
 * @property top the top of the scale: a whole count, or a whole hour of sleep in minutes.
 * @property highest the bar with the highest value (the latest of equal ones), null when nothing counts.
 * @property lowest the bar with the lowest value (the latest of equal ones).
 * @property gapDays days from the first full day of logging on that do not count for [metric].
 * @property daysBeforeLogging days before the first full day of logging.
 */
data class StatsChart(
    val metric: StatsMetric,
    val from: LocalDate,
    val to: LocalDate,
    val binDays: Int,
    val bars: List<StatsBar>,
    val bandBars: IntRange?,
    val weekLine: Double?,
    val rangeLine: Double?,
    val top: Double,
    val highest: StatsBar?,
    val lowest: StatsBar?,
    val gapDays: Int,
    val daysBeforeLogging: Int,
) {
    /** No day of the window counts for [metric]. */
    val isEmpty: Boolean get() = highest == null
}

/** The chart of [metric] over [StatsReport.comparison]. */
fun StatsReport.chart(metric: StatsMetric): StatsChart {
    val days = comparison.days
    val n = days.size
    val bin = binDays(n)
    val count = (n + bin - 1) / bin
    val bars = List(count) { k ->
        val first = maxOf(0, n - (count - k) * bin)
        val last = n - (count - 1 - k) * bin - 1
        var total = 0L
        var counted = 0
        for (i in first..last) {
            if (days[i].counted(metric)) {
                total += days[i].value(metric)
                counted++
            }
        }
        StatsBar(days[first].date, days[last].date, total, counted)
    }
    val banded = comparison.to == lastDay && n > WEEK_DAYS && (bin == 1 || bin == WEEK_DAYS)
    val bandBars = when {
        !banded -> null
        bin == 1 -> (count - WEEK_DAYS) until count
        else -> bars.lastIndex..bars.lastIndex
    }
    val weekLine = if (banded && bin == 1) lastSevenDays.average(metric)?.shown(metric) else null
    val rangeLine = comparison.average(metric)?.shown(metric)
    val peak = (bars.mapNotNull { it.value } + listOfNotNull(weekLine, rangeLine)).maxOrNull() ?: 0.0
    val top = if (metric == StatsMetric.SLEEP) maxOf(60.0, ceil(peak / 60) * 60) else maxOf(1.0, ceil(peak))
    val valued = bars.filter { it.countedDays > 0 }
    return StatsChart(
        metric = metric,
        from = comparison.from,
        to = comparison.to,
        binDays = bin,
        bars = bars,
        bandBars = bandBars,
        weekLine = weekLine,
        rangeLine = rangeLine,
        top = top,
        highest = valued.maxWithOrNull(compareBy<StatsBar> { it.value }.thenBy { it.to }),
        lowest = valued.minWithOrNull(compareBy<StatsBar> { it.value }.thenByDescending { it.to }),
        gapDays = days.count { it.date >= firstDay && !it.counted(metric) },
        daysBeforeLogging = days.count { it.date < firstDay },
    )
}

private const val WEEK_DAYS = 7
private const val MONTH_DAYS = 30
private const val QUARTER_DAYS = 90
