package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.normalized
import com.ffmathy.pooparazzi.core.model.durationMillis
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_0312
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_1432
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_1450
import com.ffmathy.pooparazzi.core.sample.SampleData.TODAY
import com.ffmathy.pooparazzi.core.sample.SampleData.YESTERDAY
import com.ffmathy.pooparazzi.core.sample.SampleData.ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val MINUTE = 60_000L

private fun at(h: Int, m: Int, day: LocalDate = TODAY) = epochMillis(day, h, m, ZONE)

/** V1_ADDITIONS §6: every derived value of the reference scenario. */
class SummaryAt1432Test {
    private val summary = summarize(SampleData.scenario(), NOW_1432, ZONE)

    @Test
    fun lastFed2h10mAgoAt1222Formula() {
        val feed = summary.lastFeed!!
        assertEquals(at(12, 22), feed.startMillis)
        assertEquals(FeedType.FORMULA, feed.feedType)
        assertEquals(130 * MINUTE, summary.sinceLastFeedMillis)
    }

    @Test
    fun awake1h05mSince1327() {
        assertNull(summary.runningSleep)
        assertEquals(at(13, 27), summary.lastSleepEnd)
        assertEquals(65 * MINUTE, summary.awakeMillis)
        assertEquals("sample-sleep-1240", summary.lastSleep?.id)
    }

    @Test
    fun pee48mAgoAt1344() {
        val diaper = summary.lastDiaper!!
        assertEquals(at(13, 44), diaper.startMillis)
        assertTrue(diaper.pee)
        assertFalse(diaper.poop)
        assertEquals(48 * MINUTE, summary.sinceLastDiaperMillis)
    }

    @Test
    fun today5Feeds4Pee2Poop7h57mSleep() {
        assertEquals(DayCounts(feeds = 5, pee = 4, poop = 2, sleepMinutes = 7 * 60 + 57), summary.today)
        assertEquals(TODAY, summary.todayDate)
    }

    @Test
    fun minimalEntriesCount() {
        // A feed with just its time counts as a feed and is the last feed; a plain diaper counts in neither pee nor poop.
        val untyped = Entry.feed(null, at(14, 0), id = "untyped")
        val plain = Entry.diaper(at(14, 10), pee = false, poop = false, id = "plain")
        val more = summarize(SampleData.scenario() + untyped + plain, SampleData.NOW_1432, SampleData.ZONE)
        assertEquals(DayCounts(feeds = 6, pee = 4, poop = 2, sleepMinutes = 7 * 60 + 57), more.today)
        assertEquals(untyped, more.lastFeed)
        assertEquals(plain, more.lastDiaper)
        assertEquals(32 * MINUTE, more.sinceLastFeedMillis)
    }

    @Test
    fun lampIsFeedIdle() {
        val state = lampState(summary)
        assertTrue(state is LampState.FeedIdle)
        state as LampState.FeedIdle
        assertEquals("sample-formula-1222", state.lastFeed?.id)
        assertEquals(130L, state.elapsedMinutes)
    }

    @Test
    fun dayEntriesNewestFirstIncludingTheSleepFromSunday() {
        val today = summary.dayEntries(TODAY)
        assertEquals(15, today.size)
        assertEquals("sample-pee-1344", today.first().id)
        assertEquals("sample-sleep-2240", today.last().id)
        assertEquals(today.sortedByDescending { it.startMillis }, today)

        val sunday = summary.dayEntries(YESTERDAY)
        assertEquals(listOf("sample-sleep-2240"), sunday.map { it.id })
        assertEquals(emptyList<Entry>(), summary.dayEntries(TODAY.plusDays(1)))
    }

    @Test
    fun sundayCountsOnlyItsPartOfTheCrossingSleep() {
        // 22:40 → 24:00 on Sunday = 80 min.
        assertEquals(DayCounts(0, 0, 0, 80), summary.dayCounts(YESTERDAY))
    }

