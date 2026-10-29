package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.home.NoticeVariant
import com.ffmathy.pooparazzi.phone.ui.home.ReceiptVariant
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
    fun formulaLogsAnInstantAtStampPlusOffsetAndShowsTheReceipt() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onSheetOffset(-10)
        home.settle()
        home.clock.advanceBy(20_000) // the stamp was taken on the first tap
        home.viewModel.onFeedType(FeedType.FORMULA)
        val state = home.settle()

        val feed = home.entries().single { it.feedType == FeedType.FORMULA && it.startMillis > SampleData.at(13, 0) }
        assertEquals(SampleData.NOW_1432 - 10 * MINUTE, feed.startMillis)
        assertNull(feed.endMillis)
        val receipt = state.receipt
        assertEquals(ReceiptVariant.FeedLogged(FeedType.FORMULA), receipt.variant)
        assertTrue(receipt.undoable)
        assertEquals(feed.id, receipt.editEntryId)
        assertEquals(listOf(-15, -10, -5, 5), receipt.chips.map { it.minutes })
        assertTrue("the sheet's offset is pre-selected", receipt.chips.single { it.selected }.minutes == -10)
        assertEquals(feed.startMillis, receipt.time)
        assertEquals(listOf(HomeHaptic.SEGMENT_TICK, HomeHaptic.CONFIRM), home.haptics)
    }

    @Test
    fun foodLogsAnInstant() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.FOOD)
        val state = home.settle()
        assertEquals(ReceiptVariant.FeedLogged(FeedType.FOOD), state.receipt.variant)
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
        assertEquals(ReceiptVariant.FeedLogged(FeedType.BREAST, BreastSide.LEFT_THEN_RIGHT), state.receipt.variant)
        assertEquals(-15, state.receipt.chips.single { it.selected }.minutes)
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
        assertEquals(ReceiptVariant.FeedLogged(FeedType.BREAST, null), state.receipt.variant)
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
        assertNull(state.sheet)
        assertEquals(NoticeVariant.Undone, state.card!!.variant)
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
        assertTrue(state.receipt.variant is ReceiptVariant.FeedLogged)
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
        assertEquals(ReceiptVariant.FeedLogged(null), state.receipt.variant)
        assertEquals(1, state.today.feeds)
        assertEquals(feed.id, (state.lamp as LampState.FeedIdle).lastFeed!!.id)
    }

    @Test
    fun theSameFeedTwiceWithinFiveSecondsIsDeduplicated() = homeTest { home ->
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.FORMULA)
        home.settle()
        home.closeReceipt()
        home.clock.advanceBy(2_000)
        home.tapFeed()
        home.settle()
        home.clock.advanceBy(1_000)
        // Same stamp window: the sheet was opened 2 s after the first log.
        home.viewModel.onFeedType(FeedType.FORMULA)
        val state = home.settle()
        assertEquals(1, home.entries().count { it.feedType == FeedType.FORMULA && it.startMillis >= SampleData.NOW_1432 })
        assertEquals(SampleData.NOW_1432, state.receipt.dedupe!!.startMillis)
        assertEquals("the receipt is for the existing entry", SampleData.NOW_1432, state.receipt.time)
        assertEquals(HomeHaptic.REJECT, home.haptics.last())

        home.scope.testScheduler.advanceTimeBy(2_100)
        assertNull("the dedupe line lasts 2 s", home.settle().receipt.dedupe)
    }

    @Test
    fun aFeedLoggedDuringASleepOffersEndSleep() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 50)).getOrThrow()
        home.tapFeed()
        home.viewModel.onFeedType(FeedType.BREAST)
        home.viewModel.onLogBreastfeed()
        var state = home.settle()
        assertEquals(true, state.receipt.endSleep)
        // The sleep keeps the Lamp: a breast feed is an instant.
        assertTrue(state.lamp is LampState.SleepRunning)

        home.viewModel.onEndSleep()
        state = home.settle()
        assertEquals(SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
        assertEquals(ReceiptVariant.WokeUp(42 * MINUTE), state.receipt.variant)
        assertFalse(state.sleepRunning)
        assertFalse(state.anythingRunning)
    }
}
