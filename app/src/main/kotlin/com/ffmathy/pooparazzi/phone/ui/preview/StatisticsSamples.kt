package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsStateBuilder
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsUiState
import java.time.LocalDate

/**
 * Statistics states (V1_ADDITIONS §8) from the sample history, built through [StatisticsStateBuilder] at Mon 26 Oct
 * 2026 14:32 unless noted: every window ends Sun 25 Oct (the 25-hour day), and `history()` was first logged on Tue
 * 22 Sep, so its first full day is Wed 23 Sep. Today's entries are never counted.
 */
object StatisticsSamples {
    private val zone = SampleData.ZONE

    /**
     * The default: Poop open over the last 30 days (26 Sep – 25 Oct), averaging 7.1 feeds · 7.6 pee · 1.8 poop ·
     * 14h 07m sleep. Dashed line 1.8, scale 5; highest Wed 7 Oct (5), lowest Fri 23 Oct (0).
     */
    fun lastThirtyDays(): StatisticsUiState = build(SampleData.history(zone = zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    /**
     * Poop open over the last 7 days (19 – 25 Oct): 6.7 feeds · 7.9 pee · 0.7 poop · 14h 01m sleep. Dashed line 0.7,
     * scale 2; highest Sat 24 Oct (2), lowest Fri 23 Oct (0).
     */
    fun lastSevenDays(): StatisticsUiState = build(SampleData.history(zone = zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last7Days)

    /** The same at 03:12: the numbers do not move during the day. */
    fun night(): StatisticsUiState = build(SampleData.history(zone = zone), SampleData.NOW_0312, StatsMetric.POOP, StatsRange.Last30Days)

    /** Two babies: the same log as Noa's, labelled "Noa" under the title (BABIES_AND_BACKUP §2.4). */
    fun twins(): StatisticsUiState =
        lastThirtyDays().copy(babies = SampleData.twinSettings.babies, babyId = SampleData.twinSettings.selectedBaby.id)

    /**
     * Sleep open: the line at 14h 07m, scale "16h 00m"; highest Fri 2 Oct (15h 27m), lowest Sat 26 Sep
     * (11h 24m).
     */
    fun sleep(): StatisticsUiState = build(SampleData.history(zone = zone), SampleData.NOW_1432, StatsMetric.SLEEP, StatsRange.Last30Days)

    /**
     * Tue 13 and Wed 14 Oct forgotten: "28 of 30 days logged", the last 30 days 7.1 · 7.7 · 1.8 · 14h 06m, two gaps
     * in the chart.
     */
    fun gaps(): StatisticsUiState = build(SampleData.historyWithGaps(zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    /**
     * Feeds open over chosen dates, 5–18 Oct: 7.1 · 7.5 · 1.7 · 14h 20m. 14 bars; highest Thu 15 Oct (8), lowest Wed 14 Oct (6).
     */
    fun custom(): StatisticsUiState =
        build(SampleData.history(zone = zone), SampleData.NOW_1432, StatsMetric.FEEDS, StatsRange.Custom(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18)))

    /**
     * Three full days of logging (the first full day is Fri 23 Oct): "3 of 30 days logged", 6.7 · 7.3 · 1.0 ·
     * 13h 28m; the 27 days before logging are empty.
     */
    fun firstDays(): StatisticsUiState =
        build(SampleData.history(days = 3, zone = zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    /**
     * Half a year of chosen dates (29 Apr – 25 Oct, 180 days): 26 bars of 7 days, 7.2 · 7.4 · 2.2 · 14h 07m. Highest 11–17 May (3.3), lowest 17–23 Aug (1.1), scale 4.
     */
    fun halfYear(): StatisticsUiState = build(
        SampleData.history(days = 200, zone = zone),
        SampleData.NOW_1432,
        StatsMetric.POOP,
        StatsRange.Custom(LocalDate.of(2026, 4, 29), LocalDate.of(2026, 10, 25)),
    )

    /**
     * No whole day logged yet (the scenario starts Sun 22:40): Sun and today so far count, 2 of 30 days logged, 5.0 feeds · 4.0 pee · 2.0 poop · 4h 39m sleep, and the "today so far" footnote.
     */
    fun soFar(): StatisticsUiState = build(SampleData.scenario(zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    /** Nothing logged: the empty state. */
    fun empty(): StatisticsUiState = build(emptyList(), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    private fun build(entries: List<Entry>, now: Long, metric: StatsMetric, range: StatsRange): StatisticsUiState =
        StatisticsStateBuilder.build(entries, now, zone, metric, range)
}
