package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.home.HomeHaptic
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.NoticeVariant
import com.ffmathy.pooparazzi.phone.ui.home.SinceRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Diaper sheet: independent Pee / Poop checkboxes, optional colour and texture (V1_ADDITIONS §3.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HomeDiaperTest : HomeViewModelTestBase() {

    private fun HomeTestRig.diaperSheet() = settle().sheet as HomeSheetUi.Diaper

    @Test
    fun bothCheckboxesAreOffWhenTheSheetOpensAndSavingThenLogsAPlainChange() = homeTest { home ->
        home.viewModel.onDiaper()
        val sheet = home.diaperSheet()
        assertFalse(sheet.pee)
        assertFalse(sheet.poop)

        home.viewModel.onDiaperSave()
        val state = home.settle()
        val diaper = home.entries().single()
        assertFalse(diaper.pee || diaper.poop)
        assertNull("no receipt follows the sheet", state.sheet)
        assertEquals(DiaperKind.PLAIN, (state.row2 as SinceRow.LastDiaper).kind)
        // A plain change counts in neither pee nor poop.
        assertEquals(0, state.today.pee)
        assertEquals(0, state.today.poop)
    }

    @Test
    fun peeAloneLogsAPeeDiaper() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperSave()
        val state = home.settle()
        val diaper = home.entries().single()
        assertTrue(diaper.pee)
        assertFalse(diaper.poop)
        assertEquals(SampleData.NOW_1432, diaper.startMillis)
        assertNull("no receipt follows the sheet", state.sheet)
        assertEquals(DiaperKind.PEE, (state.row2 as SinceRow.LastDiaper).kind)
        assertEquals(HomeHaptic.CONFIRM, home.haptics.last())
    }

    @Test
    fun poopWithColourAndTextureAtAnOffset() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onSheetOffset(-15)
        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperPoop(true)
        home.viewModel.onPoopColor(PoopColor.YELLOW)
        home.viewModel.onPoopTexture(PoopTexture.SEEDY)
        val sheet = home.diaperSheet()
        assertEquals(PoopColor.YELLOW, sheet.color)
        assertEquals(PoopTexture.SEEDY, sheet.texture)

        home.viewModel.onDiaperSave()
        val state = home.settle()
        val diaper = home.entries().single()
        assertTrue(diaper.pee && diaper.poop)
        assertEquals(PoopColor.YELLOW, diaper.poopColor)
        assertEquals(PoopTexture.SEEDY, diaper.poopTexture)
        assertEquals(SampleData.NOW_1432 - 15 * MINUTE, diaper.startMillis)
        assertNull("no receipt follows the sheet", state.sheet)
    }

    @Test
    fun tappingTheSelectedChipAgainClearsIt() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onDiaperPoop(true)
        home.viewModel.onPoopColor(PoopColor.PALE)
        home.viewModel.onPoopColor(null)
        home.viewModel.onPoopTexture(PoopTexture.HARD)
        home.viewModel.onPoopTexture(null)
        val sheet = home.diaperSheet()
        assertNull(sheet.color)
        assertNull(sheet.texture)
    }

    @Test
    fun turningPoopOffDropsColourAndTexture() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperPoop(true)
        home.viewModel.onPoopColor(PoopColor.RED)
        home.viewModel.onPoopTexture(PoopTexture.WATERY)
        home.viewModel.onDiaperPoop(false)
        val sheet = home.diaperSheet()
        assertNull(sheet.color)
        assertNull(sheet.texture)
        // Colour needs poop.
        home.viewModel.onPoopColor(PoopColor.GREEN)
        assertNull(home.diaperSheet().color)

        home.viewModel.onDiaperSave()
        home.settle()
        val diaper = home.entries().single()
        assertTrue(diaper.pee)
        assertFalse(diaper.poop)
        assertNull(diaper.poopColor)
        assertNull(diaper.poopTexture)
    }

    @Test
    fun cancelLogsNothing() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onDiaperPee(true)
        home.viewModel.onSheetDismiss()
        val state = home.settle()
        assertNull(state.sheet)
        assertNull(state.card)
        assertTrue(home.entries().isEmpty())
    }

    @Test
    fun theSameDiaperTwiceIsDeduplicated() = homeTest { home ->
        home.viewModel.onDiaper()
        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperSave()
        home.settle()
        home.closeReceipt()
        home.clock.advanceBy(3_000)
        home.viewModel.onDiaper()
        home.viewModel.onDiaperPee(true)
        home.viewModel.onDiaperSave()
        val state = home.settle()
        assertEquals(1, home.entries().size)
        assertNull("no sheet follows", state.sheet)
        assertEquals(NoticeVariant.Duplicate(home.entries().single()), state.card!!.variant)
    }
}
