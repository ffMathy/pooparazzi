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
import com.ffmathy.pooparazzi.phone.ui.home.HomeHaptic
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
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
        assertEquals(CardRow.AdjustTime, card.row)
        assertEquals(listOf(HomeHaptic.SEGMENT_TICK, HomeHaptic.CONFIRM), home.haptics)
        val adjust = home.openAdjust()
        assertEquals(listOf(-15, -10, -5, 5), adjust.chips.map { it.minutes })
        assertTrue("the sheet's offset is pre-selected", adjust.chips.single { it.selected }.minutes == -10)
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
    fun breastOpensTheSideStepInsteadOfLogging() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onSheetOffset(-5)
        home.viewModel.onFeedType(FeedType.BREAST)
        val state = home.settle()
        val sheet = state.sheet as HomeSheetUi.Feed
        assertTrue(sheet.breast)
        assertEquals(emptyList<Breast>(), sheet.ticks)
        assertNull(sheet.side)
        assertEquals("the time picked before Breast is kept", -5, sheet.offsetMinutes)
        assertTrue("nothing is logged yet", home.entries().isEmpty())
        assertNull(state.card)
        assertTrue(state.lamp is LampState.FeedIdle)
    }

    @Test
    fun logFeedLogsABreastFeedInstantWithTheTickedOrder() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        home.viewModel.onSideTick(Breast.LEFT)
        home.viewModel.onSideTick(Breast.RIGHT)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, (home.settle().sheet as HomeSheetUi.Feed).side)
        // The time can still change in the Breast step.
        home.viewModel.onSheetOffset(-15)
        home.clock.advanceBy(20_000)
        home.viewModel.onLogBreastfeed()
        val state = home.settle()

        val feed = home.entries().single()
        assertEquals(FeedType.BREAST, feed.feedType)
        assertEquals(SampleData.NOW_1432 - 15 * MINUTE, feed.startMillis)
        assertNull("a breast feed has no end", feed.endMillis)
        assertFalse(feed.isRunning)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, feed.breastSide)
        assertNull(state.sheet)
        assertEquals(CardVariant.FeedLogged(FeedType.BREAST, BreastSide.LEFT_THEN_RIGHT), state.card!!.variant)
        assertEquals(CardRow.AdjustTime, state.card!!.row)
        val lamp = state.lamp as LampState.FeedIdle
        assertEquals(feed.id, lamp.lastFeed!!.id)
        assertEquals(1, state.today.feeds)
        assertEquals(HomeHaptic.CONFIRM, home.haptics.last())
    }

    @Test
    fun theSideFollowsTheTickOrderAndUntickingRemovesIt() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        home.viewModel.onSideTick(Breast.RIGHT)
        home.viewModel.onSideTick(Breast.LEFT)
        assertEquals(BreastSide.RIGHT_THEN_LEFT, (home.settle().sheet as HomeSheetUi.Feed).side)
        home.viewModel.onSideTick(Breast.RIGHT)
        assertEquals(listOf(Breast.LEFT), (home.settle().sheet as HomeSheetUi.Feed).ticks)
        home.viewModel.onSideTick(Breast.RIGHT)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, (home.settle().sheet as HomeSheetUi.Feed).side)
    }

    @Test
    fun sideTicksAndLogFeedDoNothingBeforeBreastIsTapped() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onSideTick(Breast.LEFT)
        home.viewModel.onLogBreastfeed()
        val sheet = home.settle().sheet as HomeSheetUi.Feed
        assertFalse(sheet.breast)
        assertEquals(emptyList<Breast>(), sheet.ticks)
        assertTrue(home.entries().isEmpty())
    }

    @Test
    fun logFeedWithNothingTickedRecordsJustTheTime() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        home.viewModel.onLogBreastfeed()
        val state = home.settle()
        val feed = home.entries().single()
        assertEquals(FeedType.BREAST, feed.feedType)
        assertEquals(SampleData.NOW_1432, feed.startMillis)
        assertNull(feed.breastSide)
        assertNull(feed.endMillis)
        assertEquals(CardVariant.FeedLogged(FeedType.BREAST, null), state.card!!.variant)
    }

    @Test
    fun cancellingTheBreastStepLogsNothing() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        home.viewModel.onSideTick(Breast.LEFT)
        home.viewModel.onSheetDismiss()
        val state = home.settle()
        assertNull(state.sheet)
        assertNull(state.card)
        assertTrue(home.entries().isEmpty())
    }

    @Test
    fun undoRemovesTheBreastFeed() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        home.viewModel.onSideTick(Breast.RIGHT)
        home.viewModel.onLogBreastfeed()
        home.settle()
        home.viewModel.onUndo()
        val state = home.settle()
        assertEquals(CardVariant.Undone, state.card!!.variant)
        assertTrue(home.entries().isEmpty())
    }

    @Test
    fun aBreastFeedNeverBlocksTheNextOne() = homeTest { home ->
        // A breastfeed logged on the watch a minute ago (an older version would have shown it as running).
        home.repository.logFeed(FeedType.BREAST, SampleData.NOW_1432 - MINUTE, BreastSide.LEFT)
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        home.viewModel.onLogBreastfeed()
        val state = home.settle()
        assertEquals(2, home.entries().count { it.feedType == FeedType.BREAST })
        assertTrue(state.card!!.variant is CardVariant.FeedLogged)
        assertFalse(state.anythingRunning)
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
        assertEquals("the dedupe line lasts 2 s", CardRow.AdjustTime, home.settle().card!!.row)
    }

    @Test
    fun aFeedLoggedDuringASleepOffersEndSleep() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 50)).getOrThrow()
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        home.viewModel.onLogBreastfeed()
        var state = home.settle()
        assertEquals(CardRow.RunningSleep(endSleepEnabled = true), state.card!!.row)
        // The sleep keeps the Lamp: a breast feed is an instant.
        assertTrue(state.lamp is LampState.SleepRunning)

        home.viewModel.onCardEndSleep()
        state = home.settle()
        assertEquals(SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
        assertEquals(CardVariant.WokeUp(42 * MINUTE), state.card!!.variant)
        assertFalse(state.sleepRunning)
        assertFalse(state.anythingRunning)
    }
}
