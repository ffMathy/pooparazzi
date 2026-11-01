package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sample.SampleData.ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private val DAY: LocalDate = LocalDate.of(2026, 10, 1)

private fun at(day: Int, h: Int, m: Int = 0) = epochMillis(DAY.plusDays(day.toLong()), h, m, ZONE)

/** The pattern of the 7 days before day [day] (as the last-7-days range on that day), seen at [now]. */
private fun week(entries: List<Entry>, day: Int, now: Long) =
    dailyPattern(entries, DAY.plusDays(day - 7L), DAY.plusDays(day - 1L), now, ZONE)

/** Statistics' "Typical day" card: the typical day over the range. */
class PatternTest {
    /** Each day: a feed every 3 h from 00:00, a diaper 30 min after each, a nap 13:00–14:00 and a night 20:00–02:00. */
    private fun regularDays(days: Int): List<Entry> = (0 until days).flatMap { d ->
        (0 until 8).flatMap { k ->
            listOf(
                Entry.feed(FeedType.FORMULA, at(d, 3 * k), id = "f$d-$k"),
                Entry.diaper(at(d, 3 * k, 30), pee = true, poop = false, id = "d$d-$k"),
            )
        } + Entry.sleep(at(d, 13), at(d, 14), id = "nap$d") + Entry.sleep(at(d, 20), at(d + 1, 2), id = "night$d")
    }

    @Test
    fun fewerThanThreeLoggedDaysIsNoPatternYet() {
        val entries = regularDays(2)
        assertNull(week(entries, 2, at(2, 12)))
        assertNotNull(week(regularDays(3), 3, at(3, 12)))
    }

    @Test
    fun theWindowIsTheRangeAndOnlyLoggedDaysCount() {
        val p = week(regularDays(10), 9, at(9, 12))!!
        assertEquals(DAY.plusDays(2), p.from)
        assertEquals(DAY.plusDays(8), p.to)
        assertEquals(7, p.days)
        // Days before logging began are in the range but not counted.
        assertEquals(3, dailyPattern(regularDays(3), DAY.minusDays(27), DAY.plusDays(2), at(3, 12), ZONE)!!.days)
    }

    @Test
    fun feedAndDiaperLanesMarkTheSlotsTheyHappenIn() {
        val p = week(regularDays(7), 7, at(7, 12))!!
        assertEquals(PatternRules.SLOTS, p.feeds.size)
        assertEquals(1f, p.feeds[0], 0.001f) // 00:00
        assertEquals(1f, p.feeds[6], 0.001f) // 03:00
        assertEquals(0f, p.feeds[1], 0.001f)
        assertEquals(1f, p.diapers[1], 0.001f) // 00:30
        assertEquals(0f, p.diapers[0], 0.001f)
    }

    @Test
    fun theSleepLaneIsTheShareOfTheSlotAsleep() {
        val p = week(regularDays(7), 7, at(7, 12))!!
        assertEquals(1f, p.sleep[26], 0.001f) // 13:00, the nap
        assertEquals(0f, p.sleep[28], 0.001f) // 14:00
        assertEquals(1f, p.sleep[44], 0.001f) // 22:00
        // 00:00–02:00 of the first day comes from no night before it: 6 of 7 days.
        assertEquals(6f / 7, p.sleep[0], 0.001f)
    }

    @Test
    fun intervalsAreSplitByDayAndNight() {
        val p = week(regularDays(7), 7, at(7, 12))!!
        assertEquals(3 * HOUR, p.day.betweenFeeds!!.medianMillis)
        assertEquals(3 * HOUR, p.night.betweenDiapers!!.medianMillis)
        assertEquals(HOUR, p.day.sleep!!.medianMillis)
        assertEquals(6 * HOUR, p.night.sleep!!.medianMillis)
        // Awake from 14:00 to 20:00 (by day) and from 02:00 to 13:00 (by night: it starts at 02:00).
        assertEquals(6 * HOUR, p.day.awake!!.medianMillis)
        assertEquals(11 * HOUR, p.night.awake!!.medianMillis)
    }

    @Test
    fun aFeedPerSideIsOneFeed() {
        val sides = regularDays(7) + (0 until 7).map { d -> Entry.feed(FeedType.FORMULA, at(d, 9, 5), id = "side$d") }
        val p = week(sides, 7, at(7, 12))!!
        assertEquals(3 * HOUR, p.day.betweenFeeds!!.medianMillis)
        assertEquals(3 * HOUR, p.day.betweenFeeds!!.lowMillis)
    }

    @Test
    fun theSampleWeekHasAPattern() {
        val p = dailyPattern(SampleData.history(), SampleData.TODAY.minusDays(7), SampleData.YESTERDAY, SampleData.NOW_1432, SampleData.ZONE)!!
        assertEquals(7, p.days)
        assertTrue(p.night.sleep!!.medianMillis > p.day.sleep!!.medianMillis)
        assertTrue(p.sleep.all { it in 0f..1f } && p.feeds.all { it in 0f..1f } && p.diapers.all { it in 0f..1f })
    }
}
