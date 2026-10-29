package com.ffmathy.pooparazzi.core.sample

import com.ffmathy.pooparazzi.core.domain.dailyCounts
import com.ffmathy.pooparazzi.core.domain.epochMillis
import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.model.EntryKind
import com.ffmathy.pooparazzi.core.model.validate
import com.ffmathy.pooparazzi.core.sample.SampleData.NOW_1432
import com.ffmathy.pooparazzi.core.sample.SampleData.TODAY
import com.ffmathy.pooparazzi.core.sample.SampleData.YESTERDAY
import com.ffmathy.pooparazzi.core.sample.SampleData.ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SampleData.history]: weeks of plausible, valid logging that ends in the reference scenario. */
class SampleHistoryTest {
    private val history = SampleData.history()

    @Test
    fun itIsTheSameOnEveryRunAndEndsInTheScenario() {
        assertEquals(history, SampleData.history())
        assertEquals(SampleData.scenario(), history.takeLast(SampleData.scenario().size))
        val generated = history.dropLast(SampleData.scenario().size)
        assertTrue(generated.all { (it.endMillis ?: it.startMillis) <= epochMillis(YESTERDAY, 22, 30, ZONE) })
        assertEquals(TODAY.minusDays(34), localDate(history.minOf { it.startMillis }, ZONE))
    }

    @Test
    fun everyEntryIsValidWithAUniqueIdAndSleepsNeverOverlap() {
        history.forEach { assertNull(it.id, validate(it)) }
        assertEquals(history.size, history.map { it.id }.toSet().size)
        history.filter { it.kind == EntryKind.SLEEP }.sortedBy { it.startMillis }.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.id} overlaps ${b.id}", a.endMillis!! <= b.startMillis)
        }
    }

    @Test
    fun everyFullDayLooksLikeABabysDay() {
        val days = dailyCounts(history, TODAY.minusDays(33), YESTERDAY.minusDays(1), NOW_1432, ZONE)
        assertEquals(32, days.size)
        days.forEach { day ->
            assertTrue("$day", day.feeds in 5..12)
            assertTrue("$day", day.pee in 3..12)
            assertTrue("$day", day.poop in 0..6)
            assertTrue("$day", day.sleepMinutes in 10 * 60L..18 * 60L)
        }
        // Night sleeps grow and poops get rarer: the last week differs from the month.
        val month = days.takeLast(30)
        val week = days.takeLast(7)
        assertTrue(week.sumOf { it.sleepMinutes } / 7.0 > month.sumOf { it.sleepMinutes } / 30.0)
        assertTrue(week.sumOf { it.poop } / 7.0 < month.sumOf { it.poop } / 30.0)
    }
}