    @Test
    fun ribbon() {
        val ribbon = summary.ribbon(TODAY)
        assertEquals(5, ribbon.sleeps.size)
        val first = ribbon.sleeps.first()
        assertEquals(0f, first.start)
        assertTrue(first.continuesBefore)
        assertEquals(95f / 1440f, first.end, 1e-6f)
        assertEquals(2, ribbon.breastfeeds.size)
        assertEquals(listOf("sample-formula-0525", "sample-formula-1222"), ribbon.formula.map { it.entry.id })
        assertEquals(listOf("sample-food-1030"), ribbon.food.map { it.entry.id })
        assertEquals(
            listOf(DiaperKind.PEE, DiaperKind.BOTH, DiaperKind.POOP, DiaperKind.PEE, DiaperKind.PEE),
            ribbon.diapers.map { it.kind },
        )
        assertEquals(872f / 1440f, ribbon.nowFraction!!, 1e-6f)
        assertEquals(7f / 24f, ribbon.fractionAt(LocalTime.of(7, 0), ZONE), 1e-6f)

        val sunday = summary.ribbon(YESTERDAY)
        assertNull(sunday.nowFraction)
        // Sunday 25 Oct 2026 is the 25-hour DST day in Copenhagen; the bar runs from 22:40 to the end of the day.
        assertEquals(25 * 60 * MINUTE, sunday.bounds.lengthMillis)
        assertTrue(sunday.sleeps.single().continuesAfter)
        assertEquals(1f, sunday.sleeps.single().end)
    }
}

class SummaryAt0312Test {
    private val summary = summarize(SampleData.scenarioAt0312(), NOW_0312, ZONE)

    @Test
    fun asleep1h07mSince0205WithEmberAt42Degrees() {
        val sleep = summary.runningSleep!!
        assertEquals(at(2, 5), sleep.startMillis)
        val state = lampState(summary)
        assertTrue(state is LampState.SleepRunning)
        assertEquals(67L, state.elapsedMinutes)
        assertEquals(42f, emberAngleDegrees(state.elapsedMinutes))
        assertNull(summary.awakeMillis)
    }

    @Test
    fun lastFed1h32mAgoAt0140Breast() {
        val feed = summary.lastFeed!!
        assertEquals(at(1, 40), feed.startMillis)
        assertEquals(FeedType.BREAST, feed.feedType)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, feed.breastSide)
        assertEquals(92 * MINUTE, summary.sinceLastFeedMillis)
    }

    @Test
    fun pee1h20mAgoAt0152() {
        assertEquals(at(1, 52), summary.lastDiaper!!.startMillis)
        assertEquals(80 * MINUTE, summary.sinceLastDiaperMillis)
    }

    @Test
    fun today1Feed1Pee0Poop2h42mSleepIncludingTheRunningSleep() {
        assertEquals(DayCounts(feeds = 1, pee = 1, poop = 0, sleepMinutes = 2 * 60 + 42), summary.today)
    }

    @Test
    fun lastSleepEndIsTheSundaySleepsEnd() {
        assertEquals(at(1, 35), summary.lastSleepEnd)
    }

    @Test
    fun runningSleepIsListedAndDrawnUpToNow() {
        val ribbon = summary.ribbon(TODAY)
        val running = ribbon.sleeps.last()
        assertTrue(running.running)
        assertTrue(running.continuesAfter)
        assertEquals(192f / 1440f, running.end, 1e-6f)
        assertEquals("sample-sleep-0205", summary.dayEntries(TODAY).first().id)
    }
}

class SummaryAt1450Test {
    private val summary = summarize(SampleData.scenarioWithRecentFeed(), NOW_1450, ZONE)

    @Test
    fun aBreastFeedIsAnInstantSoTheLampStaysIdle() {
        val state = lampState(summary)
        assertTrue(state is LampState.FeedIdle)
        state as LampState.FeedIdle
        assertEquals(feedId(), state.lastFeed?.id)
        assertEquals(18L, state.elapsedMinutes)
        assertEquals(0L, summary.lastFeed!!.durationMillis(NOW_1450))
    }

    @Test
    fun timeSinceLastFeedCountsFromTheBreastFeed() {
        assertEquals(feedId(), summary.lastFeed?.id)
        assertEquals(18 * MINUTE, summary.sinceLastFeedMillis)
    }

