package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_0312
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_1432
import com.ffmathy.pooparazzi.core.sample.SampleData.TODAY
import com.ffmathy.pooparazzi.core.sample.SampleData.YESTERDAY
import com.ffmathy.pooparazzi.core.sample.SampleData.ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

/** [dailyCounts] gives every day exactly what History shows for it ([Summary.dayCounts]), in one pass. */
class DailyCountsTest {

    @Test
    fun theReferenceDayAndTheEveningBefore() {
        val counts = dailyCounts(SampleData.scenario(), YESTERDAY, TODAY, NOW_1432, ZONE)
        // Yesterday holds only the start of the 22:40 sleep, up to midnight.
        assertEquals(listOf(DayCounts(feeds = 0, pee = 0, poop = 0, sleepMinutes = 80), DayCounts(5, 4, 2, 7 * 60 + 57)), counts)
    }

    @Test
    fun aRunningSleepCountsUpToNow() {
        val counts = dailyCounts(SampleData.scenarioAt0312(), TODAY, TODAY, NOW_0312, ZONE)
        // 00:00–01:35 and the running 02:05–03:12.
        assertEquals(listOf(DayCounts(feeds = 1, pee = 1, poop = 0, sleepMinutes = 95 + 67)), counts)
    }

    @Test
    fun daysBeforeTheFirstEntryAndAfterNowAreZero() {
        val counts = dailyCounts(SampleData.scenario(), TODAY.minusDays(5), TODAY.plusDays(2), NOW_1432, ZONE)
        assertEquals(8, counts.size)
        assertEquals(List(4) { DayCounts(0, 0, 0, 0) }, counts.take(4))
        assertEquals(DayCounts(0, 0, 0, 80), counts[4])
        assertEquals(DayCounts(5, 4, 2, 477), counts[5])
        assertEquals(List(2) { DayCounts(0, 0, 0, 0) }, counts.takeLast(2))
    }

    @Test
    fun anEmptyRangeHasNoDays() {
        assertTrue(dailyCounts(SampleData.scenario(), TODAY, YESTERDAY, NOW_1432, ZONE).isEmpty())
    }

    @Test
    fun deletedAndFutureEntriesAreLeftOut() {
        val entries = listOf(
            Entry.feed(FeedType.FORMULA, SampleData.at(9, 0), id = "deleted").copy(deleted = true),
            Entry.feed(FeedType.FORMULA, SampleData.at(15, 0), id = "future"),
            Entry.diaper(SampleData.at(10, 0), pee = true, poop = true, id = "both"),
        )
        assertEquals(listOf(DayCounts(feeds = 0, pee = 1, poop = 1, sleepMinutes = 0)), dailyCounts(entries, TODAY, TODAY, NOW_1432, ZONE))
    }

    @Test
    fun aSleepAcrossSeveralDaysIsSplitAtEachMidnight() {
        val sleep = Entry.sleep(SampleData.at(22, 0, TODAY.minusDays(2)), SampleData.at(1, 30), id = "long")
        val counts = dailyCounts(listOf(sleep), TODAY.minusDays(2), TODAY, NOW_1432, ZONE)
        // Sun 25 Oct 2026 is the 25-hour day of the change back from summer time.
        assertEquals(listOf(120L, 25 * 60L, 90L), counts.map { it.sleepMinutes })
    }

    @Test
    fun everyDayAgreesWithHistoryAcrossBothDstChanges() {
        // Copenhagen: 29 Mar 2026 is 23 hours long, 25 Oct 2026 is 25 hours long.
        listOf(LocalDate.of(2026, 3, 20) to LocalDate.of(2026, 4, 6), LocalDate.of(2026, 10, 18) to LocalDate.of(2026, 11, 3)).forEach { (from, to) ->
            listOf(ZONE, ZoneId.of("America/New_York"), ZoneId.of("UTC")).forEach { zone ->
                repeat(5) { seed ->
                    val entries = randomLog(from.minusDays(1), to, zone, Random(seed.toLong()))
                    // "Now" inside the range (a running sleep and later entries are cut at it) and after it.
                    listOf(midday(from.plusDays(9), zone) + 17_345, midday(to.plusDays(2), zone)).forEach { now ->
                        val summary = summarize(entries, now, zone)
                        val counts = dailyCounts(entries, from, to, now, zone)
                        var day = from
                        counts.forEach { count ->
                            assertEquals("$zone seed $seed now $now day $day", summary.dayCounts(day), count)
                            day = day.plusDays(1)
                        }
                        assertEquals(to.plusDays(1), day)
                    }
                }
            }
        }
    }

    private fun midday(day: LocalDate, zone: ZoneId): Long = epochMillis(day, 12, 0, zone)

    /**
     * A messy log from [from] to [to]: sleeps of 10 minutes to 30 hours (some across midnight and the DST change, one
     * possibly running), feeds and diapers at odd seconds, a few deleted entries.
     */
    private fun randomLog(from: LocalDate, to: LocalDate, zone: ZoneId, random: Random): List<Entry> {
        val entries = mutableListOf<Entry>()
        var t = from.atStartOfDay(zone).toInstant().toEpochMilli() + random.nextInt(3_600_000)
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var n = 0
        while (t < end) {
            val id = "e${n++}"
            when (random.nextInt(6)) {
                0 -> {
                    val length = if (random.nextInt(25) == 0) 30 * 3_600_000L else 600_000L + random.nextInt(4 * 3_600_000)
                    val running = t + length >= end && random.nextBoolean()
                    entries += Entry.sleep(t, if (running) null else t + length, id = id)
                    t += length
                }
                1, 2 -> entries += Entry.feed(FeedType.entries.getOrNull(random.nextInt(4)), t, id = id)
                else -> entries += Entry.diaper(t, pee = random.nextBoolean(), poop = random.nextBoolean(), id = id)
            }
            if (random.nextInt(15) == 0) entries[entries.lastIndex] = entries.last().copy(deleted = true)
            t += 60_000L + random.nextInt(3 * 3_600_000)
        }
        return entries
    }
}
