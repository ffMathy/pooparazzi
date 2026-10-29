package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_0312
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_1432
import com.ffmathy.pooparazzi.core.sample.SampleData.TODAY
import com.ffmathy.pooparazzi.core.sample.SampleData.ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

private fun oct(day: Int) = LocalDate.of(2026, 10, day)
private fun sep(day: Int) = LocalDate.of(2026, 9, day)
private fun at(day: LocalDate, h: Int, m: Int = 0) = epochMillis(day, h, m, ZONE)

private fun report(entries: List<Entry> = SampleData.history(), range: StatsRange = StatsRange.Last30Days, now: Long = NOW_1432) =
    checkNotNull(statsReport(entries, now, ZONE, range)) { "no report" }

/** Tenths of feeds, pee and poop, and minutes of sleep: the four averages as shown. */
private fun StatsWindow.averages(): List<Long?> = listOf(
    average(StatsMetric.FEEDS)?.tenths?.toLong(),
    average(StatsMetric.PEE)?.tenths?.toLong(),
    average(StatsMetric.POOP)?.tenths?.toLong(),
    average(StatsMetric.SLEEP)?.minutes,
)

/**
 * V1_ADDITIONS §8, fixture F1 at Mon 26 Oct 14:32: Fri 16 a feed (the first logged day); Sat 17 two feeds, a pee and a
 * sleep; Sun 18 only a poop; Mon 19 nothing; Tue 20 a feed, a plain change and a sleep; nothing after.
 */
private val F1 = listOf(
    Entry.feed(FeedType.FORMULA, at(oct(16), 10), id = "fri-feed"),
    Entry.feed(FeedType.FORMULA, at(oct(17), 8), id = "sat-feed-1"),
    Entry.diaper(at(oct(17), 9), pee = true, poop = false, id = "sat-pee"),
    Entry.feed(FeedType.FORMULA, at(oct(17), 12), id = "sat-feed-2"),
    Entry.sleep(at(oct(17), 13), at(oct(17), 14, 30), id = "sat-sleep"),
    Entry.diaper(at(oct(18), 9), pee = false, poop = true, id = "sun-poop"),
    Entry.feed(FeedType.FORMULA, at(oct(20), 8), id = "tue-feed"),
    Entry.diaper(at(oct(20), 9), pee = false, poop = false, id = "tue-plain"),
    Entry.sleep(at(oct(20), 13), at(oct(20), 15), id = "tue-sleep"),
)

/** Which days the windows cover, and how ranges are clamped and normalised (`history()` at Mon 26 Oct 14:32). */
class StatisticsWindowsTest {
    @Test
    fun lastSevenDaysAreTheSevenWholeDaysBeforeToday() {
        val week = report().lastSevenDays
        assertEquals(oct(19) to oct(25), week.from to week.to)
        assertEquals(7, week.dayCount)
        assertEquals(oct(25), report().lastDay)
    }

    @Test
    fun lastThirtyDaysEndYesterday() {
        val month = report().lastThirtyDays
        assertEquals(sep(26) to oct(25), month.from to month.to)
        assertEquals(30, month.dayCount)
        assertEquals("the default comparison is the last 30 days", month, report().comparison)
    }

    @Test
    fun lastNinetyDaysKeepTheirDatesBeforeLogging() {
        val quarter = report(range = StatsRange.Last90Days).comparison
        assertEquals(LocalDate.of(2026, 7, 28) to oct(25), quarter.from to quarter.to)
        assertEquals(90, quarter.dayCount)
        assertEquals(33, quarter.loggedDays)
    }

    @Test
    fun allDaysStartTheDayAfterTheFirstEntry() {
        val all = report(range = StatsRange.AllDays)
        assertEquals("history() starts on Tue 22 Sep, a partial day", sep(23), all.firstDay)
        assertEquals(sep(23) to oct(25), all.comparison.from to all.comparison.to)
    }

