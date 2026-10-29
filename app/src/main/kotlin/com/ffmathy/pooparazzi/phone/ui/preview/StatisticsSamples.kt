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
     * The default: Poop open over the last 30 days (26 Sep – 25 Oct). The last 7 days average 6.7 feeds · 7.9 pee ·
     * 0.7 poop · 14h 01m sleep, the last 30 days 7.1 · 7.6 · 1.8 · 14h 07m. Band on 19–25 Oct, 7-day line 0.7, dashed
     * line 1.8, scale 5; highest Wed 7 Oct (5), lowest Fri 23 Oct (0).
     */
    fun lastThirtyDays(): StatisticsUiState = build(SampleData.history(zone = zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    /** The same at 03:12: the numbers do not move during the day. */
    fun night(): StatisticsUiState = build(SampleData.history(zone = zone), SampleData.NOW_0312, StatsMetric.POOP, StatsRange.Last30Days)

    /** Two babies: the same log as Noa's, labelled "Noa" under the title (BABIES_AND_BACKUP §2.4). */
    fun twins(): StatisticsUiState =
        lastThirtyDays().copy(babies = SampleData.twinSettings.babies, babyId = SampleData.twinSettings.selectedBaby.id)

    /**
     * Sleep open: lines at 14h 01m and 14h 07m, scale "16h 00m"; highest Fri 2 Oct (15h 27m), lowest Sat 26 Sep
     * (11h 24m).
     */
    fun sleep(): StatisticsUiState = build(SampleData.history(zone = zone), SampleData.NOW_1432, StatsMetric.SLEEP, StatsRange.Last30Days)

    /**
     * Tue 13 and Wed 14 Oct forgotten: "28 of 30 days logged", the last 30 days 7.1 · 7.7 · 1.8 · 14h 06m, two gaps
     * in the chart; the last 7 days are unchanged.
     */
    fun gaps(): StatisticsUiState = build(SampleData.historyWithGaps(zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    /**
     * Feeds open over chosen dates, 5–18 Oct: 7.1 · 7.5 · 1.7 · 14h 20m, with "Last 30 days" lines 7.1 · 7.6 · 1.8 ·
     * 14h 07m. 14 bars, no band; highest Thu 15 Oct (8), lowest Wed 14 Oct (6).
     */
    fun custom(): StatisticsUiState =
        build(SampleData.history(zone = zone), SampleData.NOW_1432, StatsMetric.FEEDS, StatsRange.Custom(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18)))

    /**
     * Three full days of logging (the first full day is Fri 23 Oct): "3 of 7" and "3 of 30 days logged", both
     * 6.7 · 7.3 · 1.0 · 13h 28m; the 27 days before logging are empty.
     */
    fun firstDays(): StatisticsUiState =
        build(SampleData.history(days = 3, zone = zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    /**
     * Half a year of chosen dates (29 Apr – 25 Oct, 180 days): 26 bars of 7 days, the newest (19–25 Oct) on the band.
     * Chosen 7.2 · 7.4 · 2.2 · 14h 07m; last 7 days 6.6 · 7.1 · 1.9 · 14h 13m; last 30 days 6.8 · 7.1 · 2.0 · 14h 29m.
     * Highest 11–17 May (3.3), lowest 17–23 Aug (1.1), scale 4.
     */
    fun halfYear(): StatisticsUiState = build(
        SampleData.history(days = 200, zone = zone),
        SampleData.NOW_1432,
        StatsMetric.POOP,
        StatsRange.Custom(LocalDate.of(2026, 4, 29), LocalDate.of(2026, 10, 25)),
    )

    /** Only today logged (the scenario starts Sun 22:40, so its first full day would be today): the empty state. */
    fun empty(): StatisticsUiState = build(SampleData.scenario(zone), SampleData.NOW_1432, StatsMetric.POOP, StatsRange.Last30Days)

    private fun build(entries: List<Entry>, now: Long, metric: StatsMetric, range: StatsRange): StatisticsUiState =
        StatisticsStateBuilder.build(entries, now, zone, metric, range)
}
