package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.isRunning
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

/** Feed types, the Feed sheet and breastfeeds with their side (V1_ADDITIONS §2.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeFeedTest : HomeViewModelTestBase() {

    @Test
    fun feedOpensTheFeedSheetStampedAtTheTap() = homeTest { home ->
        home.tapFeed()
        val sheet = home.settle().sheet as HomeSheetUi.Feed
        assertEquals(SampleData.NOW_1432, sheet.stamp)
        assertEquals(listOf(0, -5, -10, -15), sheet.chips.map { it.minutes })
        assertTrue(sheet.chips.first { it.minutes == 0 }.selected)
    }

    @Test
    fun formulaLogsAnInstantAtStampPlusOffsetAndShowsTheCard() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onSheetOffset(-10)
        home.settle()
        home.clock.advanceBy(20_000) // the stamp was taken on the first tap
        home.viewModel.onFeedType(FeedType.FORMULA)
        val state = home.settle()

        val feed = home.entries().single { it.feedType == FeedType.FORMULA && it.startMillis > SampleData.at(13, 0) }
        assertEquals(SampleData.NOW_1432 - 10 * MINUTE, feed.startMillis)
        assertNull(feed.endMillis)
        assertNull(state.sheet)
        val card = state.card!!
        assertEquals(CardVariant.FeedLogged(FeedType.FORMULA), card.variant)
        assertEquals(CardAction.UNDO, card.action)
        val row = card.row as CardRow.Earlier
        assertEquals(listOf(-5, -10, -15), row.chips.map { it.minutes })
        assertTrue("the sheet's offset is pre-selected", row.chips.single { it.selected }.minutes == -10)
        assertEquals(listOf(HomeHaptic.SEGMENT_TICK, HomeHaptic.CONFIRM), home.haptics)
    }

    @Test
    fun foodLogsAnInstant() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.FOOD)
        val state = home.settle()
        assertEquals(CardVariant.FeedLogged(FeedType.FOOD), state.card!!.variant)
        val lamp = state.lamp as LampState.FeedIdle
        assertEquals(FeedType.FOOD, lamp.lastFeed!!.feedType)
        assertEquals(1, state.today.feeds)
    }

    @Test
    fun breastStartsATimerAndEndFeedOpensTheSideSheet() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        var state = home.settle()
        val running = state.runningFeed!!
        assertTrue(running.isRunning)
        assertEquals(SampleData.NOW_1432, running.startMillis)
        assertEquals(CardVariant.FeedStarted, state.card!!.variant)
        assertTrue(state.lamp is LampState.FeedRunning)

        home.viewModel.onSheetClosed()
        home.scope.testScheduler.advanceTimeBy(700)
        home.clock.advanceBy(18 * MINUTE)
        home.tapFeed()
        state = home.settle()
        val side = state.sheet as HomeSheetUi.Side
        assertEquals(running.id, side.feedId)
        assertEquals(running.startMillis, side.startMillis)
        assertEquals(listOf(0, -5, -10), side.chips.map { it.minutes })
    }

    @Test
    fun aSideEndsTheBreastfeedWithItsDuration() = homeTest { home ->
        val feed = home.repository.startBreastfeed(SampleData.NOW_1432).getOrThrow()
        home.clock.advanceBy(18 * MINUTE)
        home.tapFeed()
        home.viewModel.onSideTick(Breast.LEFT)
        home.viewModel.onSideTick(Breast.RIGHT)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, (home.settle().sheet as HomeSheetUi.Side).side)
        home.viewModel.onEndFeed()
        val state = home.settle()

        val ended = home.repository.get(feed.id)!!
        assertEquals(SampleData.NOW_1450, ended.endMillis)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, ended.breastSide)
        assertNull(state.runningFeed)
        assertEquals(CardVariant.FeedEnded(BreastSide.LEFT_THEN_RIGHT, 18 * MINUTE), state.card!!.variant)
        assertTrue(state.lamp is LampState.FeedIdle)
    }

    @Test
    fun theSideFollowsTheTickOrderAndUntickingRemovesIt() = homeTest { home ->
        home.repository.startBreastfeed(SampleData.NOW_1432).getOrThrow()
        home.clock.advanceBy(18 * MINUTE)
        home.tapFeed()
        home.viewModel.onSideTick(Breast.RIGHT)
        home.viewModel.onSideTick(Breast.LEFT)
        assertEquals(BreastSide.RIGHT_THEN_LEFT, (home.settle().sheet as HomeSheetUi.Side).side)
        home.viewModel.onSideTick(Breast.RIGHT)
        assertEquals(listOf(Breast.LEFT), (home.settle().sheet as HomeSheetUi.Side).ticks)
        home.viewModel.onSideTick(Breast.RIGHT)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, (home.settle().sheet as HomeSheetUi.Side).side)
    }

    @Test
    fun endFeedWithNothingTickedRecordsNoSide() = homeTest { home ->
        val feed = home.repository.startBreastfeed(SampleData.NOW_1432).getOrThrow()
        home.clock.advanceBy(18 * MINUTE)
        home.tapFeed()
        home.viewModel.onEndFeed()
        val state = home.settle()
        val ended = home.repository.get(feed.id)!!
        assertEquals(SampleData.NOW_1450, ended.endMillis)
        assertNull(ended.breastSide)
        assertEquals(CardVariant.FeedEnded(null, 18 * MINUTE), state.card!!.variant)
    }

    @Test
    fun justTheTimeLogsAFeedWithNoType() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(null)
        val state = home.settle()
        val feed = home.entries().single()
        assertNull(feed.feedType)
        assertNull(feed.endMillis)
        assertEquals(SampleData.NOW_1432, feed.startMillis)
        assertEquals(CardVariant.FeedLogged(null), state.card!!.variant)
        assertEquals(1, state.today.feeds)
        assertEquals(feed.id, (state.lamp as LampState.FeedIdle).lastFeed!!.id)
    }

    @Test
    fun sideEndChipsBeforeTheStartAreDisabled() = homeTest { home ->
        home.repository.startBreastfeed(SampleData.NOW_1432).getOrThrow()
        home.clock.advanceBy(7 * MINUTE)
        home.tapFeed()
        val side = home.settle().sheet as HomeSheetUi.Side
        assertEquals(
            listOf(OffsetChipUi(0, true, true), OffsetChipUi(-5, false, true), OffsetChipUi(-10, false, false)),
            side.chips,
        )
        home.viewModel.onSheetOffset(-10)
        assertEquals(0, (home.settle().sheet as HomeSheetUi.Side).offsetMinutes)
    }

    @Test
    fun cancellingTheSideSheetKeepsTheFeedRunning() = homeTest { home ->
        home.repository.startBreastfeed(SampleData.NOW_1432).getOrThrow()
        home.clock.advanceBy(5 * MINUTE)
        home.tapFeed()
        home.settle()
        home.viewModel.onSheetDismiss()
        val state = home.settle()
        assertNull(state.sheet)
        assertTrue(state.runningFeed!!.isRunning)
        assertNull(state.card)
    }

    @Test
    fun aBreastfeedEndedWithinAMinuteIsCancelledAndUndoResumesIt() = homeTest { home ->
        val feed = home.repository.startBreastfeed(SampleData.NOW_1432).getOrThrow()
        home.clock.advanceBy(30_000)
        home.tapFeed()
        home.viewModel.onSideTick(Breast.LEFT)
        home.viewModel.onEndFeed()
        var state = home.settle()
        assertEquals(CardVariant.FeedCancelled(BreastSide.LEFT), state.card!!.variant)
        assertEquals(CardRow.None, state.card!!.row)
        assertTrue(home.repository.get(feed.id)!!.deleted)
        assertNull(state.runningFeed)

        home.viewModel.onUndo()
        state = home.settle()
        assertEquals(CardVariant.Undone, state.card!!.variant)
        assertEquals(feed.id, state.runningFeed!!.id)
        assertNull(state.runningFeed!!.breastSide)
    }

    @Test
    fun breastWhileOneRunsShowsTheFeedingGuard() = homeTest { home ->
        home.tapFeed()
        home.settle()
        // The watch starts a breastfeed while the sheet is open.
        val other = home.repository.startBreastfeed(SampleData.NOW_1432 - 2 * MINUTE).getOrThrow()
        home.viewModel.onFeedType(FeedType.BREAST)
        val state = home.settle()
        assertEquals(CardRow.Guard(GuardKind.FEEDING, other.startMillis), state.card!!.row)
        assertEquals(HomeHaptic.REJECT, home.haptics.last())
        assertEquals(1, home.entries().count { it.isRunning })
    }

    @Test
    fun theSameFeedTwiceWithinFiveSecondsIsDeduplicated() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.FORMULA)
        home.settle()
        home.viewModel.onSheetClosed()
        home.scope.testScheduler.advanceTimeBy(700)
        home.clock.advanceBy(2_000)
        home.tapFeed()
        home.settle()
        home.clock.advanceBy(1_000)
        // Same stamp window: the sheet was opened 2 s after the first log.
        home.viewModel.onFeedType(FeedType.FORMULA)
        val state = home.settle()
        assertEquals(1, home.entries().count { it.feedType == FeedType.FORMULA && it.startMillis >= SampleData.NOW_1432 })
        val row = state.card!!.row as CardRow.Dedupe
        assertEquals(SampleData.NOW_1432, row.entry.startMillis)
        assertEquals(HomeHaptic.REJECT, home.haptics.last())

        home.scope.testScheduler.advanceTimeBy(2_100)
        assertTrue("the dedupe line lasts 2 s", home.settle().card!!.row is CardRow.Earlier)
    }

    @Test
    fun aFeedLoggedDuringASleepOffersEndSleep() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 50)).getOrThrow()
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        var state = home.settle()
        val row = state.card!!.row as CardRow.RunningSleep
        assertTrue(row.endSleepEnabled)
        assertEquals(listOf(-5, -10), row.chips.map { it.minutes })
        // A running feed takes the Lamp; since-row 1 shows the sleep.
        assertTrue(state.lamp is LampState.FeedRunning)

        home.viewModel.onCardEndSleep()
        state = home.settle()
        assertEquals(SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardVariant.WokeUp(42 * MINUTE), state.card!!.variant)
        assertFalse(state.sleepRunning)
        assertTrue("the breastfeed keeps running", state.feedRunning)
    }
}
