package com.ffmathy.pooparazzi.phone.ui.statistics

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ffmathy.pooparazzi.core.domain.StatsAverage
import com.ffmathy.pooparazzi.core.domain.StatsBar
import com.ffmathy.pooparazzi.core.domain.StatsChart
import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.domain.StatsReport
import com.ffmathy.pooparazzi.core.domain.StatsWindow
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.formatTenths
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.history.MINUTE_MILLIS
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.ffmathy.pooparazzi.core.R as CoreR

// Statistics' text (V1_ADDITIONS §8). Visible dates are abbreviated ("26 Sep"); TalkBack always hears full month names
// ("26 September", "Wednesday 7 October"), names before values, and durations in words.

/** "Last 7 days", "Last 30 days", "Last 90 days", "All days", or "Chosen dates" for dates picked in the dialog. */
@Composable
@ReadOnlyComposable
internal fun rangeLabel(range: StatsRange): String = stringResource(
    when (range) {
        StatsRange.Last7Days -> R.string.statistics_last_7_days
        StatsRange.Last30Days -> R.string.statistics_last_30_days
        StatsRange.Last90Days -> R.string.statistics_last_90_days
        StatsRange.AllDays -> R.string.statistics_all_days
        is StatsRange.Custom -> R.string.statistics_chosen_dates
    },
)

/** "Feeds", "Pee", "Poop", "Sleep": the row labels, in History's order. */
@Composable
@ReadOnlyComposable
internal fun metricName(metric: StatsMetric): String = stringResource(
    when (metric) {
        StatsMetric.FEEDS -> R.string.statistics_feeds
        StatsMetric.PEE -> CoreR.string.diaper_pee
        StatsMetric.POOP -> CoreR.string.diaper_poop
        StatsMetric.SLEEP -> CoreR.string.kind_sleep
    },
)

/** Dates of the window [from]..[to] show their year when it spans two years or is not in [today]'s year. */
internal fun showsYear(from: LocalDate, to: LocalDate, today: LocalDate): Boolean = from.year != to.year || to.year != today.year

@Composable
@ReadOnlyComposable
private fun formatDate(date: LocalDate, @StringRes pattern: Int, formatters: Formatters): String =
    DateTimeFormatter.ofPattern(stringResource(pattern), formatters.clock.locale).format(date)

/** "26 Sep" / "26. sep." (with the year when [year]). */
@Composable
@ReadOnlyComposable
internal fun visibleDate(date: LocalDate, year: Boolean, formatters: Formatters): String =
    formatDate(date, if (year) R.string.statistics_pattern_date_year else R.string.statistics_pattern_date, formatters)

/** "26 September" / "26. september" (with the year when [year]). */
@Composable
@ReadOnlyComposable
internal fun spokenDate(date: LocalDate, year: Boolean, formatters: Formatters): String =
    formatDate(date, if (year) R.string.statistics_pattern_spoken_date_year else R.string.statistics_pattern_spoken_date, formatters)

/** "Wednesday 7 October" / "onsdag den 7. oktober" (with the year when [year]). */
@Composable
@ReadOnlyComposable
internal fun spokenDay(date: LocalDate, year: Boolean, formatters: Formatters): String =
    formatDate(date, if (year) R.string.statistics_pattern_spoken_day_year else R.string.statistics_pattern_spoken_day, formatters)

/** "26 Sep – 25 Oct", or one date when the window is one day. */
@Composable
@ReadOnlyComposable
internal fun visibleRange(from: LocalDate, to: LocalDate, today: LocalDate, formatters: Formatters): String {
    val year = showsYear(from, to, today)
    return if (from == to) {
        visibleDate(from, year, formatters)
    } else {
        stringResource(R.string.join_dash, visibleDate(from, year, formatters), visibleDate(to, year, formatters))
    }
}

