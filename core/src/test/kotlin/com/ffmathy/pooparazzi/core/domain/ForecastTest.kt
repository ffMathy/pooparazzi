package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sample.SampleData.ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private val DAY: LocalDate = LocalDate.of(2026, 10, 1)

private fun at(day: Int, h: Int, m: Int = 0) = epochMillis(DAY.plusDays(day.toLong()), h, m, ZONE)

private fun forecastAt(entries: List<Entry>, now: Long) = forecast(summarize(entries, now, ZONE))

/** V1_ADDITIONS §9: what the last 7 days say comes next. */
class ForecastTest {
    /** A feed every 3 hours, on the hour, for the days before [until]. */
    private fun feedsEvery3h(until: Long): List<Entry> =
        generateSequence(at(0, 0)) { it + 3 * HOUR }.takeWhile { it <= until }.mapIndexed { i, t -> Entry.feed(FeedType.FORMULA, t, id = "f$i") }.toList()

    @Test
    fun regularFeedsPredictTheNextOneAGapLater() {
        val now = at(6, 13)
        val f = forecastAt(feedsEvery3h(at(6, 12)), now).feed!!
        assertEquals(at(6, 15), f.atMillis)
        assertEquals(2 * HOUR, f.remainingMillis(now))
        assertNull(f.agoMillis(now))
    }

    @Test
    fun underFiveMinutesAwayIsSoon() {
        val now = at(6, 14, 57)
        val f = forecastAt(feedsEvery3h(at(6, 12)), now).feed!!
        assertNull(f.remainingMillis(now))
        assertNull(f.agoMillis(now))
    }

    @Test
    fun aPassedEstimateStaysPutAndSaysHowLongAgo() {
        val now = at(6, 15, 24)
        val f = forecastAt(feedsEvery3h(at(6, 12)), now).feed!!
        assertEquals(at(6, 15), f.atMillis)
        assertNull(f.remainingMillis(now))
        assertEquals(24 * MINUTE, f.agoMillis(now))
    }

    @Test
    fun theEstimateIsTheMedianOfTheComparableStretchesAndDoesNotMoveOn() {
        // Poops (no time of day to match) alternate 2 h, 3 h, 4 h apart: the typical gap is 3 h, whether 30 minutes
        // or 3½ hours have passed since the last one.
        val lengths = listOf(2, 3, 4)
        val poops = mutableListOf<Entry>()
        var t = at(0, 0)
        var i = 0
        while (t <= at(6, 12)) {
            poops += Entry.diaper(t, pee = false, poop = true, id = "p$i")
            t += lengths[i % 3] * HOUR
            i++
        }
        val last = poops.last().startMillis
        assertEquals(last + 3 * HOUR, forecastAt(poops, last + 30 * MINUTE).poop!!.atMillis)
        val later = forecastAt(poops, last + 210 * MINUTE).poop!!
        assertEquals(last + 3 * HOUR, later.atMillis)
        assertEquals(30 * MINUTE, later.agoMillis(last + 210 * MINUTE))
        assertEquals(last + 8 * HOUR, later.goneAtMillis)
    }

    @Test
    fun sleepIsComparedWithSleepsAtTheSameTimeOfDay() {
        // Every day: a 1 h nap at 10:00 and 14:00, a 4 h night sleep from 21:00.
        val sleeps = (0..5).flatMap { d ->
            listOf(
                Entry.sleep(at(d, 10), at(d, 11), id = "n$d-10"),
                Entry.sleep(at(d, 14), at(d, 15), id = "n$d-14"),
                Entry.sleep(at(d, 21), at(d, 21) + 4 * HOUR, id = "s$d"),
            )
        }
        val night = forecastAt(sleeps + Entry.sleep(at(6, 21), null, id = "now"), at(6, 21, 30)).sleep!!
        assertEquals(at(6, 21) + 4 * HOUR, night.atMillis)
        val nap = forecastAt(sleeps + Entry.sleep(at(6, 14), null, id = "now"), at(6, 14, 20)).sleep!!
        assertEquals(at(6, 15), nap.atMillis)
    }

