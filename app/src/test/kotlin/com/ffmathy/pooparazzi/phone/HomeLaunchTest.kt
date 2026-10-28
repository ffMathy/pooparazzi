package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.launch.ReceiptKind
import com.ffmathy.pooparazzi.phone.ui.home.CardAction
import com.ffmathy.pooparazzi.phone.ui.home.CardVariant
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home opened by a widget or notification (DESIGN_SPEC §3.1): the sheet it asks for, or the widget log's undo card. */
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
    fun aWidgetLogOpensItsUndoCard() = homeTest { home ->
        val poop = Entry.diaper(SampleData.NOW_1432, pee = false, poop = true, id = "widget-poop")
        home.seed(listOf(poop))
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.LOGGED, poop.id))
        val card = home.settle().card!!
        assertEquals(CardVariant.DiaperLogged(DiaperKind.POOP), card.variant)
        assertEquals(CardAction.UNDO, card.action)

        home.viewModel.onUndo()
        home.settle()
        assertNull(home.entries().firstOrNull { it.id == poop.id })
    }

    @Test
    fun aWidgetWokeUpCanBeUndoneInTheApp() = homeTest { home ->
        val sleep = Entry.sleep(SampleData.at(13, 50), SampleData.at(14, 30), id = "widget-sleep")
        home.seed(listOf(sleep))
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.WOKE_UP, sleep.id))
        assertTrue(home.settle().card!!.variant is CardVariant.WokeUp)
        home.viewModel.onUndo()
        home.settle()
        assertTrue(home.entries().single { it.id == sleep.id }.let { it.isSleep && it.isRunning })
    }

    @Test
    fun aStaleReceiptShowsNoCard() = homeTest { home ->
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.LOGGED, "gone"))
        assertNull(home.settle().card)
        home.viewModel.onLaunch(HomeLaunch.Receipt(ReceiptKind.SLEEP_STARTED, "sample-sleep-1240"))
        assertNull("that sleep is not running", home.settle().card)
    }
}