/** "26 September to 25 October" / "26. september til 25. oktober", or one date when the range is one day. */
@Composable
@ReadOnlyComposable
internal fun spokenRange(from: LocalDate, to: LocalDate, year: Boolean, formatters: Formatters): String =
    if (from == to) {
        spokenDate(from, year, formatters)
    } else {
        stringResource(R.string.spoken_range, spokenDate(from, year, formatters), spokenDate(to, year, formatters))
    }

/** "3 of 7 days logged", or null when something was logged on every day of [window]. */
@Composable
@ReadOnlyComposable
internal fun coverageText(window: StatsWindow): String? =
    if (window.loggedDays < window.dayCount) {
        pluralStringResource(R.plurals.statistics_days_logged, window.dayCount, window.loggedDays, window.dayCount)
    } else {
        null
    }

/**
 * A window's name, dates and coverage for TalkBack: "Last 7 days, 19 October to 25 October", or
 * "Last 30 days, 26 September to 25 October, 3 of 30 days logged".
 */
@Composable
@ReadOnlyComposable
internal fun windowSpoken(label: String, window: StatsWindow, today: LocalDate, formatters: Formatters): String {
    val range = spokenRange(window.from, window.to, showsYear(window.from, window.to, today), formatters)
    val coverage = coverageText(window)
    return if (coverage == null) {
        stringResource(R.string.spoken_pair, label, range)
    } else {
        stringResource(R.string.spoken_running, label, range, coverage)
    }
}

/** An average as shown: "6.7" / "6,7" for counts, "14h 01m" / "14t 01m" for sleep, "–" when nothing counted. */
@Composable
@ReadOnlyComposable
internal fun averageText(average: StatsAverage?, metric: StatsMetric, formatters: Formatters): String = when {
    average == null -> stringResource(R.string.statistics_no_value)
    metric == StatsMetric.SLEEP -> formatters.duration.format(average.minutes * MINUTE_MILLIS)
    else -> formatTenths(average.tenths, formatters.clock.locale)
}

/** An average for TalkBack: "6.7 a day", "14 hours 1 minute a day", or "not logged" (never the dash). */
@Composable
@ReadOnlyComposable
internal fun averageSpoken(average: StatsAverage?, metric: StatsMetric, formatters: Formatters): String = when {
    average == null -> stringResource(R.string.statistics_spoken_not_logged)
    metric == StatsMetric.SLEEP -> stringResource(R.string.statistics_spoken_per_day, formatters.duration.spoken(average.minutes * MINUTE_MILLIS))
    else -> stringResource(R.string.statistics_spoken_per_day, formatTenths(average.tenths, formatters.clock.locale))
}

/** A row for TalkBack: "Poop. Last 30 days: 1.8 a day.", or "Feeds. Chosen dates: 7.1 a day.". */
@Composable
@ReadOnlyComposable
internal fun rowSpoken(metric: StatsMetric, report: StatsReport, formatters: Formatters): String = stringResource(
    R.string.statistics_spoken_row,
    metricName(metric),
    rangeLabel(report.range),
    averageSpoken(report.window.average(metric), metric, formatters),
)

/** The label on the chart's top line: "5", or "16h 00m" for sleep. */
internal fun scaleLabel(chart: StatsChart, formatters: Formatters): String =
    if (chart.metric == StatsMetric.SLEEP) {
        formatters.duration.format(chart.top.toLong() * MINUTE_MILLIS)
    } else {
        chart.top.toLong().toString()
    }

/** "Days with no diapers logged are left out." under a chart with gaps. */
@Composable
@ReadOnlyComposable
internal fun gapsNote(metric: StatsMetric): String = stringResource(
    when (metric) {
        StatsMetric.FEEDS -> R.string.statistics_gaps_feeds
        StatsMetric.PEE, StatsMetric.POOP -> R.string.statistics_gaps_diapers
        StatsMetric.SLEEP -> R.string.statistics_gaps_sleep
    },
)

