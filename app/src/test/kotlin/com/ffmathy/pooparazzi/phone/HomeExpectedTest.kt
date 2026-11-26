package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Expected 15:25" under the sheets' chips (V1_ADDITIONS §9): a forgotten feed or sleep can be put where the past week
 * expected it, even long after the estimate passed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeExpectedTest : HomeViewModelTestBase() {

    @Test
    fun theFeedSheetOffersTheExpectedFeedAndLogsItThere() = homeTest(now = SampleData.at(18, 40)) { home ->
        home.seed(SampleData.history(zone = SampleData.ZONE))
        home.tapFeed()
        val expected = (home.settle().sheet as HomeSheetUi.Feed).expected!!
        assertEquals(SampleData.at(15, 25), expected.atMillis)
        assertFalse(expected.selected)

        home.viewModel.onSheetExpected()
        val sheet = home.settle().sheet as HomeSheetUi.Feed
        assertEquals(SampleData.at(15, 25), sheet.time)
        assertTrue(sheet.expected!!.selected)

        home.viewModel.onFeedType(FeedType.FOOD)
        home.settle()
        assertEquals(SampleData.at(15, 25), home.entries().maxBy { it.startMillis }.startMillis)
    }

    @Test
    fun aSecondTapReturnsToTheBaseTime() = homeTest(now = SampleData.at(18, 40)) { home ->
        home.seed(SampleData.history(zone = SampleData.ZONE))
        home.tapFeed()
        home.viewModel.onSheetExpected()
        home.settle()
        home.viewModel.onSheetExpected()
        val sheet = home.settle().sheet as HomeSheetUi.Feed
        assertEquals(0, sheet.offsetMinutes)
        assertFalse(sheet.expected!!.selected)
    }

    @Test
    fun aLateStartSleepOffersTheExpectedSleepAndMovesTheStartThere() = homeTest(now = SampleData.at(18, 20)) { home ->
        home.seed(SampleData.history(zone = SampleData.ZONE))
        home.tapSleepToggle()
        val receipt = home.settle().receipt
        assertEquals(SampleData.at(15, 15), receipt.expected!!.atMillis)

        home.viewModel.onSheetExpected()
        home.settle()
        val sleep = home.entries().single { it.isSleep && it.endMillis == null }
        assertEquals(SampleData.at(15, 15), sleep.startMillis)
        assertTrue(home.settle().receipt.expected!!.selected)
    }

    @Test
    fun anEstimateCloseToTheTimeIsNotOffered() = homeTest(now = SampleData.at(15, 28)) { home ->
        home.seed(SampleData.history(zone = SampleData.ZONE))
        home.tapFeed()
        assertNull("15:25 is within the nudge chips' reach", (home.settle().sheet as HomeSheetUi.Feed).expected)
    }

    @Test
    fun withNothingToGoOnThereIsNoChip() = homeTest { home ->
        home.tapFeed()
        assertNull((home.settle().sheet as HomeSheetUi.Feed).expected)
    }
}