    @Test
    fun aCustomRangeIsClampedOnEveryBuild() {
        assertEquals(StatsRange.Custom(sep(23), oct(25)), report(range = StatsRange.Custom(LocalDate.of(2026, 9, 1), oct(26))).range)
        assertEquals(StatsRange.Custom(oct(1), oct(10)), report(range = StatsRange.Custom(oct(1), oct(10))).range)
        assertEquals(StatsRange.Last30Days, report(range = StatsRange.Custom(sep(1), sep(10))).range)
    }

    @Test
    fun aConfirmedRangeEqualToAPresetBecomesThePreset() {
        assertEquals(StatsRange.Last30Days, StatsRange.Custom(sep(26), oct(25)).normalised(sep(23), oct(25)))
        assertEquals(StatsRange.AllDays, StatsRange.Custom(sep(1), oct(25)).normalised(sep(23), oct(25)))
        assertEquals(StatsRange.Custom(oct(1), oct(10)), StatsRange.Custom(oct(1), oct(10)).normalised(sep(23), oct(25)))
        assertEquals(
            StatsRange.Last90Days,
            StatsRange.Custom(LocalDate.of(2026, 7, 28), oct(25)).normalised(LocalDate.of(2026, 7, 1), oct(25)),
        )
    }

    @Test
    fun theNumbersDoNotMoveDuringTheDay() {
        assertEquals(report(now = NOW_0312), report(now = NOW_1432))
    }
}

/** The four averages of `history()` and the half-up rounding. */
class StatisticsAveragesTest {
    @Test
    fun theWeekAverages6point7Feeds7point9Pee0point7Poop14h01mSleep() {
        val week = report().lastSevenDays
        assertEquals(listOf(67, 79, 7, 841L), week.averages())
        assertEquals(7, week.loggedDays)
    }

    @Test
    fun theMonthAverages7point1Feeds7point6Pee1point8Poop14h07mSleep() {
        assertEquals(listOf(71, 76, 18, 847L), report().lastThirtyDays.averages())
    }

    @Test
    fun ninetyAndAllDaysAverageTheSame33Days() {
        assertEquals(listOf(72, 76, 18, 840L), report(range = StatsRange.Last90Days).comparison.averages())
        assertEquals(listOf(72, 76, 18, 840L), report(range = StatsRange.AllDays).comparison.averages())
    }

    @Test
    fun aFortnightRoundsSleepHalfUp() {
        // 12 033 minutes over 14 days is 859.5.
        assertEquals(listOf(71, 75, 17, 860L), report(range = StatsRange.Custom(oct(5), oct(18))).comparison.averages())
    }

    @Test
    fun roundingIsHalfUp() {
        assertEquals(13, roundedTenths(5, 4))
        assertEquals(3, roundedTenths(1, 4))
        assertEquals(1, roundedTenths(1, 8))
        assertEquals(4, roundedTenths(3, 8))
        assertEquals(421L, roundedMinutes(841, 2))
        assertEquals(1L, roundedMinutes(1, 2))
    }

    @Test
    fun twoForgottenDaysLeaveTwentyEightOfThirty() {
        val month = report(SampleData.historyWithGaps()).lastThirtyDays
        assertEquals(listOf(71, 77, 18, 846L), month.averages())
        assertEquals(28, month.loggedDays)
        val byDate = month.days.associateBy { it.date }
        assertEquals(DayStatus.NOTHING_LOGGED, byDate.getValue(oct(13)).status)
        assertEquals(DayStatus.NOTHING_LOGGED, byDate.getValue(oct(14)).status)
        assertEquals(DayStatus.COUNTED, byDate.getValue(oct(15)).status)
        assertEquals(863L, byDate.getValue(oct(15)).counts.sleepMinutes)
    }
}

/** Which days count for which kind (fixture F1 and small logs). */
class StatisticsDaysTest {
    @Test
    fun forgottenKindsAreLeftOutPerKind() {
        val f1 = report(F1)
        assertEquals(listOf(15, 3, 3, 105L), f1.lastThirtyDays.averages())
        assertEquals(3, f1.lastThirtyDays.loggedDays)
        assertEquals(listOf(10, 0, 0, 120L), f1.lastSevenDays.averages())
        assertEquals(1, f1.lastSevenDays.loggedDays)
    }

