package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.phone.review.ReviewMemory
import com.ffmathy.pooparazzi.phone.review.ReviewRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** When "Enjoying Pooparazzi?" may show: only after a few days of real logging (DESIGN_SPEC §3.3.1). */
class ReviewRuleTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private val firstSeen = ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day = 24L * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000
    private val seen = ReviewMemory(firstSeenMillis = firstSeen)

    /** [perDay] diapers on each of [days] days from the first one. */
    private fun log(days: Int, perDay: Int, from: Long = firstSeen) =
        (0 until days).flatMap { d -> (0 until perDay).map { i -> Entry.diaper(from + d * day + i * hour, pee = true, poop = false) } }

    private fun asks(memory: ReviewMemory = seen, entries: List<Entry> = log(3, 5), now: Long = firstSeen + 3 * day) =
        ReviewRule.shouldAsk(memory, entries, now, zone)

    @Test fun asksAfterThreeDaysOfLogging() = assertTrue(asks())

    @Test fun notBeforeHomeWasSeen() = assertFalse(asks(memory = ReviewMemory()))

    @Test fun notBeforeThreeDays() = assertFalse(asks(now = firstSeen + 3 * day - 1))

    @Test fun notWithTooFewEntries() = assertFalse(asks(entries = log(3, 5).drop(1)))

    @Test fun notWhenAllWasLoggedOnTwoDays() = assertFalse(asks(entries = log(2, 10), now = firstSeen + 5 * day))

    @Test fun importedHistoryDoesNotCount() {
        // A month of entries from before the app was installed (an import, or the family's backup).
        val old = log(30, 5, from = firstSeen - 40 * day)
        assertFalse(asks(entries = old, now = firstSeen + 5 * day))
    }

    @Test fun deletedEntriesDoNotCount() = assertFalse(asks(entries = log(3, 5).map { it.copy(deleted = true) }))

    @Test fun neverAfterRatingOrDontAskAgain() = assertFalse(asks(memory = seen.copy(done = true)))

    @Test fun notNowWaitsAMonthThenAsksOnceMore() {
        val now = firstSeen + 3 * day
        val once = ReviewRule.later(seen, now)
        assertEquals(1, once.timesAsked)
        assertFalse(asks(memory = once, now = now + 29 * day))
        assertTrue(asks(memory = once, now = now + 30 * day))
        val twice = ReviewRule.later(once, now + 30 * day)
        assertFalse(asks(memory = twice, now = now + 365 * day))
    }
}
