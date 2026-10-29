package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.domain.StatsReport
import com.ffmathy.pooparazzi.core.domain.StatsWindow
import com.ffmathy.pooparazzi.core.domain.epochMillis
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsUiState
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Statistics' windows, ranges, open row and saved choices over a real log (V1_ADDITIONS §8). Unless noted the log is
 * `history(days = 10)`: first logged Thu 15 Oct, so the first full day is Fri 16 Oct, seen at Mon 26 Oct 14:32.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StatisticsViewModelTest : CoreTestBase() {
    private val tenDays = SampleData.history(days = 10)

    private fun CoreTestRig.statistics(saved: SavedStateHandle = SavedStateHandle()): StatisticsViewModel =
        StatisticsViewModel(repository, settings, clock, SampleData.ZONE, saved).also { collect(it.state) }

    private fun StatisticsViewModel.current(): StatisticsUiState = checkNotNull(state.value) { "Statistics has not loaded" }

    private fun StatisticsViewModel.report(): StatsReport = checkNotNull(current().report) { "Statistics has no report" }

    private fun oct(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    /** Tenths of feeds, pee and poop, and minutes of sleep: the four averages as shown. */
    private fun StatsWindow.averages(): List<Long?> = listOf(
        average(StatsMetric.FEEDS)?.tenths?.toLong(),
        average(StatsMetric.PEE)?.tenths?.toLong(),
        average(StatsMetric.POOP)?.tenths?.toLong(),
        average(StatsMetric.SLEEP)?.minutes,
    )

    @Test
    fun theDefaultIsTheLastThirtyDaysWithPoopOpen() = coreTest(seed = tenDays) { core ->
        val state = core.statistics().current()
        val report = checkNotNull(state.report)

        assertEquals(LocalDate.of(2026, 9, 26) to oct(25), report.window.from to report.window.to)
        assertEquals("10 of 30 days logged", 10 to 30, report.window.loggedDays to report.window.dayCount)
        assertEquals(listOf(70L, 68L, 26L, 822L), report.window.averages())
        assertEquals(StatsMetric.POOP, state.metric)
        assertEquals(StatsMetric.POOP, state.chart?.metric)
        assertEquals(StatsRange.Last30Days, report.range)
    }

    @Test
    fun choosingTheLastSevenDaysRecounts() = coreTest(seed = tenDays) { core ->
        val saved = SavedStateHandle()
        val statistics = core.statistics(saved)
        statistics.onRange(StatsRange.Last7Days)
        runCurrent()
        val report = statistics.report()

        assertEquals(StatsRange.Last7Days, report.range)
        assertEquals(oct(19) to oct(25), report.window.from to report.window.to)
        assertEquals(listOf(70L, 67L, 26L, 822L), report.window.averages())
        assertEquals(7, statistics.current().chart?.bars?.size)
        assertEquals("LAST_7_DAYS", saved.get<String>("statistics_range"))
        assertEquals(StatsRange.Last7Days, core.statistics(saved).report().range)
    }

    @Test
    fun choosingDatesRecounts() = coreTest(seed = tenDays) { core ->
        val statistics = core.statistics()
        statistics.onRange(StatsRange.Custom(oct(20), oct(22)))
        runCurrent()
        val report = statistics.report()

        assertEquals(StatsRange.Custom(oct(20), oct(22)), report.range)
        assertEquals(listOf(67L, 70L, 27L, 825L), report.window.averages())
    }

    @Test
    fun datesCoveringAllDaysSelectAllDays() = coreTest(seed = tenDays) { core ->
        val statistics = core.statistics()
        // 10 Oct is before the first full day, so the dates are cut to 16–25 Oct: every countable day.
        statistics.onRange(StatsRange.Custom(oct(10), oct(25)))
        runCurrent()
        val report = statistics.report()

        assertEquals(StatsRange.AllDays, report.range)
        assertEquals(oct(16) to oct(25), report.window.from to report.window.to)
    }

    @Test
    fun openingSleepShowsItsChart() = coreTest(seed = tenDays) { core ->
        val statistics = core.statistics()
        statistics.onMetric(StatsMetric.SLEEP)
        runCurrent()
        val state = statistics.current()

        assertEquals(StatsMetric.SLEEP, state.metric)
        assertEquals(StatsMetric.SLEEP, state.chart?.metric)
        assertEquals(30, state.chart?.bars?.size)
    }

    @Test
    fun theChoicesSurviveProcessDeath() = coreTest(seed = tenDays) { core ->
        val saved = SavedStateHandle()
        val first = core.statistics(saved)
        first.onMetric(StatsMetric.FEEDS)
        first.onRange(StatsRange.Custom(oct(20), oct(22)))
        runCurrent()
        assertEquals("CUSTOM", saved.get<String>("statistics_range"))

        val restored = core.statistics(saved).current()
        assertEquals(StatsMetric.FEEDS, restored.metric)
        assertEquals(StatsRange.Custom(oct(20), oct(22)), restored.report?.range)
    }

    @Test
    fun aBrokenSavedRangeFallsBackToThirtyDaysAndPoop() = coreTest(seed = tenDays) { core ->
        val saved = SavedStateHandle(mapOf("statistics_range" to "CUSTOM", "statistics_metric" to "NAPS"))
        val state = core.statistics(saved).current()

        assertEquals(StatsMetric.POOP, state.metric)
        assertEquals(StatsRange.Last30Days, state.report?.range)
    }

    @Test
    fun anEntryLoggedElsewhereIsCountedAtOnce() = coreTest(seed = tenDays) { core ->
        val statistics = core.statistics()
        assertEquals(26, statistics.report().window.average(StatsMetric.POOP)?.tenths)

        // A poop yesterday, synced from the watch; the minute tick has not come yet.
        core.repository.add(Entry.diaper(SampleData.at(9, 0, day = SampleData.YESTERDAY) + 30_000, pee = false, poop = true, id = "late-poop"))
        runCurrent()

        assertEquals(27, statistics.report().window.average(StatsMetric.POOP)?.tenths)
    }

    @Test
    fun todayIsNotCountedYet() = coreTest(seed = tenDays) { core ->
        val statistics = core.statistics()
        val before = statistics.report()

        core.repository.logDiaper(core.clock.now(), pee = true, poop = true, color = null, texture = null)
        runCurrent()

        val after = statistics.report()
        assertEquals(before.window, after.window)
    }

    @Test
    fun midnightMovesTheWindowsByADay() = coreTest(seed = tenDays) { core ->
        val statistics = core.statistics()
        statistics.current()

        core.clock.set(epochMillis(SampleData.TODAY.plusDays(1), 0, 5, SampleData.ZONE))
        statistics.onRange(StatsRange.Last7Days)
        runCurrent()

        val week = statistics.report().window
        assertEquals(oct(20) to oct(26), week.from to week.to)
        assertEquals(listOf(64L, 63L, 23L, 775L), week.averages())
    }

    @Test
    fun anEmptyLogLoadsTheEmptyState() = coreTest(seed = emptyList()) { core ->
        val state = core.statistics().current()
        assertNull(state.report)
        assertTrue(state.isEmpty)
    }

    @Test
    fun beforeAWholeDayItCountsTodaySoFar() = coreTest { core ->
        // The reference scenario starts Sun 22:40, so there is no whole day yet: Sun and today so far count.
        val report = checkNotNull(core.statistics().current().report)
        assertTrue(report.soFar)
        assertEquals(listOf(50L, 40L, 20L, 279L), report.window.averages())
    }

    /**
     * Only the current baby's log counts (BABIES_AND_BACKUP §2.4): Ida's three days never mix into Noa's statistics,
     * the label follows the baby, and a switch never counts Noa's log under Ida's label.
     */
    @Test
    fun onlyTheCurrentBabysLogCounts() = coreTest(seed = tenDays) { core ->
        core.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        core.settings.selectBaby(SampleData.TWIN_ID)
        core.seed(SampleData.history(days = 3).map { it.copy(id = "ida-${it.id}") })
        core.settings.selectBaby(Baby.FIRST_ID)
        runCurrent()

        val statistics = core.statistics()
        val seen = core.collect(statistics.state)
        runCurrent()
        val noa = statistics.current()
        assertTrue(noa.hasSeveralBabies)
        assertEquals(Baby.FIRST_ID, noa.baby.id)
        assertEquals("Noa's averages alone", listOf(70L, 68L, 26L, 822L), checkNotNull(noa.report).window.averages())
        assertEquals(10, noa.report!!.window.loggedDays)

        core.settings.selectBaby(SampleData.TWIN_ID)
        runCurrent()
        val ida = seen.filterNotNull().filter { it.babyId == SampleData.TWIN_ID }
        assertTrue(ida.isNotEmpty())
        assertTrue("never Noa's log under Ida's label", ida.all { it.report?.window?.loggedDays == 3 })
    }
}