    @Test
    fun aPlainChangeIsARealZeroForPeeAndPoop() {
        val tuesday = report(F1).lastSevenDays.days.single { it.date == oct(20) }
        assertTrue(tuesday.counted(StatsMetric.PEE))
        assertTrue(tuesday.counted(StatsMetric.POOP))
        assertEquals(0L, tuesday.value(StatsMetric.PEE))
        assertEquals(0L, tuesday.value(StatsMetric.POOP))
    }

    @Test
    fun theFirstLoggedDayIsBeforeLogging() {
        val days = report(F1).lastThirtyDays.days.associateBy { it.date }
        assertEquals(DayStatus.BEFORE_LOGGING, days.getValue(oct(16)).status)
        assertEquals(DayStatus.COUNTED, days.getValue(oct(17)).status)
        assertEquals(DayStatus.NOTHING_LOGGED, days.getValue(oct(19)).status)
    }

    @Test
    fun aSleepTailAloneDoesNotCountADay() {
        val entries = listOf(
            Entry.feed(null, at(oct(18), 10), id = "sun"),
            Entry.feed(null, at(oct(19), 10), id = "mon"),
            Entry.feed(null, at(oct(21), 10), id = "wed"),
            Entry.sleep(at(oct(19), 22), at(oct(20), 6), id = "night"),
        )
        val week = report(entries).lastSevenDays
        val tuesday = week.days.single { it.date == oct(20) }
        assertEquals(DayStatus.NOTHING_LOGGED, tuesday.status)
        assertEquals(360L, tuesday.counts.sleepMinutes)
        assertEquals(listOf(10, null, null, 120L), week.averages())
    }

    @Test
    fun aForgottenRunningSleepCountsOnlyOnTheDayItStarted() {
        val entries = listOf(
            Entry.feed(null, at(oct(15), 10), id = "thu"),
            Entry.feed(null, at(oct(16), 10), id = "fri"),
            Entry.feed(null, at(oct(17), 10), id = "sat"),
            Entry.sleep(at(oct(17), 20), id = "forgotten"),
        )
        val week = report(entries).lastSevenDays
        assertEquals(listOf(null, null, null, null), week.averages())
        assertEquals(0, week.loggedDays)
        assertEquals(listOf(10, null, null, 240L), report(entries).lastThirtyDays.averages())
    }

    @Test
    fun aRunningSleepCountsUpToMidnightOfYesterday() {
        val entries = listOf(
            Entry.feed(null, at(oct(24), 10), id = "sat"),
            Entry.feed(null, at(oct(25), 10), id = "sun"),
            Entry.sleep(at(oct(25), 22), id = "running"),
        )
        listOf(NOW_0312, NOW_1432).forEach { now ->
            assertEquals(listOf(10, null, null, 120L), report(entries, now = now).lastSevenDays.averages())
        }
    }

    @Test
    fun sleepAcrossMidnightCountsOnBothDaysThatHaveASleep() {
        val entries = listOf(
            Entry.feed(null, at(oct(20), 10), id = "tue"),
            Entry.feed(null, at(oct(21), 10), id = "wed"),
            Entry.sleep(at(oct(21), 22), at(oct(22), 6), id = "night"),
            Entry.sleep(at(oct(22), 13), at(oct(22), 14), id = "nap"),
        )
        // Wed 120 + Thu (360 + 60), over the 2 days a sleep starts on.
        assertEquals(270L, report(entries).lastSevenDays.average(StatsMetric.SLEEP)?.minutes)
    }

    @Test
    fun deletedAndFutureEntriesAreIgnored() {
        val entries = SampleData.history() +
            Entry.feed(null, at(sep(10), 10), id = "deleted").copy(deleted = true) +
            Entry.feed(null, at(TODAY, 15), id = "future")
        val withExtras = report(entries)
        assertEquals(sep(23), withExtras.firstDay)
        assertEquals(report().lastThirtyDays, withExtras.lastThirtyDays)
    }
}

