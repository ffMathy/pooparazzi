package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.home.CardRow
import com.ffmathy.pooparazzi.phone.ui.home.CardVariant
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.SinceRow
import com.ffmathy.pooparazzi.phone.ui.preview.HomeSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The derived Home values of the reference scenario (V1_ADDITIONS §6). */
class HomeStateBuilderTest {
    private val minute = 60_000L

    @Test
    fun idleAt1432() {
        val state = HomeSamples.idle()
        val lamp = state.lamp as LampState.FeedIdle
        assertEquals(FeedType.FORMULA, lamp.lastFeed!!.feedType)
        assertEquals(130 * minute, lamp.sinceMillis)
        val awake = state.row1 as SinceRow.Awake
        assertEquals(SampleData.at(13, 27), awake.sinceMillis)
        assertEquals(65 * minute, awake.elapsedMillis)
        val diaper = state.row2 as SinceRow.LastDiaper
        assertEquals(DiaperKind.PEE, diaper.kind)
        assertEquals(48 * minute, diaper.elapsedMillis)
        assertEquals(5, state.today.feeds)
        assertEquals(4, state.today.pee)
        assertEquals(2, state.today.poop)
        assertEquals(477L, state.today.sleepMinutes)
        assertNull(state.card)
    }

    @Test
    fun asleepAt0312ShowsLastFedInRowOne() {
        val state = HomeSamples.asleep()
        val lamp = state.lamp as LampState.SleepRunning
        assertEquals(67L, lamp.elapsedMinutes)
        val fed = state.row1 as SinceRow.LastFed
        assertEquals(FeedType.BREAST, fed.feed.feedType)
        assertEquals(92 * minute, fed.elapsedMillis)
        assertEquals(80 * minute, (state.row2 as SinceRow.LastDiaper).elapsedMillis)
        assertEquals(1, state.today.feeds)
        assertEquals(162L, state.today.sleepMinutes)
    }

    @Test
    fun aBreastFeedLeavesTheLampIdleAt1450() {
        val state = HomeSamples.breastLogged()
        val lamp = state.lamp as LampState.FeedIdle
        assertEquals(18L, lamp.elapsedMinutes)
        assertEquals(FeedType.BREAST, lamp.lastFeed!!.feedType)
        assertTrue(state.row1 is SinceRow.Awake)
        assertFalse(state.anythingRunning)
        assertEquals(CardVariant.FeedLogged(FeedType.BREAST, BreastSide.LEFT_THEN_RIGHT), state.card!!.variant)
    }

    @Test
    fun justLoggedOffersAdjustTime() {
        val card = HomeSamples.justLogged().card!!
        assertEquals(CardRow.AdjustTime, card.row)
        assertFalse(card.paused)
    }

    @Test
    fun theAdjustSheetOverAPausedCardCannotGoIntoTheFuture() {
        val state = HomeSamples.adjustingFeed()
        assertTrue(state.card!!.paused)
        val sheet = state.sheet as HomeSheetUi.Adjust
        assertFalse(sheet.sleepStart)
        assertEquals(listOf(true, true, true, false), sheet.chips.map { it.enabled })
    }

    @Test
    fun adjustingStartShowsTheChosenStart() {
        val sheet = HomeSamples.adjustingStart().sheet as HomeSheetUi.Adjust
        assertTrue(sheet.sleepStart)
        assertEquals(SampleData.at(1, 50), sheet.time)
        assertEquals(-15, sheet.chips.single { it.selected }.minutes)
    }

    @Test
    fun theBreastStepKeepsTheSheetChipsAndTicks() {
        val sheet = HomeSamples.breastSheet(listOf(Breast.RIGHT, Breast.LEFT)).sheet as HomeSheetUi.Feed
        assertTrue(sheet.breast)
        assertEquals(listOf(0, -5, -10, -15), sheet.chips.map { it.minutes })
        assertTrue(sheet.chips.all { it.enabled })
        assertEquals(BreastSide.RIGHT_THEN_LEFT, sheet.side)
        assertEquals(SampleData.NOW_1432, sheet.time)
    }
}
