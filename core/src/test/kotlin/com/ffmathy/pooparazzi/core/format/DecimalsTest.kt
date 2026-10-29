package com.ffmathy.pooparazzi.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Averages with one decimal in the language's own separator (V1_ADDITIONS §8). */
class DecimalsTest {
    private val danish = Locale.forLanguageTag("da-DK")

    @Test
    fun englishUsesAPointAndDanishAComma() {
        assertEquals("6.7", formatTenths(67, Locale.UK))
        assertEquals("6,7", formatTenths(67, danish))
        assertEquals("0.5", formatTenths(5, Locale.US))
    }

    @Test
    fun theDecimalIsAlwaysShown() {
        assertEquals("0.0", formatTenths(0, Locale.UK))
        assertEquals("12,0", formatTenths(120, danish))
    }

    @Test
    fun largeNumbersAreNotGrouped() {
        assertEquals("1234.5", formatTenths(12345, Locale.UK))
        assertEquals("1234,5", formatTenths(12345, danish))
    }
}