/** DST days in Copenhagen: Sun 29 Mar 2026 has 23 hours, Sun 25 Oct 2026 has 25. Sleep minutes are real minutes. */
class StatisticsDstTest {
    @Test
    fun fallBackDayKeepsItsRealMinutes() {
        val entries = listOf(
            Entry.feed(null, at(oct(23), 10), id = "fri"),
            Entry.sleep(at(oct(24), 23), at(oct(25), 5), id = "night"),
            Entry.sleep(at(oct(25), 13), at(oct(25), 14), id = "nap"),
        )
        val week = report(entries).lastSevenDays
        assertEquals(listOf(60L, 420L), week.days.filter { it.counted(StatsMetric.SLEEP) }.map { it.counts.sleepMinutes })
        assertEquals(240L, week.average(StatsMetric.SLEEP)?.minutes)
    }

    @Test
    fun springForwardDayKeepsItsRealMinutes() {
        val march = { day: Int -> LocalDate.of(2026, 3, day) }
        val entries = listOf(
            Entry.feed(null, at(march(27), 10), id = "fri"),
            Entry.sleep(at(march(28), 23), at(march(29), 5), id = "night"),
            Entry.sleep(at(march(29), 13), at(march(29), 14), id = "nap"),
        )
        val week = report(entries, now = at(march(31), 12)).lastSevenDays
        val days = week.days.associateBy { it.date }
        assertEquals(60L, days.getValue(march(28)).counts.sleepMinutes)
        assertEquals(300L, days.getValue(march(29)).counts.sleepMinutes)
        assertEquals(DayStatus.NOTHING_LOGGED, days.getValue(march(30)).status)
        assertEquals(180L, week.average(StatsMetric.SLEEP)?.minutes)
    }
}

/** The bars, band, lines and scale of the chart. */
class StatisticsChartTest {
    @Test
    fun theDefaultPoopChartIsThirtyDaysWithTheLastSevenOnTheBand() {
        val chart = report().chart(StatsMetric.POOP)
        assertEquals(1, chart.binDays)
        assertEquals(
            listOf(3, 3, 4, 4, 3, 2, 2, 3, 0, 2, 1, 5, 2, 2, 1, 0, 3, 2, 1, 2, 1, 2, 0, 0, 1, 0, 1, 0, 2, 1).map { it.toDouble() },
            chart.bars.map { it.value },
        )
        assertEquals(23..29, chart.bandBars)
        assertEquals(0.7, chart.weekLine)
        assertEquals(1.8, chart.rangeLine)
        assertEquals(5.0, chart.top, 0.0)
        assertEquals(oct(7), chart.highest?.from)
        assertEquals("the latest of the zeros", oct(23), chart.lowest?.from)
        assertEquals(0, chart.gapDays)
        assertEquals(0, chart.daysBeforeLogging)
    }

    @Test
    fun sleepIsInMinutesAndTheScaleEndsOnAnHour() {
        val chart = report().chart(StatsMetric.SLEEP)
        assertEquals(841.0, chart.weekLine)
        assertEquals(847.0, chart.rangeLine)
        assertEquals(960.0, chart.top, 0.0)
        assertEquals(oct(2), chart.highest?.from)
        assertEquals(927.0, chart.highest?.value)
        assertEquals(sep(26), chart.lowest?.from)
        assertEquals(684.0, chart.lowest?.value)
    }

    @Test
    fun gapsAreNullAndZerosAreZero() {
        val chart = report(F1).chart(StatsMetric.POOP)
        val values = chart.bars.map { it.value }
        assertEquals(List(21) { null }, values.take(21))
        assertEquals(listOf(0.0, 1.0, null, 0.0), values.subList(21, 25))
        assertEquals(List(5) { null }, values.takeLast(5))
        assertEquals(6, chart.gapDays)
        assertEquals(21, chart.daysBeforeLogging)
        assertEquals(1.0, chart.top, 0.0)
        assertEquals(0.0, chart.weekLine)
        assertEquals(0.3, chart.rangeLine)
        assertEquals(oct(18), chart.highest?.from)
        assertEquals(oct(20), chart.lowest?.from)
    }