    @Test
    fun awakeTheSleepForecastIsTheNextSleepFromTheWakeWindows() {
        // Wake windows: 11:00 → 14:00 (3 h) and 15:00 → 21:00 (6 h); 01:00 → 10:00 is the night and too long to count.
        val sleeps = (0..6).flatMap { d ->
            listOf(Entry.sleep(at(d, 10), at(d, 11), id = "n$d-10"), Entry.sleep(at(d, 14), at(d, 15), id = "n$d-14"))
        }
        val f = forecastAt(sleeps, at(6, 15, 10)).sleep!!
        assertEquals(at(6, 18), f.atMillis)
    }

    @Test
    fun tooFewStretchesPredictNothing() {
        val feeds = listOf(at(6, 0), at(6, 3), at(6, 6), at(6, 9)).mapIndexed { i, t -> Entry.feed(null, t, id = "f$i") }
        assertNull(forecastAt(feeds, at(6, 10)).feed)
    }

    @Test
    fun manyStretchesFromOneDayAreNotYetAPattern() {
        val feeds = (0..7).map { Entry.feed(null, at(6, 0) + it * 2 * HOUR, id = "f$it") }
        assertNull(forecastAt(feeds, at(6, 15)).feed)
    }

    @Test
    fun stretchesOlderThanAWeekDoNotCount() {
        val old = feedsEvery3h(at(6, 12)).filter { it.startMillis < at(-1, 0) }
        assertTrue(forecastAt(feedsEvery3h(at(-1, 0)), at(7, 1)).isEmpty)
        assertTrue(old.isEmpty())
    }

    @Test
    fun poopThatFollowsNoPatternIsNotPredicted() {
        val gaps = listOf(1, 30, 2, 50, 3, 40, 1, 20).map { it * HOUR }
        val poops = gaps.runningFold(at(0, 8)) { t, gap -> t + gap }
            .mapIndexed { i, t -> Entry.diaper(t, pee = false, poop = true, id = "p$i") }
        assertNull(forecastAt(poops, poops.last().startMillis + HOUR).poop)
    }

    @Test
    fun regularPoopIsPredictedWhateverTheTimeOfDay() {
        val poops = (0..12).map { Entry.diaper(at(0, 7) + it * 12 * HOUR, pee = true, poop = true, id = "p$it") }
        val last = poops.last().startMillis
        assertEquals(last + 12 * HOUR, forecastAt(poops, last + HOUR).poop!!.atMillis)
    }

    @Test
    fun aStretchFarLongerThanAnyOfTheWeekMeansLoggingStopped() {
        assertNull(forecastAt(feedsEvery3h(at(6, 0)), at(6, 7)).feed)
    }

    @Test
    fun theSampleWeekPredictsSleepAndFeedButNotPoop() {
        val f = forecast(summarize(SampleData.history(), SampleData.NOW_1432, ZONE))
        assertTrue(f.sleep!!.atMillis in SampleData.NOW_1432 + 45 * MINUTE..SampleData.NOW_1432 + 55 * MINUTE)
        assertTrue(f.feed!!.atMillis in SampleData.NOW_1432 + 55 * MINUTE..SampleData.NOW_1432 + 60 * MINUTE)
        assertNull(f.poop)
    }

    @Test
    fun oneDayOfLoggingIsNotEnough() {
        assertTrue(forecast(summarize(SampleData.scenario(), SampleData.NOW_1432, ZONE)).isEmpty)
    }

