package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import com.ffmathy.pooparazzi.phone.ui.home.GuardKind
import com.ffmathy.pooparazzi.phone.ui.home.NoticeVariant
import com.ffmathy.pooparazzi.phone.ui.home.ReceiptVariant
import com.ffmathy.pooparazzi.phone.ui.home.HomeHaptic
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.OffsetChipUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Sleep toggle, its 2 s guard and 60 s discard, the receipt sheet (offsets, Undo, End sleep, Edit, no timeout),
 * the notices, Adjust start and cooldowns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeSleepAndReceiptTest : HomeViewModelTestBase() {

    @Test
    fun startSleepRunsASleepAndShowsTheReceipt() = homeTest { home ->
        home.tapSleepToggle()
        val state = home.settle()
        val sleep = state.runningSleep!!
        assertEquals(SampleData.NOW_1432, sleep.startMillis)
        assertEquals(ReceiptVariant.SleepStarted, state.receipt.variant)
        assertEquals(SampleData.NOW_1432, state.receipt.time)
        assertTrue(state.lamp is LampState.SleepRunning)
        assertEquals(listOf(HomeHaptic.TOGGLE_ON), home.haptics)
    }

    @Test
    fun aSecondToggleWithinTwoSecondsIsGuarded() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.closeReceipt()
        home.clock.advanceBy(700)
        home.tapSleepToggle()
        var state = home.settle()
        assertTrue("still asleep", state.sleepRunning)
        assertNull("no new receipt", state.sheet)
        assertEquals(NoticeVariant.Guard(GuardKind.ASLEEP, SampleData.NOW_1432), state.card!!.variant)
        assertEquals(listOf(HomeHaptic.TOGGLE_ON, HomeHaptic.REJECT), home.haptics)

        home.scope.testScheduler.advanceTimeBy(2_100)
        state = home.settle()
        assertNull("the notice lasts 2 s", state.card)
    }

    @Test
    fun wokeUpEndsTheSleepAndOffsetsMoveTheEndAtOnce() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 25)).getOrThrow()
        home.tapSleepToggle()
        var state = home.settle()
        assertEquals(SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
        assertEquals(ReceiptVariant.WokeUp(67 * MINUTE), state.receipt.variant)
        assertEquals(HomeHaptic.TOGGLE_OFF, home.haptics.last())

        home.viewModel.onSheetOffset(-10)
        state = home.settle()
        assertEquals(SampleData.at(14, 22), home.repository.get(sleep.id)!!.endMillis)
        assertEquals(ReceiptVariant.WokeUp(57 * MINUTE), state.receipt.variant)
        assertEquals(SampleData.at(14, 22), state.receipt.time)
        assertEquals(HomeHaptic.SEGMENT_TICK, home.haptics.last())
    }

    @Test
    fun endOffsetsBeforeTheStartAreDisabled() = homeTest { home ->
        home.repository.startSleep(SampleData.at(14, 24)).getOrThrow()
        home.tapSleepToggle()
        assertEquals(
            listOf(OffsetChipUi(-15, false), OffsetChipUi(-10, false), OffsetChipUi(-5, true), OffsetChipUi(5, false)),
            home.settle().receipt.chips,
        )
        home.viewModel.onSheetOffset(-10)
        assertEquals(0, home.settle().receipt.offsetMinutes)
        assertEquals(HomeHaptic.REJECT, home.haptics.last())
    }

    @Test
    fun aSleepStoppedWithinAMinuteIsCancelledAndUndoResumesIt() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.closeReceipt()
        home.scope.testScheduler.advanceTimeBy(2_100)
        home.clock.advanceBy(40_000)
        home.tapSleepToggle()
        var state = home.settle()
        assertEquals(ReceiptVariant.SleepCancelled, state.receipt.variant)
        assertTrue("nothing to move", state.receipt.chips.isEmpty())
        assertFalse(state.sleepRunning)
        assertTrue(home.entries().none { it.isSleep })

        home.viewModel.onUndo()
        state = home.settle()
        assertNull(state.sheet)
        assertEquals(NoticeVariant.Undone, state.card!!.variant)
        assertTrue(state.runningSleep!!.isRunning)
        assertEquals(SampleData.NOW_1432, state.runningSleep!!.startMillis)
    }

    @Test
    fun undoRemovesALogAndUndoneLastsTwoSeconds() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        assertEquals(1, home.entries().size)

        home.viewModel.onUndo()
        var state = home.settle()
        assertTrue(home.entries().isEmpty())
        assertNull(state.sheet)
        assertEquals(NoticeVariant.Undone, state.card!!.variant)
        assertEquals(HomeHaptic.REJECT, home.haptics.last())

        home.scope.testScheduler.advanceTimeBy(2_100)
        state = home.settle()
        assertNull(state.card)
    }

    @Test
    fun aLogFromASheetShowsNoReceipt() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onSheetOffset(-5)
        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperSave()
        val state = home.settle()
        assertNull("the sheet already picked the time", state.sheet)
        assertNull(state.card)
        assertEquals(SampleData.NOW_1432 - 5 * MINUTE, home.entries().single().startMillis)
        assertEquals(HomeHaptic.CONFIRM, home.haptics.last())
    }

    @Test
    fun receiptNudgesAddUpFromTheBaseAndShowWhatItWas() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        suspend fun startTime() = home.entries().single().startMillis

        home.viewModel.onSheetOffset(-5)
        home.settle()
        assertEquals(SampleData.NOW_1432 - 5 * MINUTE, startTime())
        home.viewModel.onSheetOffset(-15)
        home.settle()
        assertEquals("taps add up", SampleData.NOW_1432 - 20 * MINUTE, startTime())
        home.viewModel.onSheetOffset(5)
        val state = home.settle()
        assertEquals(SampleData.NOW_1432 - 15 * MINUTE, startTime())
        assertEquals(SampleData.NOW_1432, state.receipt.was)
    }

    @Test
    fun theReceiptHasNoTimeoutAndDoneKeepsTheLog() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.viewModel.onSheetOffset(-10)
        home.scope.testScheduler.advanceTimeBy(10 * MINUTE)
        assertEquals("still open", ReceiptVariant.SleepStarted, home.settle().receipt.variant)

        home.viewModel.onReceiptDone()
        val state = home.settle()
        assertNull(state.sheet)
        assertNull("no card after a receipt", state.card)
        assertEquals(SampleData.NOW_1432 - 10 * MINUTE, home.entries().single().startMillis)
    }

    @Test
    fun dismissingTheReceiptKeepsTheLogAsShown() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.viewModel.onSheetOffset(-5)
        home.viewModel.onSheetDismiss()
        assertNull(home.settle().sheet)
        assertEquals(SampleData.NOW_1432 - 5 * MINUTE, home.entries().single().startMillis)
    }

    @Test
    fun editInTheReceiptClosesItAndOpensTheEditor() = homeTest { home ->
        home.tapSleepToggle()
        val receipt = home.settle().receipt
        var editorOpened = false
        home.viewModel.onSheetEdit(receipt.editEntryId!!) { editorOpened = true }
        home.settle()
        assertTrue(editorOpened)
        assertNull(home.settle().sheet)
    }

    @Test
    fun aWidgetDiaperLoggedDuringASleepCanEndTheSleepAtItsTime() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 0)).getOrThrow()
        val poop = home.repository.logDiaper(SampleData.at(14, 27), pee = false, poop = true)
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.LOGGED, poop.id))
        var state = home.settle()
        assertEquals(true, state.receipt.endSleep)

        home.viewModel.onEndSleep()
        state = home.settle()
        assertEquals(SampleData.at(14, 27), home.repository.get(sleep.id)!!.endMillis)
        assertEquals(ReceiptVariant.WokeUp(87 * MINUTE), state.receipt.variant)
        assertNull("End sleep is done", state.receipt.endSleep)
        assertTrue(state.receipt.chips.isNotEmpty())
    }

    @Test
    fun adjustStartMovesTheRunningStartOnSaveAndPlusFiveNeedsRoom() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(14, 30)).getOrThrow()
        home.viewModel.onAdjustStart()
        var state = home.settle()
        val sheet = state.sheet as HomeSheetUi.Adjust
        assertEquals(sleep.id, sheet.entryId)
        assertEquals(
            listOf(OffsetChipUi(-15, true), OffsetChipUi(-10, true), OffsetChipUi(-5, true), OffsetChipUi(5, false)),
            sheet.chips,
        )

        home.viewModel.onSheetOffset(-15)
        home.scope.testScheduler.advanceTimeBy(60_000)
        state = home.settle()
        assertEquals("nothing moves before Save", SampleData.at(14, 30), state.runningSleep!!.startMillis)
        assertEquals(SampleData.at(14, 15), state.sheet!!.time)
        home.viewModel.onAdjustSave()
        state = home.settle()
        assertNull(state.sheet)
        assertEquals(SampleData.at(14, 15), state.runningSleep!!.startMillis)
        assertTrue("the timer keeps running", state.runningSleep!!.isRunning)
        assertEquals(HomeHaptic.CONFIRM, home.haptics.last())
    }

    @Test
    fun cancellingAdjustStartMovesNothing() = homeTest { home ->
        home.repository.startSleep(SampleData.at(14, 0)).getOrThrow()
        home.viewModel.onAdjustStart()
        home.viewModel.onSheetOffset(-10)
        home.viewModel.onSheetDismiss()
        val state = home.settle()
        assertNull(state.sheet)
        assertEquals(SampleData.at(14, 0), state.runningSleep!!.startMillis)
    }

    @Test
    fun editInAdjustStartSavesTheOffsetFirst() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(14, 0)).getOrThrow()
        home.viewModel.onAdjustStart()
        home.viewModel.onSheetOffset(-5)
        var editorOpened = false
        home.viewModel.onSheetEdit(sleep.id) { editorOpened = true }
        home.settle()
        assertTrue(editorOpened)
        assertNull(home.settle().sheet)
        assertEquals(SampleData.at(13, 55), home.repository.get(sleep.id)!!.startMillis)
    }

    @Test
    fun adjustStartTargetsTheRunningSleepNeverABreastFeed() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 0)).getOrThrow()
        home.repository.logFeed(FeedType.BREAST, SampleData.at(14, 20))
        home.viewModel.onAdjustStart()
        assertEquals(sleep.id, (home.settle().sheet as HomeSheetUi.Adjust).entryId)
    }

    @Test
    fun adjustStartDoesNothingWithoutARunningSleep() = homeTest { home ->
        home.repository.logFeed(FeedType.BREAST, SampleData.at(14, 20))
        home.viewModel.onAdjustStart()
        assertNull(home.settle().sheet)
    }

    @Test
    fun homeIgnoresTapsWhileAReceiptIsOpenAndForSixHundredMillisAfterDone() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.viewModel.onDiaper()
        assertTrue("Diaper ignored under the receipt", home.settle().sheet is HomeSheetUi.Receipt)

        home.viewModel.onReceiptDone()
        home.settle()
        home.viewModel.onSheetClosed()
        home.settle()
        home.scope.testScheduler.advanceTimeBy(300)
        home.viewModel.onDiaper()
        home.tapSleepToggle()
        var state = home.settle()
        assertNull("Diaper ignored", state.sheet)
        assertTrue("Sleep toggle ignored", state.sleepRunning)

        home.scope.testScheduler.advanceTimeBy(400)
        home.viewModel.onDiaper()
        state = home.settle()
        assertTrue(state.sheet is HomeSheetUi.Diaper)
    }

    @Test
    fun aCancelledSheetHasNoCooldown() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onSheetDismiss()
        home.viewModel.onSheetClosed()
        home.tapFeed()
        assertTrue(home.settle().sheet is HomeSheetUi.Feed)
    }

    @Test
    fun theReferenceScenarioDrivesHome() = homeTest { home ->
        home.seed(SampleData.scenario())
        val state = home.settle()
        val lamp = state.lamp as LampState.FeedIdle
        assertEquals("sample-formula-1222", lamp.lastFeed!!.id)
        assertEquals(130 * MINUTE, lamp.sinceMillis)
        assertEquals(5, state.today.feeds)
        assertEquals(4, state.today.pee)
        assertEquals(2, state.today.poop)
        assertEquals(7 * 60L + 57, state.today.sleepMinutes)
        assertEquals("Noa", state.babyName)
        assertEquals(listOf<Entry>(), home.entries().filter { it.isRunning })
    }
}