    @Test
    fun binDaysByLength() {
        assertEquals(listOf(1, 1, 7, 7, 7, 14, 14, 21), listOf(1, 62, 63, 180, 434, 435, 868, 869).map(::binDays))
        (1..2000).forEach { n -> assertTrue("$n days", (n + binDays(n) - 1) / binDays(n) <= MAX_DAILY_BARS) }
    }

    @Test
    fun halfAYearIsTwentySixSevenDayBarsEndingYesterday() {
        val chart = report(SampleData.history(days = 200), StatsRange.Custom(LocalDate.of(2026, 4, 29), oct(25))).chart(StatsMetric.POOP)
        assertEquals(26, chart.bars.size)
        assertEquals(StatsBar(LocalDate.of(2026, 4, 29), LocalDate.of(2026, 5, 3), total = 15, countedDays = 5), chart.bars.first())
        assertEquals(StatsBar(oct(19), oct(25), total = 13, countedDays = 7), chart.bars.last())
        assertEquals(25..25, chart.bandBars)
        assertNull(chart.weekLine)
        assertEquals(2.2, chart.rangeLine)
        assertEquals(4.0, chart.top, 0.0)
        assertEquals(StatsBar(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 17), total = 23, countedDays = 7), chart.highest)
        assertEquals(StatsBar(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23), total = 8, countedDays = 7), chart.lowest)
    }

    @Test
    fun lastNinetyDaysShowsTheDaysBeforeLoggingAsEmptyBars() {
        val chart = report(range = StatsRange.Last90Days).chart(StatsMetric.POOP)
        assertEquals(13, chart.bars.size)
        assertEquals(LocalDate.of(2026, 7, 28) to LocalDate.of(2026, 8, 2), chart.bars.first().from to chart.bars.first().to)
        assertEquals(List(8) { 0 }, chart.bars.take(8).map { it.countedDays })
        assertEquals(StatsBar(sep(21), sep(27), total = 14, countedDays = 5), chart.bars[8])
        assertEquals(StatsBar(oct(19), oct(25), total = 5, countedDays = 7), chart.bars[12])
        assertEquals(12..12, chart.bandBars)
        assertEquals(0, chart.gapDays)
        assertEquals(57, chart.daysBeforeLogging)
    }

    @Test
    fun theBandNeedsAWindowLongerThanSevenDaysEndingYesterday() {
        val early = report(range = StatsRange.Custom(oct(10), oct(22))).chart(StatsMetric.POOP)
        assertEquals(13, early.bars.size)
        assertNull(early.bandBars)
        assertNull(early.weekLine)
        assertEquals(1.1, early.rangeLine)
        assertNull(report(range = StatsRange.Custom(oct(19), oct(25))).chart(StatsMetric.POOP).bandBars)
        assertNull(report(SampleData.history(days = 3), StatsRange.AllDays).chart(StatsMetric.POOP).bandBars)
    }
}

/** Too little to count, and input order. */
class StatisticsEdgeTest {
    @Test
    fun anEmptyLogHasNoReport() {
        assertNull(statsReport(emptyList(), NOW_1432, ZONE, StatsRange.Last30Days))
    }

    @Test
    fun onlyTodayLoggedHasNoReport() {
        // The scenario starts Sun 22:40, so its first full day would be today.
        assertNull(statsReport(SampleData.scenario(), NOW_1432, ZONE, StatsRange.Last30Days))
    }

    @Test
    fun theFirstFullDayIsTheDayAfterTheFirstEntry() {
        val only = report(listOf(Entry.feed(null, at(oct(24), 15), id = "sat")))
        assertEquals(oct(25), only.firstDay)
        assertEquals(oct(25), only.lastDay)
        assertEquals(listOf(null, null, null, null), only.lastSevenDays.averages())
        assertEquals(0, only.lastSevenDays.loggedDays)
        assertTrue(only.chart(StatsMetric.POOP).isEmpty)
    }

    @Test
    fun inputOrderDoesNotMatter() {
        assertEquals(report(), report(SampleData.history().shuffled(Random(7))))
    }
}
