package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.home.CardAction
import com.ffmathy.pooparazzi.phone.ui.home.CardRow
import com.ffmathy.pooparazzi.phone.ui.home.CardVariant
import com.ffmathy.pooparazzi.phone.ui.home.GuardKind
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

/** The Sleep toggle, its 2 s guard and 60 s discard, the undo card's offsets and timing, Adjust start and cooldowns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeSleepAndCardTest : HomeViewModelTestBase() {

    @Test
    fun startSleepRunsASleepAndShowsTheCard() = homeTest { home ->
        home.tapSleepToggle()
        val state = home.settle()
        val sleep = state.runningSleep!!
        assertEquals(SampleData.NOW_1432, sleep.startMillis)
        assertEquals(CardVariant.SleepStarted, state.card!!.variant)
        assertTrue(state.lamp is LampState.SleepRunning)
        assertEquals(listOf(HomeHaptic.TOGGLE_ON), home.haptics)
    }

    @Test
    fun aSecondToggleWithinTwoSecondsIsGuarded() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.scope.testScheduler.advanceTimeBy(1_500)
        home.clock.advanceBy(1_500)
        home.tapSleepToggle()
        var state = home.settle()
        assertTrue("still asleep", state.sleepRunning)
        assertEquals(CardRow.Guard(GuardKind.ASLEEP, SampleData.NOW_1432), state.card!!.row)
        assertEquals(CardVariant.SleepStarted, state.card!!.variant)
        assertEquals(listOf(HomeHaptic.TOGGLE_ON, HomeHaptic.REJECT), home.haptics)

        home.scope.testScheduler.advanceTimeBy(2_100)
        state = home.settle()
        assertTrue("the guard line lasts 2 s", state.card!!.row is CardRow.Earlier)
    }

    @Test
    fun wokeUpEndsTheSleepAndOffsetsMoveTheEnd() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 25)).getOrThrow()
        home.tapSleepToggle()
        var state = home.settle()
        assertEquals(SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardVariant.WokeUp(67 * MINUTE), state.card!!.variant)
        assertEquals(HomeHaptic.TOGGLE_OFF, home.haptics.last())

        home.viewModel.onCardOffset(-10)
        state = home.settle()
        assertEquals(SampleData.at(14, 22), home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardVariant.WokeUp(57 * MINUTE), state.card!!.variant)
        assertEquals(HomeHaptic.SEGMENT_TICK, home.haptics.last())
    }

    @Test
    fun endOffsetsBeforeTheStartAreDisabled() = homeTest { home ->
        home.repository.startSleep(SampleData.at(14, 24)).getOrThrow()
        home.tapSleepToggle()
        val row = home.settle().card!!.row as CardRow.Earlier
        assertEquals(
            listOf(OffsetChipUi(-5, false, true), OffsetChipUi(-10, false, false), OffsetChipUi(-15, false, false)),
            row.chips,
        )
        home.viewModel.onCardOffset(-10)
        home.settle()
        assertEquals(HomeHaptic.REJECT, home.haptics.last())
    }

    @Test
    fun aSleepStoppedWithinAMinuteIsCancelledAndUndoResumesIt() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.scope.testScheduler.advanceTimeBy(2_100)
        home.clock.advanceBy(40_000)
        home.tapSleepToggle()
        var state = home.settle()
        assertEquals(CardVariant.SleepCancelled, state.card!!.variant)
        assertEquals(CardRow.None, state.card!!.row)
        assertFalse(state.sleepRunning)
        assertTrue(home.entries().none { it.isSleep })

        home.viewModel.onUndo()
        state = home.settle()
        assertEquals(CardVariant.Undone, state.card!!.variant)
        assertEquals(CardAction.NONE, state.card!!.action)
        assertTrue(state.runningSleep!!.isRunning)
        assertEquals(SampleData.NOW_1432, state.runningSleep!!.startMillis)
    }

    @Test
    fun undoRemovesALogAndUndoneLastsTwoSeconds() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperSave()
        home.settle()
        assertEquals(1, home.entries().size)

        home.viewModel.onUndo()
        var state = home.settle()
        assertTrue(home.entries().isEmpty())
        assertEquals(CardVariant.Undone, state.card!!.variant)
        assertEquals(HomeHaptic.REJECT, home.haptics.last())

        home.scope.testScheduler.advanceTimeBy(2_100)
        state = home.settle()
        assertNull(state.card)
    }

    @Test
    fun offsetsAreRelativeToTheBaseAndRetappingClears() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.FOOD)
        home.settle()
        suspend fun feedTime() = home.entries().single().startMillis

        home.viewModel.onCardOffset(-5)
        home.settle()
        assertEquals(SampleData.NOW_1432 - 5 * MINUTE, feedTime())
        home.viewModel.onCardOffset(-15)
        home.settle()
        assertEquals("never cumulative", SampleData.NOW_1432 - 15 * MINUTE, feedTime())
        home.viewModel.onCardOffset(-15)
        val state = home.settle()
        assertEquals("re-tapping the selected chip returns to 0", SampleData.NOW_1432, feedTime())
        assertTrue((state.card!!.row as CardRow.Earlier).chips.none { it.selected })
    }

    @Test
    fun theCardExpiresAfterTenSecondsAndChipTapsRestartTheWindow() = homeTest { home ->
        home.tapSleepToggle()
        var state = home.settle()
        val windowKey = state.card!!.windowKey
        home.scope.testScheduler.advanceTimeBy(8_000)
        home.viewModel.onCardOffset(-5)
        state = home.settle()
        assertEquals(windowKey + 1, state.card!!.windowKey)
        home.scope.testScheduler.advanceTimeBy(8_000)
        assertTrue("restarted by the chip tap", home.settle().card != null)
        home.scope.testScheduler.advanceTimeBy(2_100)
        assertNull(home.settle().card)
    }

    @Test
    fun aDiaperLoggedDuringASleepCanEndTheSleepAtItsTime() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 0)).getOrThrow()
        home.viewModel.onDiaper()
        home.viewModel.onSheetOffset(-5)
        home.viewModel.onDiaperPoop(true)
        home.viewModel.onDiaperSave()
        var state = home.settle()
        val row = state.card!!.row as CardRow.RunningSleep
        assertTrue(row.endSleepEnabled)
        assertEquals(listOf(OffsetChipUi(-5, true, true), OffsetChipUi(-10, false, true)), row.chips)

        home.viewModel.onCardEndSleep()
        state = home.settle()
        assertEquals(SampleData.at(14, 27), home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardVariant.WokeUp(87 * MINUTE), state.card!!.variant)
        assertTrue(state.card!!.row is CardRow.Earlier)
    }

    @Test
    fun adjustStartMovesTheRunningStartAndPlusFiveNeedsRoom() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(14, 30)).getOrThrow()
        home.viewModel.onAdjustStart()
        var state = home.settle()
        assertEquals(CardVariant.AdjustStart, state.card!!.variant)
        assertEquals(CardAction.DONE, state.card!!.action)
        val row = state.card!!.row as CardRow.Adjust
        assertEquals(sleep.id, row.editEntryId)
        assertEquals(
            listOf(OffsetChipUi(-15, false, true), OffsetChipUi(-5, false, true), OffsetChipUi(5, false, false)),
            row.chips,
        )

        home.viewModel.onCardOffset(-15)
        state = home.settle()
        assertEquals(SampleData.at(14, 15), state.runningSleep!!.startMillis)
        assertTrue("the timer keeps running", state.runningSleep!!.isRunning)

        home.viewModel.onCardDone()
        assertNull(home.settle().card)
    }

    @Test
    fun adjustStartTargetsTheRunningSleepNeverABreastFeed() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 0)).getOrThrow()
        home.repository.logFeed(com.ffmathy.pooparazzi.core.model.FeedType.BREAST, SampleData.at(14, 20))
        home.viewModel.onAdjustStart()
        val card = home.settle().card!!
        assertEquals(CardVariant.AdjustStart, card.variant)
        assertEquals(sleep.id, (card.row as CardRow.Adjust).editEntryId)
    }

    @Test
    fun adjustStartDoesNothingWithoutARunningSleep() = homeTest { home ->
        home.repository.logFeed(com.ffmathy.pooparazzi.core.model.FeedType.BREAST, SampleData.at(14, 20))
        home.viewModel.onAdjustStart()
        assertNull(home.settle().card)
    }

    @Test
    fun homeIgnoresTapsForSixHundredMillisAfterACommittedSheetCloses() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperSave()
        home.settle()
        home.viewModel.onSheetClosed()
        home.settle()

        home.scope.testScheduler.advanceTimeBy(300)
        home.viewModel.onDiaper()
        home.tapSleepToggle()
        var state = home.settle()
        assertNull("Diaper ignored", state.sheet)
        assertFalse("Sleep toggle ignored", state.sleepRunning)

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
    fun aGuardWithoutACardShowsANotice() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.viewModel.onCardDone() // not an Adjust card: Done is not offered, but a dismissed card is the same case
        home.settle()
        home.scope.testScheduler.advanceTimeBy(500)
        home.tapSleepToggle()
        val card = home.settle().card!!
        assertEquals(CardVariant.Notice(GuardKind.ASLEEP), card.variant)
        assertEquals(CardRow.Guard(GuardKind.ASLEEP, SampleData.NOW_1432), card.row)
        assertEquals(CardAction.NONE, card.action)
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
