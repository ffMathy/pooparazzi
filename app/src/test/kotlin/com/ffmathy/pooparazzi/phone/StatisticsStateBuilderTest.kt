package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.preview.StatisticsSamples
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsStateBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Statistics' states from the sample history (V1_ADDITIONS §8): the builder shared by the view model and previews. */
class StatisticsStateBuilderTest {
    @Test
    fun theDefaultIsPoopOverTheLastThirtyDays() {
        val state = StatisticsSamples.lastThirtyDays()
        assertEquals(SampleData.TODAY, state.today)
        assertEquals(StatsRange.Last30Days, state.report?.range)
        assertEquals(StatsMetric.POOP, state.metric)
        assertEquals(StatsMetric.POOP, state.chart?.metric)
        assertEquals("one bar per day", 30, state.chart?.bars?.size)
    }

    @Test
    fun aMetricChangeOnlyChangesTheChart() {
        val poop = StatisticsSamples.lastThirtyDays()
        val sleep = StatisticsStateBuilder.build(SampleData.history(), SampleData.NOW_1432, SampleData.ZONE, StatsMetric.SLEEP, StatsRange.Last30Days)
        assertEquals(poop.report, sleep.report)
        assertNotEquals(poop.chart, sleep.chart)
        assertEquals(StatsMetric.SLEEP, sleep.chart?.metric)
    }

    @Test
    fun theNightNumbersAreTheDayNumbers() {
        assertEquals(StatisticsSamples.lastThirtyDays().report, StatisticsSamples.night().report)
    }

    @Test
    fun beforeTheFirstFullDayThereIsNoReport() {
        listOf(SampleData.scenario(), emptyList<Entry>()).forEach { entries ->
            val state = StatisticsStateBuilder.build(entries, SampleData.NOW_1432, SampleData.ZONE, StatsMetric.POOP, StatsRange.Last30Days)
            assertNull(state.report)
            assertNull(state.chart)
            assertTrue(state.isEmpty)
        }
    }
}
