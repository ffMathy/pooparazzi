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

/**
 * The Sleep toggle, its 2 s guard and 60 s discard, the undo card and its timing, the Adjust time sheet (from the card
 * and from Adjust start) and cooldowns.
 */
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
        assertEquals("the guard line lasts 2 s", CardRow.AdjustTime, state.card!!.row)
    }

    @Test
    fun wokeUpEndsTheSleepAndOffsetsMoveTheEnd() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 25)).getOrThrow()
        home.tapSleepToggle()
        var state = home.settle()
        assertEquals(SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardVariant.WokeUp(67 * MINUTE), state.card!!.variant)
        assertEquals(HomeHaptic.TOGGLE_OFF, home.haptics.last())

        home.openAdjust()
        home.viewModel.onSheetOffset(-10)
        state = home.settle()
        assertEquals("nothing moves before Save", SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
        assertEquals(SampleData.at(14, 22), state.sheet!!.time)
        assertEquals(HomeHaptic.SEGMENT_TICK, home.haptics.last())

        home.viewModel.onAdjustSave()
        state = home.settle()
        assertNull(state.sheet)
        assertEquals(SampleData.at(14, 22), home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardVariant.WokeUp(57 * MINUTE), state.card!!.variant)
        assertEquals(HomeHaptic.CONFIRM, home.haptics.last())
    }

    @Test
    fun endOffsetsBeforeTheStartAreDisabled() = homeTest { home ->
        home.repository.startSleep(SampleData.at(14, 24)).getOrThrow()
        home.tapSleepToggle()
        home.settle()
        val sheet = home.openAdjust()
        assertEquals(
            listOf(OffsetChipUi(-15, false, false), OffsetChipUi(-10, false, false), OffsetChipUi(-5, false, true), OffsetChipUi(5, false, false)),
            sheet.chips,
        )
        home.viewModel.onSheetOffset(-10)
        assertEquals(0, home.settle().sheet!!.offsetMinutes)
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
        home.viewModel.onFeedType(FeedType.FORMULA)
        home.settle()
        suspend fun feedTime() = home.entries().single().startMillis
        fun adjust(minutes: Int) {
            home.openAdjust()
            home.viewModel.onSheetOffset(minutes)
            home.viewModel.onAdjustSave()
            home.settle()
        }

        adjust(-5)
        assertEquals(SampleData.NOW_1432 - 5 * MINUTE, feedTime())
        adjust(-15)
        assertEquals("never cumulative", SampleData.NOW_1432 - 15 * MINUTE, feedTime())
        adjust(-15)
        assertEquals("re-tapping the selected chip returns to 0", SampleData.NOW_1432, feedTime())
        assertTrue(home.openAdjust().chips.none { it.selected })
    }

    @Test
    fun theCardExpiresAfterTenSeconds() = homeTest { home ->
        home.tapSleepToggle()
        home.settle()
        home.scope.testScheduler.advanceTimeBy(9_000)
        assertTrue(home.settle().card != null)
        home.scope.testScheduler.advanceTimeBy(1_100)
        assertNull(home.settle().card)
    }

    @Test
    fun theAdjustSheetHasNoTimeoutAndTheCardWaitsForIt() = homeTest { home ->
        home.tapSleepToggle()
        var state = home.settle()
        val windowKey = state.card!!.windowKey
        home.scope.testScheduler.advanceTimeBy(8_000)
        home.viewModel.onCardAdjust()
        home.settle()
        home.scope.testScheduler.advanceTimeBy(60_000)
        state = home.settle()
        assertTrue("the sheet stays open", state.sheet is HomeSheetUi.Adjust)
        assertTrue("the card waits", state.card!!.paused)

        home.viewModel.onSheetDismiss()
        state = home.settle()
        assertNull(state.sheet)
        assertFalse(state.card!!.paused)
        assertEquals("closing the sheet restarts the window", windowKey + 1, state.card!!.windowKey)
        home.scope.testScheduler.advanceTimeBy(9_000)
        assertTrue(home.settle().card != null)
        home.scope.testScheduler.advanceTimeBy(1_100)
        assertNull(home.settle().card)
    }

    @Test
    fun cancellingTheAdjustSheetMovesNothing() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.FORMULA)
        home.settle()
        home.openAdjust()
        home.viewModel.onSheetOffset(-10)
        home.viewModel.onSheetDismiss()
        home.settle()
        assertEquals(SampleData.NOW_1432, home.entries().single().startMillis)
    }

    @Test
    fun editInTheAdjustSheetSavesTheOffsetFirst() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.FORMULA)
        home.settle()
        val sheet = home.openAdjust()
        home.viewModel.onSheetOffset(-5)
        var editorOpened = false
        home.viewModel.onAdjustEdit(sheet.entryId) { editorOpened = true }
        home.settle()
        assertTrue(editorOpened)
        assertNull(home.settle().sheet)
        assertEquals(SampleData.NOW_1432 - 5 * MINUTE, home.entries().single().startMillis)
    }

    @Test
    fun aDiaperLoggedDuringASleepCanEndTheSleepAtItsTime() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 0)).getOrThrow()
        home.viewModel.onDiaper()
        home.viewModel.onSheetOffset(-5)
        home.viewModel.onDiaperPoop(true)
        home.viewModel.onDiaperSave()
        var state = home.settle()
        assertEquals(CardRow.RunningSleep(endSleepEnabled = true), state.card!!.row)

        home.viewModel.onCardEndSleep()
        state = home.settle()
        assertEquals(SampleData.at(14, 27), home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardVariant.WokeUp(87 * MINUTE), state.card!!.variant)
        assertEquals(CardRow.AdjustTime, state.card!!.row)
    }

    @Test
    fun adjustStartMovesTheRunningStartAndPlusFiveNeedsRoom() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(14, 30)).getOrThrow()
        home.viewModel.onAdjustStart()
        var state = home.settle()
        assertNull("no card, so no timeout", state.card)
        val sheet = state.sheet as HomeSheetUi.Adjust
        assertEquals(sleep.id, sheet.entryId)
        assertTrue(sheet.sleepStart)
        assertEquals(
            listOf(OffsetChipUi(-15, false, true), OffsetChipUi(-10, false, true), OffsetChipUi(-5, false, true), OffsetChipUi(5, false, false)),
            sheet.chips,
        )

        home.viewModel.onSheetOffset(-15)
        home.scope.testScheduler.advanceTimeBy(60_000)
        home.viewModel.onAdjustSave()
        state = home.settle()
        assertNull(state.sheet)
        assertEquals(SampleData.at(14, 15), state.runningSleep!!.startMillis)
        assertTrue("the timer keeps running", state.runningSleep!!.isRunning)
        assertNull(state.card)
    }

    @Test
    fun adjustStartTargetsTheRunningSleepNeverABreastFeed() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 0)).getOrThrow()
        home.repository.logFeed(com.ffmathy.pooparazzi.core.model.FeedType.BREAST, SampleData.at(14, 20))
        home.viewModel.onAdjustStart()
        assertEquals(sleep.id, (home.settle().sheet as HomeSheetUi.Adjust).entryId)
    }

    @Test
    fun adjustStartDoesNothingWithoutARunningSleep() = homeTest { home ->
        home.repository.logFeed(com.ffmathy.pooparazzi.core.model.FeedType.BREAST, SampleData.at(14, 20))
        home.viewModel.onAdjustStart()
        assertNull(home.settle().sheet)
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
        home.scope.testScheduler.advanceTimeBy(10_100)
        assertNull(home.settle().card)
        // A toggle drawn before the sleep started: the guard has no card to attach to.
        home.viewModel.onSleepToggle(shownRunning = false)
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
