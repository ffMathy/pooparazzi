package com.ffmathy.xaternity.phone

import com.ffmathy.xaternity.core.domain.LampState
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.phone.ui.home.CardRow
import com.ffmathy.xaternity.phone.ui.home.HomeSheetUi
import com.ffmathy.xaternity.phone.ui.home.SinceRow
import com.ffmathy.xaternity.phone.ui.preview.HomeSamples
import org.junit.Assert.assertEquals
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
    fun breastfeedingAt1450() {
        val state = HomeSamples.breastfeeding()
        val lamp = state.lamp as LampState.FeedRunning
        assertEquals(18L, lamp.elapsedMinutes)
        assertTrue(state.row1 is SinceRow.Awake)
        assertTrue(state.feedRunning)
    }

    @Test
    fun justLoggedHasTheEarlierRow() {
        val card = HomeSamples.justLogged().card!!
        assertEquals(listOf(-5, -10, -15), (card.row as CardRow.Earlier).chips.map { it.minutes })
    }

    @Test
    fun theSideSheetDisablesEndsBeforeTheStart() {
        val side = HomeSamples.sideSheet().sheet as HomeSheetUi.Side
        assertTrue(side.chips.all { it.enabled })
        assertEquals(SampleData.NOW_1450, side.time)
    }
}
