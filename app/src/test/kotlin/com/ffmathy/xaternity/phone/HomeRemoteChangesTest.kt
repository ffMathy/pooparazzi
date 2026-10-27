package com.ffmathy.xaternity.phone

import android.app.Application
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.model.isSleep
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.phone.ui.home.CardRow
import com.ffmathy.xaternity.phone.ui.home.CardVariant
import com.ffmathy.xaternity.phone.ui.home.GuardKind
import com.ffmathy.xaternity.phone.ui.home.HomeHaptic
import com.ffmathy.xaternity.phone.ui.home.HomeSheetUi
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
 * Entries written elsewhere (the watch, a widget, the lock-screen notification) while Home is open: they show at once,
 * also within the current minute, and a button tapped on a state that has just changed elsewhere shows the guard
 * instead of undoing the other device's log (DESIGN_SPEC §4.4 "Idempotent sleep toggles").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeRemoteChangesTest : HomeViewModelTestBase() {

    @Test
    fun aSleepStartedElsewhereThisMinuteShowsAtOnce() = homeTest { home ->
        home.settle()
        // 20 s into the minute the widget (or the watch) starts a sleep; Home's minute tick has not come yet.
        home.clock.advanceBy(20_000)
        home.repository.startSleep(home.clock.now()).getOrThrow()
        val state = home.settle()
        assertNotNull("the running sleep shows without waiting for the next minute", state.runningSleep)
        assertTrue(state.sleepRunning)
    }

    @Test
    fun startSleepOnAStaleButtonShowsTheGuardAndKeepsTheOtherSleep() = homeTest { home ->
        home.settle()
        home.clock.advanceBy(20_000)
        val elsewhere = home.repository.startSleep(home.clock.now()).getOrThrow()
        home.clock.advanceBy(20_000)
        // The parent taps what the button showed a moment ago: "Start sleep".
        home.viewModel.onSleepToggle(shownRunning = false)
        val state = home.settle()
        val stored = home.repository.get(elsewhere.id)!!
        assertFalse("the other device's sleep is not cancelled", stored.deleted)
        assertTrue(stored.isRunning)
        assertEquals(CardRow.Guard(GuardKind.ASLEEP, elsewhere.startMillis), state.card!!.row)
        assertEquals(listOf(HomeHaptic.REJECT), home.haptics)
    }

    @Test
    fun wokeUpOnAStaleButtonShowsTheAwakeGuardAndStartsNothing() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 25)).getOrThrow()
        assertTrue(home.settle().sleepRunning)
        // The watch ends the sleep: the end arrives through sync.
        home.repository.applyRemote(sleep.copy(endMillis = SampleData.NOW_1432, updatedAtMillis = SampleData.NOW_1432 + 1))
        home.clock.advanceBy(10_000)
        home.viewModel.onSleepToggle(shownRunning = true)
        val state = home.settle()
        assertTrue("no new sleep", home.entries().none { it.isSleep && it.isRunning })
        assertEquals(SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardRow.Guard(GuardKind.AWAKE, SampleData.NOW_1432), state.card!!.row)
        assertEquals(listOf(HomeHaptic.REJECT), home.haptics)
    }

    @Test
    fun theTogglesStillWorkWhenTheButtonMatches() = homeTest { home ->
        home.viewModel.onSleepToggle(shownRunning = false)
        assertTrue(home.settle().sleepRunning)
        home.scope.testScheduler.advanceTimeBy(2_100)
        home.clock.advanceBy(5 * MINUTE)
        home.viewModel.onSleepToggle(shownRunning = true)
        val state = home.settle()
        assertFalse(state.sleepRunning)
        assertEquals(CardVariant.WokeUp(5 * MINUTE), state.card!!.variant)
    }

    @Test
    fun feedOnAStaleButtonShowsTheFeedingGuardInsteadOfTheSideSheet() = homeTest { home ->
        home.settle()
        home.clock.advanceBy(20_000)
        val feed = home.repository.startBreastfeed(home.clock.now()).getOrThrow()
        home.viewModel.onFeed(shownRunning = false)
        val state = home.settle()
        assertNull("no sheet: the parent asked for Feed, not End feed", state.sheet)
        assertEquals(CardRow.Guard(GuardKind.FEEDING, feed.startMillis), state.card!!.row)
        assertEquals(listOf(HomeHaptic.REJECT), home.haptics)
    }

    @Test
    fun endFeedAfterTheFeedEndedElsewhereOpensNothing() = homeTest { home ->
        val feed = home.repository.startBreastfeed(SampleData.at(14, 0)).getOrThrow()
        assertNotNull(home.settle().runningFeed)
        home.repository.applyRemote(
            feed.copy(endMillis = SampleData.NOW_1432, breastSide = com.ffmathy.xaternity.core.model.BreastSide.LEFT, updatedAtMillis = SampleData.NOW_1432 + 1),
        )
        home.viewModel.onFeed(shownRunning = true)
        val state = home.settle()
        assertNull("no Feed sheet for a tap on End feed", state.sheet)
        assertEquals(listOf(HomeHaptic.REJECT), home.haptics)
    }

    @Test
    fun feedOnAnIdleButtonStillOpensTheFeedSheet() = homeTest { home ->
        home.viewModel.onFeed(shownRunning = false)
        assertTrue(home.settle().sheet is HomeSheetUi.Feed)
        home.viewModel.onSheetDismiss()
        home.seed(listOf(Entry.breastfeed(SampleData.at(14, 20), null, null, id = "running")))
        home.viewModel.onFeed(shownRunning = true)
        assertTrue(home.settle().sheet is HomeSheetUi.Side)
    }
}