    @Test
    fun countsAndAwake() {
        assertEquals(DayCounts(feeds = 6, pee = 4, poop = 2, sleepMinutes = 477), summary.today)
        assertEquals(83 * MINUTE, summary.awakeMillis)
    }

    @Test
    fun aRunningSleepAfterABreastFeedShowsOnTheLamp() {
        val withSleep = SampleData.scenarioWithRecentFeed() + Entry.sleep(at(14, 40), id = "nap")
        val state = lampState(summarize(withSleep, NOW_1450, ZONE))
        assertTrue(state is LampState.SleepRunning)
    }

    @Test
    fun breastFeedsAreTickMarksOnTheRibbon() {
        val marks = summary.ribbon(TODAY).breastfeeds
        assertEquals(listOf("sample-breast-0140", "sample-breast-0800", feedId()), marks.map { it.entry.id })
        assertEquals(872f / 1440f, marks.last().at, 1e-6f)
    }

    @Test
    fun aLegacyRunningBreastfeedDoesNotRunOrBlockAnything() {
        // Stored by an older version as a running interval (no end), then read back through normalized().
        val legacy = Entry(
            id = "legacy", kind = EntryKind.FEED, startMillis = at(14, 0), feedType = FeedType.BREAST, updatedAtMillis = at(14, 0),
        ).normalized()
        val legacyEnded = Entry(
            id = "legacy-ended", kind = EntryKind.FEED, startMillis = at(13, 50), endMillis = at(14, 5),
            feedType = FeedType.BREAST, breastSide = BreastSide.LEFT, updatedAtMillis = at(14, 5),
        ).normalized()
        val s = summarize(SampleData.scenario() + legacy + legacyEnded + Entry.sleep(at(14, 30), id = "nap"), NOW_1450, ZONE)
        assertTrue(lampState(s) is LampState.SleepRunning)
        assertEquals("legacy", s.lastFeed?.id)
        assertNull(legacyEnded.endMillis)
        val marks = s.ribbon(TODAY).breastfeeds.map { it.entry.id }
        assertTrue(marks.containsAll(listOf("legacy", "legacy-ended")))
    }

    private fun feedId() = SampleData.recentBreastfeed().id
}

class SummaryEdgeTest {
    @Test
    fun emptyLog() {
        val summary = summarize(emptyList(), NOW_1432, ZONE)
        assertNull(summary.lastFeed)
        assertNull(summary.lastDiaper)
        assertNull(summary.lastSleepEnd)
        assertEquals(DayCounts(0, 0, 0, 0), summary.today)
        assertEquals(LampState.FeedIdle(null, null), lampState(summary))
    }

    @Test
    fun deletedAndFutureEntriesAreIgnored() {
        val entries = listOf(
            Entry.feed(FeedType.FORMULA, at(12, 0), id = "a"),
            Entry.feed(FeedType.FORMULA, at(13, 0), id = "b").copy(deleted = true),
            Entry.feed(FeedType.FOOD, at(15, 0), id = "c"),
        )
        val summary = summarize(entries, NOW_1432, ZONE)
        assertEquals("a", summary.lastFeed?.id)
        assertEquals(1, summary.today.feeds)
    }

    @Test
    fun dayBoundsFollowTheZone() {
        val utc = ZoneId.of("UTC")
        val bounds = DayBounds.of(TODAY, utc)
        assertEquals(24 * 60 * MINUTE, bounds.lengthMillis)
        assertTrue(bounds.startMillis in bounds)
        assertFalse(bounds.endMillis in bounds)
        assertEquals(TODAY, DayBounds.containing(NOW_1432, ZONE).day)
    }

    @Test
    fun emberAngle() {
        assertEquals(0f, emberAngleDegrees(0))
        assertEquals(354f, emberAngleDegrees(59))
        assertEquals(0f, emberAngleDegrees(60))
        assertEquals(42f, emberAngleDegrees(67))
    }
}

/**
 * Real taps carry seconds. Every displayed duration counts clock minutes, so it agrees with the HH:mm times
 * printed beside it (13:44 → 14:32 is 48m, never 47m).
 */
