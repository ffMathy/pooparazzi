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
                Entry.feed(FeedType.BOTTLE, at(d, 3 * k), id = "f$d-$k"),
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
        val sides = regularDays(7) + (0 until 7).map { d -> Entry.feed(FeedType.BOTTLE, at(d, 9, 5), id = "side$d") }
        val p = week(sides, 7, at(7, 12))!!
        assertEquals(3 * HOUR, p.day.betweenFeeds!!.medianMillis)
        assertEquals(3 * HOUR, p.day.betweenFeeds!!.lowMillis)
    }

    @Test
    fun aHalfLoggedDayIsLeftOutOfItsLanes() {
        // Day 3 has only its first two feeds and diapers and no sleep from its own night onwards.
        val entries = regularDays(7).filterNot { entry ->
            val day3 = entry.id.startsWith("f3-") || entry.id.startsWith("d3-")
            (day3 && entry.id.substringAfter('-').toInt() >= 2) || entry.id == "nap3" || entry.id == "night3"
        } + Entry.sleep(at(3, 1), at(3, 1, 20), id = "odd3")
        val p = week(entries, 7, at(7, 12))!!
        assertEquals(7, p.days)
        // 06:00 has a feed on every day that counts: the half-logged day does not water it down.
        assertEquals(1f, p.feeds[12], 0.001f)
        assertEquals(1f, p.diapers[13], 0.001f)
        // The nap shows on every day that counts, and the odd 20-minute sleep is not among the sleeps.
        assertEquals(1f, p.sleep[26], 0.001f)
        assertEquals(6 * HOUR, p.night.sleep!!.lowMillis)
    }

    @Test
    fun aStretchUnlikeTheOthersIsLeftOutOfItsInterval() {
        // One nap of 4 hours among naps of an hour.
        val entries = regularDays(7).map { if (it.id == "nap2") Entry.sleep(at(2, 10), at(2, 14), id = "nap2") else it }
        val p = week(entries, 7, at(7, 12))!!
        assertEquals(HOUR, p.day.sleep!!.medianMillis)
        assertEquals(HOUR, p.day.sleep!!.highMillis)
        assertEquals(6, p.day.sleep!!.samples)
    }

    @Test
    fun ordinarySpreadStays() {
        // Naps of 50, 55, 60, 65, 70, 75 and 80 minutes all look alike.
        val entries = regularDays(7).map { entry ->
            val d = entry.id.removePrefix("nap").toIntOrNull()
            if (entry.id.startsWith("nap") && d != null) Entry.sleep(at(d, 13), at(d, 13) + (50 + 5 * d) * MINUTE, id = entry.id) else entry
        }
        val p = week(entries, 7, at(7, 12))!!
        assertEquals(7, p.day.sleep!!.samples)
        assertEquals(65 * MINUTE, p.day.sleep!!.medianMillis)
    }

    @Test
    fun aDoubleLoggedDayIsLeftOutOfTheFeeds() {
        // Day 4 also has a feed at every half past, twice the usual count.
        val entries = regularDays(7) + (0 until 8).map { k -> Entry.feed(FeedType.BOTTLE, at(4, 3 * k + 1, 30), id = "x$k") }
        val p = week(entries, 7, at(7, 12))!!
        assertEquals(0f, p.feeds[3], 0.001f) // 01:30, only on the double-logged day
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
