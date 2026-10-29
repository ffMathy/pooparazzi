package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.home.NoticeVariant
import com.ffmathy.pooparazzi.phone.ui.home.ReceiptVariant
import com.ffmathy.pooparazzi.phone.ui.home.GuardKind
import com.ffmathy.pooparazzi.phone.ui.home.HomeHaptic
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
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
        assertEquals(NoticeVariant.Guard(GuardKind.ASLEEP, elsewhere.startMillis), state.card!!.variant)
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
        assertEquals(NoticeVariant.Guard(GuardKind.AWAKE, SampleData.NOW_1432), state.card!!.variant)
        assertEquals(listOf(HomeHaptic.REJECT), home.haptics)
    }

    @Test
    fun theTogglesStillWorkWhenTheButtonMatches() = homeTest { home ->
        home.viewModel.onSleepToggle(shownRunning = false)
        assertTrue(home.settle().sleepRunning)
        home.closeReceipt()
        home.scope.testScheduler.advanceTimeBy(2_100)
        home.clock.advanceBy(5 * MINUTE)
        home.viewModel.onSleepToggle(shownRunning = true)
        val state = home.settle()
        assertFalse(state.sleepRunning)
        assertEquals(ReceiptVariant.WokeUp(5 * MINUTE), state.receipt.variant)
    }

    @Test
    fun feedOpensTheFeedSheetWhateverWasLoggedElsewhere() = homeTest { home ->
        // A breast feed from the watch (or a "running" one synced from an older version) changes nothing for Feed.
        home.repository.applyRemote(Entry.breastfeed(SampleData.at(14, 20), id = "watch-feed"))
        assertFalse(home.settle().anythingRunning)
        home.viewModel.onFeed()
        val sheet = home.settle().sheet as HomeSheetUi.Feed
        assertFalse(sheet.breast)
        assertTrue(home.haptics.isEmpty())
    }
}