    /**
     * Cluster feeds 30 minutes apart between long gaps (so the gaps between poops say nothing) from day 0 until [until], the last one at
     * [until]; each followed by a poop [delays] (cycled; null: no poop) minutes later, if that is before [now].
     */
    private fun poopAfterFeeds(until: Long, now: Long, delays: List<Int?>): List<Entry> {
        val gaps = listOf(30, 30, 30, 30, 30, 480, 480, 480).map { it * MINUTE }
        val feeds = generateSequence(0 to until) { (i, t) -> i + 1 to t - gaps[i % gaps.size] }
            .takeWhile { it.second >= at(0, 0) }.map { it.second }.toList().reversed()
        return feeds.flatMapIndexed { i, t ->
            val poopAt = delays[i % delays.size]?.let { t + it * MINUTE }?.takeIf { it <= now }
            listOf(Entry.feed(null, t, id = "f$i")) + listOfNotNull(poopAt?.let { Entry.diaper(it, pee = true, poop = true, id = "p$i") })
        }
    }

    @Test
    fun poopAfterMostFeedsIsPredictedAfterTheLastFeed() {
        // A poop 15–25 minutes after 3 in 4 feeds: the gaps between poops vary too much, the delay after a feed does not.
        val now = at(6, 12, 5)
        val poop = forecastAt(poopAfterFeeds(at(6, 12), now, listOf(15, 20, null, 25)), now).poop!!
        assertEquals(at(6, 12, 20), poop.atMillis)
        assertEquals(at(6, 12, 50), poop.goneAtMillis)
        assertEquals(15 * MINUTE, poop.remainingMillis(now))
    }

    @Test
    fun noPoopAfterFeedOnceItHasBeenLogged() {
        val now = at(6, 12, 40)
        val entries = poopAfterFeeds(at(6, 12), now, listOf(20))
        assertTrue(entries.last().poop)
        assertNull(forecastAt(entries, now).poop)
    }

    @Test
    fun noPoopAfterFeedOncePastTheLongestDelay() {
        val now = at(6, 12, 51)
        assertNull(forecastAt(poopAfterFeeds(at(6, 12), now, listOf(15, 20, null, 25)), now).poop)
    }

    @Test
    fun poopAfterOnlySomeFeedsIsNotPredicted() {
        val now = at(6, 12, 5)
        assertNull(forecastAt(poopAfterFeeds(at(6, 12), now, listOf(15, null, null, 25)), now).poop)
    }

    @Test
    fun regularPoopGapsWinOverPoopAfterFeeds() {
        // Feeds every 3 hours, each followed by a poop 20 minutes later: the poop gaps (3 h) are regular enough on their
        // own, so after the 09:20 poop the next is due at 12:20, not 20 minutes after a feed.
        val entries = feedsEvery3h(at(6, 9)).flatMap { listOf(it, Entry.diaper(it.startMillis + 20 * MINUTE, pee = true, poop = true, id = "p${it.id}")) }
        assertEquals(at(6, 12, 20), forecastAt(entries, at(6, 9, 30)).poop!!.atMillis)
    }

    /** Widgets redraw when an estimate's relative words change: each five-minute step, "soon", then every minute. */
    @Test
    fun theWordsChangeAtEachStepThenEachMinute() {
        val minute = 60_000L
        val at = 100 * minute
        val e = Expected(atMillis = at, goneAtMillis = at + 60 * minute, samples = 5)
        // 53m away reads "in 55m" until it is 50m away, then "in 50m".
        assertEquals(at - 50 * minute, e.nextTextChange(at - 53 * minute))
        assertEquals(50 * minute, e.remainingMillis(at - 50 * minute))
        // At 5m sharp it reads "in 5m"; a millisecond later "soon".
        assertEquals(at - 5 * minute + 1, e.nextTextChange(at - 5 * minute))
        assertNull(e.remainingMillis(at - 5 * minute + 1))
        // "Soon" until a minute past it, then "1m ago", "2m ago", ...
        assertEquals(at + minute, e.nextTextChange(at - 2 * minute))
        assertEquals(at + 2 * minute, e.nextTextChange(at + minute))
        assertEquals(at + 25 * minute, e.nextTextChange(at + 24 * minute + 30_000L))
    }
}
