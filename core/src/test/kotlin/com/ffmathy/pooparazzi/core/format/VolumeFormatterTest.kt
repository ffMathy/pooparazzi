package com.ffmathy.pooparazzi.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Formula amounts in millilitres, or fluid ounces where those are used, and the amount slider's steps. */
class VolumeFormatterTest {
    private val danish = Locale.forLanguageTag("da-DK")
    private val millilitres = VolumeFormatter(VolumeUnit.MILLILITRES, Locale.UK)
    private val ounces = VolumeFormatter(VolumeUnit.FLUID_OUNCES, Locale.US)

    @Test
    fun theUSLiberiaAndMyanmarUseOunces() {
        assertEquals(VolumeUnit.FLUID_OUNCES, VolumeUnit.forRegion("US"))
        assertEquals(VolumeUnit.FLUID_OUNCES, VolumeUnit.forRegion("lr"))
        assertEquals(VolumeUnit.FLUID_OUNCES, VolumeUnit.forRegion("MM"))
        assertEquals(VolumeUnit.MILLILITRES, VolumeUnit.forRegion("DK"))
        assertEquals(VolumeUnit.MILLILITRES, VolumeUnit.forRegion("GB"))
        assertEquals(VolumeUnit.MILLILITRES, VolumeUnit.forRegion(null))
    }

    @Test
    fun amountsAreShownInTheUnit() {
        assertEquals("120 ml", millilitres.format(120))
        assertEquals("4 fl oz", ounces.format(118))
        assertEquals("4.5 fl oz", ounces.format(133))
        assertEquals("4,5 fl oz", VolumeFormatter(VolumeUnit.FLUID_OUNCES, danish).format(133))
        assertEquals("never shown as nothing", "0.5 fl oz", ounces.format(5))
    }

    @Test
    fun theSliderStartsAtNoAmount() {
        assertNull(millilitres.amountAt(0))
        assertNull(ounces.amountAt(0))
        assertEquals(0, millilitres.stepOf(null))
        assertEquals(0, ounces.stepOf(null))
    }

    @Test
    fun millilitresGoUpIn10sTo300() {
        assertEquals(30, millilitres.steps)
        assertEquals(10, millilitres.amountAt(1))
        assertEquals(300, millilitres.amountAt(30))
        assertEquals(300, millilitres.amountAt(99))
        assertEquals(12, millilitres.stepOf(120))
        assertEquals(12, millilitres.stepOf(123))
        assertEquals("a tiny amount still shows as an amount", 1, millilitres.stepOf(1))
    }

    @Test
    fun ouncesGoUpInHalvesTo10() {
        assertEquals(20, ounces.steps)
        assertEquals(15, ounces.amountAt(1))
        assertEquals(118, ounces.amountAt(8))
        assertEquals(296, ounces.amountAt(20))
        assertEquals(8, ounces.stepOf(120))
        assertEquals("4 fl oz", ounces.format(ounces.amountAt(8)!!))
    }

    @Test
    fun theAmountChipsDefaultToThreeCommonAmounts() {
        assertEquals(listOf(60, 90, 120), millilitres.quickAmounts(null))
        assertEquals(listOf("2 fl oz", "3 fl oz", "4 fl oz"), ounces.quickAmounts(null).map(ounces::format))
    }

    @Test
    fun theAmountChipsCentreOnTheLastBottle() {
        assertEquals(listOf(90, 120, 150), millilitres.quickAmounts(120))
        assertEquals("to the nearest step", listOf(90, 120, 150), millilitres.quickAmounts(123))
        assertEquals(listOf("3.5 fl oz", "4.5 fl oz", "5.5 fl oz"), ounces.quickAmounts(133).map(ounces::format))
    }

    @Test
    fun theAmountChipsStayOnTheScale() {
        assertEquals(listOf(10, 40, 70), millilitres.quickAmounts(10))
        assertEquals(listOf(240, 270, 300), millilitres.quickAmounts(300))
        assertEquals(listOf(240, 270, 300), millilitres.quickAmounts(5_000))
        assertEquals(listOf("0.5 fl oz", "1.5 fl oz", "2.5 fl oz"), ounces.quickAmounts(5).map(ounces::format))
        assertEquals(listOf("8 fl oz", "9 fl oz", "10 fl oz"), ounces.quickAmounts(400).map(ounces::format))
    }

    @Test
    fun theNumberAndTheUnitApart() {
        assertEquals("120", millilitres.numberOf(120))
        assertEquals("4.5", ounces.numberOf(133))
        assertEquals("4,5", VolumeFormatter(VolumeUnit.FLUID_OUNCES, danish).numberOf(133))
        assertEquals("ml", millilitres.unitSymbol)
        assertEquals("fl oz", ounces.unitSymbol)
    }
}
