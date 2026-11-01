package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.ffmathy.pooparazzi.core.domain.DayCounts
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.history.HistoryUiState
import com.ffmathy.pooparazzi.phone.ui.history.HistoryViewModel
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** History's day switching, counts, list order and deletes with Undo (DESIGN_SPEC §3.3.3, V1_ADDITIONS §1, §6). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HistoryViewModelTest : CoreTestBase() {

    private fun CoreTestRig.history(saved: SavedStateHandle = SavedStateHandle()): HistoryViewModel =
        HistoryViewModel(repository, settings, clock, SampleData.ZONE, saved).also { collect(it.state) }

    private fun HistoryViewModel.current(): HistoryUiState = checkNotNull(state.value) { "History has not loaded" }

    @Test
    fun todayShowsTheReferenceDayNewestFirst() = coreTest { core ->
        val state = core.history().current()

        assertEquals(SampleData.TODAY, state.day)
        assertTrue(state.isToday)
        assertEquals(DayCounts(feeds = 5, pee = 4, poop = 2, sleepMinutes = 7 * 60 + 57), state.counts)
        assertEquals(15, state.entries.size)
        assertEquals("sample-pee-1344", state.entries.first().id)
        assertEquals("the sleep that began on Sunday is listed last", "sample-sleep-2240", state.entries.last().id)
        assertFalse("Next day is disabled on today", state.canGoForward)
        assertTrue(state.canGoBack)
        assertNotNull("today's ribbon has a now-line", state.ribbon.nowFraction)
        assertEquals(5, state.ribbon.sleeps.size)
        assertEquals(listOf("sample-breast-0140", "sample-breast-0800"), state.ribbon.breastfeeds.map { it.entry.id })
        assertEquals(2, state.ribbon.formula.size)
        assertEquals(1, state.ribbon.food.size)
        assertEquals(5, state.ribbon.diapers.size)
    }

    /** The settings and the baby's entries come as one: a switch never lists Noa's log under Ida's label. */
    @Test
    fun aSwitchNeverListsTheOtherBabysLog() = coreTest { core ->
        core.settings.ensureBabies(listOf(SampleData.TWIN_ID))
        val seen = core.collect(core.history().state)
        assertTrue(seen.last()!!.entries.isNotEmpty())
        core.settings.selectBaby(SampleData.TWIN_ID)
        runCurrent()
        val ida = seen.filterNotNull().filter { it.babyId == SampleData.TWIN_ID }
        assertTrue(ida.isNotEmpty())
        assertTrue("never Noa's entries under Ida's label", ida.all { it.entries.isEmpty() })
    }

    @Test
    fun previousDayShowsYesterdayAndNextDayComesBack() = coreTest { core ->
        val history = core.history()
        history.onPreviousDay()
        runCurrent()
        val yesterday = history.current()

        assertEquals(SampleData.YESTERDAY, yesterday.day)
        assertEquals("22:40 to midnight", DayCounts(feeds = 0, pee = 0, poop = 0, sleepMinutes = 80), yesterday.counts)
        assertEquals(listOf("sample-sleep-2240"), yesterday.entries.map { it.id })
        assertNull("no now-line on another day", yesterday.ribbon.nowFraction)
        assertTrue(yesterday.canGoForward)
        assertFalse("nothing was logged before Sunday", yesterday.canGoBack)

        history.onPreviousDay()
        runCurrent()
        assertEquals("History does not page into empty time", SampleData.YESTERDAY, history.current().day)

        history.onNextDay()
        runCurrent()
        assertEquals(SampleData.TODAY, history.current().day)
    }

    @Test
    fun nextDayDoesNothingOnToday() = coreTest { core ->
        val history = core.history()
        history.onNextDay()
        runCurrent()
        assertEquals(SampleData.TODAY, history.current().day)
    }

    @Test
    fun theDayShownSurvivesProcessDeath() = coreTest { core ->
        val saved = SavedStateHandle()
        core.history(saved).onPreviousDay()
        runCurrent()
        assertEquals(SampleData.YESTERDAY, core.history(saved).current().day)
    }

    @Test
    fun deleteRemovesTheRowAndUndoBringsItBack() = coreTest { core ->
        val history = core.history()
        history.onDelete("sample-pee-1344")
        runCurrent()
        val afterDelete = history.current()

        assertFalse(afterDelete.entries.any { it.id == "sample-pee-1344" })
        assertEquals(3, afterDelete.counts.pee)
        val receipt = checkNotNull(afterDelete.receipt)
        assertEquals("sample-pee-1344", receipt.entry.id)
        assertFalse("the receipt holds the entry as it was", receipt.entry.deleted)
        assertTrue("a soft delete", core.entry("sample-pee-1344")!!.deleted)

        history.onUndoDelete(receipt)
        runCurrent()
        val restored = history.current()
        assertTrue(restored.entries.any { it.id == "sample-pee-1344" })
        assertEquals(4, restored.counts.pee)
        assertNull(restored.receipt)
    }

    @Test
    fun aDismissedReceiptLeavesTheEntryDeleted() = coreTest { core ->
        val history = core.history()
        history.onDelete("sample-food-1030")
        runCurrent()
        history.onReceiptDismissed(checkNotNull(history.current().receipt))
        runCurrent()
        assertNull(history.current().receipt)
        assertEquals(4, history.current().counts.feeds)
    }

    @Test
    fun addPastEntryDefaultsToFifteenMinutesAgoOnTheDayShown() = coreTest { core ->
        val history = core.history()
        assertEquals(SampleData.NOW_1432 - 15 * MINUTE, history.addEntryDefaultTime())

        history.onPreviousDay()
        runCurrent()
        assertEquals(SampleData.at(14, 17, day = SampleData.YESTERDAY), history.addEntryDefaultTime())
    }

    @Test
    fun aRunningSleepIsListedAndCountedUpToNow() = coreTest(now = SampleData.NOW_0312, seed = SampleData.scenarioAt0312(SampleData.ZONE)) { core ->
        val state = core.history().current()
        assertEquals("sample-sleep-0205", state.entries.first().id)
        assertEquals(DayCounts(feeds = 1, pee = 1, poop = 0, sleepMinutes = 2 * 60 + 42), state.counts)
        assertTrue(state.ribbon.sleeps.last().running)
    }

    @Test
    fun anEntryLoggedElsewhereThisMinuteIsListedAtOnce() = coreTest { core ->
        val history = core.history()
        history.current()
        // 20 s into the minute the watch logs a diaper; History's minute tick has not come yet.
        core.clock.advanceBy(20_000)
        val diaper = core.repository.logDiaper(core.clock.now(), pee = true, poop = false, color = null, texture = null)
        runCurrent()
        assertEquals(diaper.id, history.current().entries.first().id)
    }
}
