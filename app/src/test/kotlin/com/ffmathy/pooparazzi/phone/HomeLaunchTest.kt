package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import com.ffmathy.pooparazzi.phone.ui.home.ReceiptVariant
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home opened by a widget or notification (DESIGN_SPEC §3.1): the sheet it asks for, or the widget log's receipt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeLaunchTest : HomeViewModelTestBase() {

    @Test
    fun feedOpensTheFeedSheet() = homeTest { home ->
        home.viewModel.onLaunch(HomeLaunch.Feed)
        val sheet = home.settle().sheet as HomeSheetUi.Feed
        assertEquals(SampleData.NOW_1432, sheet.stamp)
    }

    @Test
    fun feedAfterABreastFeedStillOpensTheFeedSheet() = homeTest { home ->
        home.seed(listOf(SampleData.recentBreastfeed()))
        home.clock.set(SampleData.NOW_1450)
        home.viewModel.onLaunch(HomeLaunch.Feed)
        val sheet = home.settle().sheet as HomeSheetUi.Feed
        assertEquals(false, sheet.breast)
        assertEquals(SampleData.NOW_1450, sheet.stamp)
    }

    @Test
    fun diaperOpensTheDiaperSheetWithBothTogglesOff() = homeTest { home ->
        home.viewModel.onLaunch(HomeLaunch.Diaper)
        val sheet = home.settle().sheet as HomeSheetUi.Diaper
        assertEquals(false, sheet.pee || sheet.poop)
    }

    @Test
    fun aWidgetSheetReportsItsCloseOnceItsLogIsSaved() = homeTest { home ->
        val closes = mutableListOf<Unit>()
        backgroundScope.launch { home.viewModel.launchedSheetClosed.collect { closes += it } }
        home.viewModel.onLaunch(HomeLaunch.Diaper)
        home.settle()
        home.viewModel.onDiaperPoop(true)
        home.viewModel.onDiaperSave()
        home.viewModel.onSheetClosed()
        home.settle()
        assertNull("no receipt follows the sheet", home.settle().sheet)
        assertEquals(1, closes.size)
        // The poop is written before the app may leave (the cover screen returns to the widget).
        assertTrue(home.entries().any { it.poop && it.startMillis == SampleData.NOW_1432 })
    }

    @Test
    fun aWidgetSheetDismissedWithBackReportsItsClose() = homeTest { home ->
        val closes = mutableListOf<Unit>()
        backgroundScope.launch { home.viewModel.launchedSheetClosed.collect { closes += it } }
        home.viewModel.onLaunch(HomeLaunch.Feed)
        home.settle()
        home.viewModel.onSheetDismiss()
        home.viewModel.onSheetClosed()
        home.settle()
        assertEquals(1, closes.size)
    }

    @Test
    fun homesOwnSheetsDoNotReportAClose() = homeTest { home ->
        val closes = mutableListOf<Unit>()
        backgroundScope.launch { home.viewModel.launchedSheetClosed.collect { closes += it } }
        home.viewModel.onDiaper()
        home.settle()
        home.viewModel.onSheetDismiss()
        home.viewModel.onSheetClosed()
        home.settle()
        assertTrue(closes.isEmpty())
    }

    @Test
    fun aWidgetLogOpensItsReceipt() = homeTest { home ->
        val poop = Entry.diaper(SampleData.NOW_1432, pee = false, poop = true, id = "widget-poop")
        home.seed(listOf(poop))
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.LOGGED, poop.id))
        val receipt = home.settle().receipt
        assertEquals(ReceiptVariant.DiaperLogged(DiaperKind.POOP), receipt.variant)
        assertTrue(receipt.undoable)

        home.viewModel.onUndo()
        home.settle()
        assertNull(home.entries().firstOrNull { it.id == poop.id })
    }

    @Test
    fun aWidgetWokeUpCanBeUndoneInTheApp() = homeTest { home ->
        val sleep = Entry.sleep(SampleData.at(13, 50), SampleData.at(14, 30), id = "widget-sleep")
        home.seed(listOf(sleep))
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.WOKE_UP, sleep.id))
        assertTrue(home.settle().receipt.variant is ReceiptVariant.WokeUp)
        home.viewModel.onUndo()
        home.settle()
        assertTrue(home.entries().single { it.id == sleep.id }.let { it.isSleep && it.isRunning })
    }

    @Test
    fun aStaleReceiptShowsNothing() = homeTest { home ->
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.LOGGED, "gone"))
        assertNull(home.settle().sheet)
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.SLEEP_STARTED, "sample-sleep-1240"))
        assertNull("that sleep is not running", home.settle().sheet)
    }
}
