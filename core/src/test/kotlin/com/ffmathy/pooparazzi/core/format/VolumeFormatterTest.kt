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
}
