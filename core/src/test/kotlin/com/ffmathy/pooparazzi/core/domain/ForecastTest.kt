package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.BottleContents
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sample.SampleData.ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
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
        generateSequence(at(0, 0)) { it + 3 * HOUR }.takeWhile { it <= until }.mapIndexed { i, t -> Entry.feed(FeedType.BOTTLE, t, id = "f$i") }.toList()

    @Test
    fun regularFeedsPredictTheNextOneAGapLater() {
        val now = at(6, 13)
        val f = forecastAt(feedsEvery3h(at(6, 12)), now).feed!!
        assertEquals(at(6, 15), f.atMillis)
        assertEquals(2 * HOUR, f.remainingMillis(now))
        assertFalse(f.isDue(now))
    }

    @Test
    fun underFiveMinutesAwayIsSoon() {
        val now = at(6, 14, 57)
        val f = forecastAt(feedsEvery3h(at(6, 12)), now).feed!!
        assertNull(f.remainingMillis(now))
        assertFalse(f.isDue(now))
    }

    @Test
    fun aPassedEstimateStaysPutAndIsDue() {
        val now = at(6, 15, 24)
        val f = forecastAt(feedsEvery3h(at(6, 12)), now).feed!!
        assertEquals(at(6, 15), f.atMillis)
        assertNull(f.remainingMillis(now))
        assertTrue(f.isDue(now))
        assertTrue(f.isDue(f.atMillis))
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
        assertTrue(later.isDue(last + 210 * MINUTE))
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
        assertTrue(f.sleep!!.atMillis in SampleData.NOW_1432 + 40 * MINUTE..SampleData.NOW_1432 + 50 * MINUTE)
        assertTrue(f.feed!!.atMillis in SampleData.NOW_1432 + 50 * MINUTE..SampleData.NOW_1432 + 55 * MINUTE)
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

    // ---- Recent days count more (growth spurts) ----

    @Test
    fun equalWeightsGiveThePlainQuantile() {
        val values = listOf(10L, 20L, 30L, 70L)
        for (q in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            assertEquals(quantile(values, q), weightedQuantile(values.map { it to 1.0 }, q), 1e-9)
        }
    }

    @Test
    fun aHeavierValuePullsTheMedianTowardsItself() {
        val values = listOf(10L to 1.0, 20L to 1.0, 30L to 4.0)
        assertTrue(weightedQuantile(values, 0.5) > 20.0)
    }

    @Test
    fun afterAGrowthSpurtTheEstimateFollowsTheRecentDays() {
        // Feeds every 3 hours for four days, then every 2 hours for the last three: the recent rhythm wins.
        val feeds = mutableListOf<Entry>()
        var t = at(0, 0)
        while (t <= at(6, 12)) {
            feeds += Entry.feed(FeedType.BOTTLE, t, id = "f${feeds.size}")
            t += if (t < at(4, 0)) 3 * HOUR else 2 * HOUR
        }
        val last = feeds.last().startMillis
        val f = forecastAt(feeds, last + 30 * MINUTE).feed!!
        assertEquals(last + 2 * HOUR, f.atMillis)
    }

    // ---- The baby's age ----

    private val sleeps = (0..5).flatMap { d ->
        listOf(Entry.sleep(at(d, 10), at(d, 11), id = "n$d-10"), Entry.sleep(at(d, 14), at(d, 15), id = "n$d-14"))
    }

    @Test
    fun noSleepEstimateBeforeSixWeeks() {
        val asleep = sleeps + Entry.sleep(at(6, 14), null, id = "now")
        val now = at(6, 14, 20)
        val today = DAY.plusDays(6)
        assertNull(forecast(summarize(asleep, now, ZONE), birthDate = today.minusDays(41)).sleep)
        assertNotNull(forecast(summarize(asleep, now, ZONE), birthDate = today.minusDays(42)).sleep)
        // Without a birth date, as before.
        assertNotNull(forecast(summarize(asleep, now, ZONE)).sleep)
    }

    /** Poops alternating 2, 3 and 4 hours apart, regular enough to predict. */
    private fun regularPoops(): List<Entry> {
        val lengths = listOf(2, 3, 4)
        val poops = mutableListOf<Entry>()
        var t = at(0, 0)
        while (t <= at(6, 12)) {
            poops += Entry.diaper(t, pee = false, poop = true, id = "p${poops.size}")
            t += lengths[poops.size % 3] * HOUR
        }
        return poops
    }

    @Test
    fun fromSixWeeksABreastfedBabysPoopGapsAreNotEstimated() {
        val poops = regularPoops()
        val now = poops.last().startMillis + 30 * MINUTE
        val feeds = (0..5).map { Entry.breastfeed(at(it, 9, 7), BreastSide.LEFT_THEN_RIGHT, id = "b$it") }
        val bornLongAgo = DAY.minusDays(60)
        assertNull(forecast(summarize(poops + feeds, now, ZONE), birthDate = bornLongAgo).poop)
        // Younger than six weeks: estimated as before.
        assertNotNull(forecast(summarize(poops + feeds, now, ZONE), birthDate = DAY.minusDays(20)).poop)
        // Formula in the week: not breastfed, estimated as before.
        val formula = Entry.feed(FeedType.BOTTLE, at(6, 9, 30), id = "formula", contents = BottleContents.FORMULA)
        assertNotNull(forecast(summarize(poops + feeds + formula, now, ZONE), birthDate = bornLongAgo).poop)
    }

    @Test
    fun breastfedMeansEveryTypedFeedIsBreastMilk() {
        val breast = Entry.breastfeed(at(0, 1), id = "b")
        val milk = Entry.feed(FeedType.BOTTLE, at(0, 2), id = "m", contents = BottleContents.MILK)
        val bottle = Entry.feed(FeedType.BOTTLE, at(0, 3), id = "x")
        val untyped = Entry.feed(null, at(0, 4), id = "u")
        assertTrue(isBreastfed(listOf(breast, milk, untyped)))
        assertFalse("a bottle without its contents may be formula", isBreastfed(listOf(breast, bottle)))
        assertFalse("nothing says what was given", isBreastfed(listOf(untyped)))
        assertFalse(isBreastfed(listOf(breast, Entry.feed(FeedType.FOOD, at(0, 5), id = "f"))))
    }

    @Test
    fun onlyKeepsTheChosenKinds() {
        val f = forecast(summarize(SampleData.history(), SampleData.NOW_1432, ZONE))
        val feedOnly = f.only(setOf(ForecastKind.FEED))
        assertEquals(f.feed, feedOnly.feed)
        assertNull(feedOnly.sleep)
        assertNull(feedOnly.poop)
        assertEquals(f.sleep, f[ForecastKind.SLEEP])
    }
}
