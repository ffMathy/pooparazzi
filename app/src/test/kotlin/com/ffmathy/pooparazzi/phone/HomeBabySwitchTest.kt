package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import com.ffmathy.pooparazzi.phone.ui.home.CardVariant
import com.ffmathy.pooparazzi.phone.ui.home.HomeHaptic
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.HomeUiState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's Switch baby sheet (BABIES_AND_BACKUP §2.4): it opens only with two or more babies; selecting a baby shows and
 * logs that baby's log, closes the card and any sheet, and resets the double-toggle guard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeBabySwitchTest : HomeViewModelTestBase() {

    private suspend fun HomeTestRig.addTwin() {
        settings.ensureBabies(listOf(SampleData.TWIN_ID))
        settings.renameBaby(SampleData.TWIN_ID, SampleData.TWIN_NAME)
    }

    @Test
    fun withOneBabyTheSwitcherNeverOpens() = homeTest { home ->
        val state = home.settle()
        assertFalse(state.hasSeveralBabies)
        home.viewModel.onSwitchBaby()
        assertFalse(home.settle().switchingBaby)
        assertTrue(home.haptics.isEmpty())
    }

    /** The settings and the baby's entries come as one: a switch never shows Noa's log under Ida's name. */
    @Test
    fun aSwitchNeverShowsTheOtherBabysLogUnderTheNewName() = homeTest { home ->
        home.seed(SampleData.scenario(SampleData.ZONE))
        home.addTwin()
        assertNotNull(home.settle().row1)
        val seen = mutableListOf<HomeUiState>()
        backgroundScope.launch { home.viewModel.state.filterNotNull().collect { seen += it } }
        runCurrent()
        home.viewModel.onSwitchBaby()
        home.viewModel.onSelectBaby(SampleData.TWIN_ID)
        home.settle()
        val ida = seen.filter { it.babyId == SampleData.TWIN_ID }
        assertTrue(ida.isNotEmpty())
        assertTrue("never Noa's rows under Ida's name", ida.all { it.row1 == null && it.row2 == null })
    }

    @Test
    fun selectingABabyShowsItsLog() = homeTest { home ->
        home.seed(SampleData.scenario(SampleData.ZONE))
        home.addTwin()
        var state = home.settle()
        assertTrue(state.hasSeveralBabies)
        assertEquals(Baby.FIRST_ID, state.babyId)
        assertNotNull("Noa's log", state.row1)

        home.viewModel.onSwitchBaby()
        assertTrue(home.settle().switchingBaby)
        home.viewModel.onSelectBaby(SampleData.TWIN_ID)
        state = home.settle()
        assertFalse("the sheet closes", state.switchingBaby)
        assertEquals(SampleData.TWIN_ID, state.babyId)
        assertEquals(SampleData.TWIN_ID, home.settings.current().selectedBaby.id)
        assertNull("Ida has nothing logged", state.row1)
        assertNull(state.row2)
        assertEquals(listOf(HomeHaptic.SEGMENT_TICK), home.haptics)

        // Logs now go to Ida.
        home.viewModel.onFeed()
        home.viewModel.onFeedType(FeedType.FORMULA)
        home.settle()
        val feed = home.entries().single()
        assertEquals(FeedType.FORMULA, feed.feedType)
        assertEquals(SampleData.TWIN_ID, feed.babyId)
    }

    @Test
    fun switchingClosesTheCard() = homeTest { home ->
        home.addTwin()
        home.tapSleepToggle()
        assertEquals(CardVariant.SleepStarted, home.settle().card!!.variant)

        home.viewModel.onSwitchBaby()
        home.viewModel.onSelectBaby(SampleData.TWIN_ID)
        val state = home.settle()
        assertNull("the card's Undo belongs to Noa", state.card)
        assertNull("Ida is awake", state.runningSleep)
        assertTrue("Noa sleeps on", home.repository.observeAllEntries().first().any { it.isSleep && it.isRunning && it.babyId == Baby.FIRST_ID })
    }

    @Test
    fun aDeepLinkSheetClosesTheSwitcherAndSwitchingClosesTheSheet() = homeTest { home ->
        home.addTwin()
        home.viewModel.onSwitchBaby()
        home.viewModel.onLaunch(HomeLaunch.Diaper)
        var state = home.settle()
        assertFalse(state.switchingBaby)
        assertTrue(state.sheet is HomeSheetUi.Diaper)

        // With a sheet open the switcher does not open (as Feed, Diaper and the toggle).
        home.viewModel.onSwitchBaby()
        assertFalse(home.settle().switchingBaby)
        home.viewModel.onSheetDismiss()
        home.viewModel.onSheetClosed()
        home.viewModel.onSwitchBaby()
        assertTrue(home.settle().switchingBaby)
        // While the switcher is open, Feed does nothing.
        home.viewModel.onFeed()
        state = home.settle()
        assertNull(state.sheet)
        home.viewModel.onSwitchBabyDismiss()
        assertFalse(home.settle().switchingBaby)
        assertEquals(Baby.FIRST_ID, home.settings.current().selectedBaby.id)
    }

    @Test
    fun switchingResetsTheDoubleToggleGuard() = homeTest { home ->
        home.addTwin()
        home.tapSleepToggle()
        home.settle()
        home.viewModel.onSwitchBaby()
        home.viewModel.onSelectBaby(SampleData.TWIN_ID)
        home.settle()
        // Within 2 s of Noa's toggle, Ida's sleep still starts: twins can sleep at the same time.
        home.scope.testScheduler.advanceTimeBy(500)
        home.tapSleepToggle()
        val state = home.settle()
        assertEquals(CardVariant.SleepStarted, state.card!!.variant)
        assertEquals(SampleData.TWIN_ID, state.runningSleep!!.babyId)
        val running = home.repository.observeAllEntries().first().filter { it.isSleep && it.isRunning }
        assertEquals(setOf(Baby.FIRST_ID, SampleData.TWIN_ID), running.map { it.babyId }.toSet())
    }

    @Test
    fun aWidgetReceiptForAnotherBabyShowsNoCard() = homeTest { home ->
        home.addTwin()
        val sleep = home.repository.startSleep(SampleData.NOW_1432).getOrThrow()
        home.settings.selectBaby(SampleData.TWIN_ID)
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.SLEEP_STARTED, sleep.id))
        assertNull(home.settle().card)

        home.settings.selectBaby(Baby.FIRST_ID)
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.SLEEP_STARTED, sleep.id))
        assertEquals(CardVariant.SleepStarted, home.settle().card!!.variant)
    }
}