/**
 * The chart's text alternative: "Poop per day, 26 September to 25 October. Highest 5 on Wednesday 7 October. Lowest 0
 * on Friday 23 October.", for bars of several days "Poop per day, 29 April to 25 October, each bar 7 days. Highest 3.3
 * from 11 May to 17 May. Lowest 1.1 from 17 August to 23 August.", then "2 days with no diapers logged are left out."
 * and "Days before 23 October are left out." when they apply. The oldest of several-day bars can be a single day; it
 * is then said like a day ("Lowest 1.0 on Sunday 23 August.").
 */
@Composable
@ReadOnlyComposable
internal fun chartSpoken(chart: StatsChart, report: StatsReport, today: LocalDate, formatters: Formatters): String {
    val highest = chart.highest ?: return stringResource(R.string.statistics_chart_empty)
    val lowest = chart.lowest ?: highest
    val year = showsYear(chart.from, chart.to, today)
    val name = metricName(chart.metric)
    val range = spokenRange(chart.from, chart.to, year, formatters)
    val sentences = mutableListOf<String>()
    sentences += if (chart.binDays == 1) {
        stringResource(R.string.statistics_spoken_chart_days, name, range)
    } else {
        stringResource(R.string.statistics_spoken_chart_bars, name, range, pluralStringResource(CoreR.plurals.spoken_days, chart.binDays, chart.binDays))
    }
    sentences += extremeSpoken(R.string.statistics_spoken_highest_on, R.string.statistics_spoken_highest_from, highest, chart, year, formatters)
    sentences += extremeSpoken(R.string.statistics_spoken_lowest_on, R.string.statistics_spoken_lowest_from, lowest, chart, year, formatters)
    if (chart.gapDays > 0) {
        val gaps = when (chart.metric) {
            StatsMetric.FEEDS -> R.plurals.statistics_spoken_gaps_feeds
            StatsMetric.PEE, StatsMetric.POOP -> R.plurals.statistics_spoken_gaps_diapers
            StatsMetric.SLEEP -> R.plurals.statistics_spoken_gaps_sleep
        }
        sentences += pluralStringResource(gaps, chart.gapDays, chart.gapDays)
    }
    if (chart.daysBeforeLogging > 0) {
        sentences += stringResource(R.string.statistics_spoken_before, spokenDate(report.firstDay, year, formatters))
    }
    return sentences.reduce { text, sentence -> stringResource(R.string.join_space, text, sentence) }
}

/** "Highest 5 on Wednesday 7 October." for a one-day [bar], "Highest 3.3 from 11 May to 17 May." for a longer one. */
@Composable
@ReadOnlyComposable
private fun extremeSpoken(
    @StringRes onDay: Int,
    @StringRes overDays: Int,
    bar: StatsBar,
    chart: StatsChart,
    year: Boolean,
    formatters: Formatters,
): String {
    val value = spokenBarValue(bar, chart, formatters)
    return if (bar.from == bar.to) {
        stringResource(onDay, value, spokenDay(bar.from, year, formatters))
    } else {
        stringResource(overDays, value, spokenRange(bar.from, bar.to, year, formatters))
    }
}

/**
 * A bar's value for TalkBack: a day's whole count ("5") or a bar's average per day ("3.3"); sleep in words
 * ("15 hours 27 minutes").
 */
@Composable
@ReadOnlyComposable
private fun spokenBarValue(bar: StatsBar, chart: StatsChart, formatters: Formatters): String {
    val average = bar.average ?: return stringResource(R.string.statistics_spoken_not_logged)
    return when {
        chart.metric == StatsMetric.SLEEP ->
            formatters.duration.spoken((if (chart.binDays == 1) bar.total else average.minutes) * MINUTE_MILLIS)
        chart.binDays == 1 -> bar.total.toString()
        else -> formatTenths(average.tenths, formatters.clock.locale)
    }
}
