package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
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

/** A feed or diaper saved during a sleep also logs Woke up, and the sheet says so first (DESIGN_SPEC §4.4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeWakeOnLogTest : HomeViewModelTestBase() {

    @Test
    fun theFeedSheetSaysWokeUpWillBeLoggedAndSavingEndsTheSleepAtTheFeedsTime() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 25)).getOrThrow()
        home.tapFeed()
        home.viewModel.onSheetOffset(-5)
        assertTrue((home.settle().sheet as HomeSheetUi.Feed).wakes)

        home.viewModel.onFeedType(FeedType.FOOD)
        val state = home.settle()

        assertEquals(SampleData.at(14, 27), home.repository.get(sleep.id)!!.endMillis)
        assertFalse(state.sleepRunning)
        assertNull("still no receipt", state.sheet)
        assertEquals(HomeHaptic.CONFIRM, home.haptics.last())
    }

    @Test
    fun theDiaperSheetSaysSoTooAndSavingEndsTheSleep() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(13, 25)).getOrThrow()
        home.viewModel.onDiaper()
        assertTrue((home.settle().sheet as HomeSheetUi.Diaper).wakes)

        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperSave()
        home.settle()

        assertEquals(SampleData.NOW_1432, home.repository.get(sleep.id)!!.endMillis)
    }

    @Test
    fun aTimeBeforeTheSleepBeganLeavesItRunningAndTheNoteGoes() = homeTest { home ->
        val sleep = home.repository.startSleep(SampleData.at(14, 25)).getOrThrow()
        home.tapFeed()
        home.viewModel.onSheetOffset(-10)
        assertFalse((home.settle().sheet as HomeSheetUi.Feed).wakes)

        home.viewModel.onFeedType(null)
        home.settle()

        assertNull(home.repository.get(sleep.id)!!.endMillis)
    }

    @Test
    fun awakeTheSheetsSayNothing() = homeTest { home ->
        home.tapFeed()
        assertFalse((home.settle().sheet as HomeSheetUi.Feed).wakes)
    }
}
