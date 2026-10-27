package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.home.CardAction
import com.ffmathy.pooparazzi.phone.ui.home.CardRow
import com.ffmathy.pooparazzi.phone.ui.home.CardVariant
import com.ffmathy.pooparazzi.phone.ui.home.SinceRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home's side of the edit sheet: the since-rows name the entry to edit; a delete there shows "… deleted" with Undo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeEditTest : HomeViewModelTestBase() {

    @Test
    fun sinceRowsEditTheirLatestEntry() = homeTest { home ->
        home.seed(SampleData.scenario(SampleData.ZONE))
        val state = home.settle()
        assertEquals("row 1: the last sleep", "sample-sleep-1240", (state.row1 as SinceRow.Awake).entryId)
        assertEquals("row 2: the last diaper", "sample-pee-1344", state.row2!!.entryId)
    }

    @Test
    fun aDeleteInTheEditSheetShowsTheDeletedCardAndUndoRestores() = homeTest { home ->
        home.seed(SampleData.scenario(SampleData.ZONE))
        val previous = home.repository.delete("sample-sleep-1240")!!
        home.viewModel.onEntryDeleted(previous)
        val card = home.settle().card!!

        assertEquals(CardVariant.Deleted(previous), card.variant)
        assertEquals(CardAction.UNDO, card.action)
        assertEquals(CardRow.None, card.row)
        assertEquals("the since-row falls back to the sleep before", "sample-sleep-0930", home.settle().row1!!.entryId)

        home.viewModel.onUndo()
        val undone = home.settle()
        assertEquals(CardVariant.Undone, undone.card!!.variant)
        assertFalse(home.repository.get("sample-sleep-1240")!!.deleted)
        assertEquals("sample-sleep-1240", undone.row1!!.entryId)
    }

    @Test
    fun undoingADeletedRunningSleepStartsItAgain() = homeTest(now = SampleData.NOW_0312) { home ->
        home.seed(SampleData.scenarioAt0312(SampleData.ZONE))
        val previous = home.repository.delete("sample-sleep-0205")!!
        home.viewModel.onEntryDeleted(previous)
        assertFalse(home.settle().sleepRunning)

        home.viewModel.onUndo()
        val state = home.settle()
        assertTrue(state.sleepRunning)
        assertTrue(home.repository.get("sample-sleep-0205")!!.isRunning)
    }
}