class OffMinuteStampsTest {
    private val second = 1_000L

    @Test
    fun timeSinceAgreesWithTheClockTimes() {
        val pee = Entry.diaper(at(13, 44) + 30 * second, pee = true, poop = false, id = "pee")
        val formula = Entry.feed(FeedType.FORMULA, at(12, 22) + 50 * second, id = "formula")
        val sleep = Entry.sleep(at(12, 40) + 50 * second, at(13, 27) + 10 * second, id = "sleep")
        val now = at(14, 32) + 10 * second
        val summary = summarize(listOf(pee, formula, sleep), now, ZONE)
        assertEquals(48 * MINUTE, summary.sinceLastDiaperMillis)
        assertEquals(130 * MINUTE, summary.sinceLastFeedMillis)
        assertEquals(65 * MINUTE, summary.awakeMillis)
        assertEquals(47L, summary.today.sleepMinutes)
        assertEquals(130L, lampState(summary).elapsedMinutes)
    }

    @Test
    fun aRunningSleepAndItsEmberAgreeWithSince0205() {
        val sleep = Entry.sleep(at(2, 5) + 40 * second, id = "sleep")
        val state = lampState(summarize(listOf(sleep), at(3, 12) + 20 * second, ZONE)) as LampState.SleepRunning
        assertEquals(67L, state.elapsedMinutes)
        assertEquals(42f, emberAngleDegrees(state.elapsedMinutes))
    }

    @Test
    fun clockMinutes() {
        assertEquals(1L, com.ffmathy.pooparazzi.core.model.clockMinutesBetween(at(14, 31) + 59 * second, at(14, 32)))
        assertEquals(0L, com.ffmathy.pooparazzi.core.model.clockMinutesBetween(at(14, 32), at(14, 32) + 59 * second))
        assertEquals(0L, com.ffmathy.pooparazzi.core.model.clockMinutesBetween(at(14, 32), at(14, 30)))
        // Before 1970 (negative epoch) the minute floor still works.
        assertEquals(1L, com.ffmathy.pooparazzi.core.model.clockMinutesBetween(-61 * second, -59 * second))
    }
}

/** DST days in Copenhagen: Sun 29 Mar 2026 has 23 hours, Sun 25 Oct 2026 has 25. Sleep minutes are real minutes. */
class DstSleepMinutesTest {
    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    @Test
    fun springForward() {
        val sunday = day(3, 29)
        val sleep = Entry.sleep(epochMillis(sunday.minusDays(1), 23, 30, ZONE), epochMillis(sunday, 4, 0, ZONE), id = "s")
        val summary = summarize(listOf(sleep), epochMillis(sunday, 12, 0, ZONE), ZONE)
        assertEquals("00:00–04:00 is 3 real hours", 180L, summary.today.sleepMinutes)
        assertEquals(30L, summary.dayCounts(sunday.minusDays(1)).sleepMinutes)
    }

    @Test
    fun fallBack() {
        val sunday = day(10, 25)
        val sleep = Entry.sleep(epochMillis(sunday.minusDays(1), 23, 30, ZONE), epochMillis(sunday, 4, 0, ZONE), id = "s")
        val summary = summarize(listOf(sleep), epochMillis(sunday, 12, 0, ZONE), ZONE)
        assertEquals("00:00–04:00 is 5 real hours", 300L, summary.today.sleepMinutes)
    }

    @Test
    fun aRunningSleepAcrossMidnightCountsOnBothDays() {
        val sunday = day(10, 25)
        val sleep = Entry.sleep(epochMillis(sunday.minusDays(1), 22, 0, ZONE), id = "s")
        val summary = summarize(listOf(sleep), epochMillis(sunday, 1, 15, ZONE), ZONE)
        assertEquals(75L, summary.today.sleepMinutes)
        assertEquals(120L, summary.dayCounts(sunday.minusDays(1)).sleepMinutes)
        assertEquals(195L, (lampState(summary) as LampState.SleepRunning).elapsedMinutes)
    }
}
