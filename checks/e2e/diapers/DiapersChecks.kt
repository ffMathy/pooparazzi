package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for Diapers: the Diaper sheet, Pee and Poop, Save, the nudges and closing it. */
class DiapersChecks : Phone() {
    private fun assertNoDiaperRow() = assertTrue("no diaper row:\n${screen()}", !showsPart("Last diaper"))

    @Test
    fun `opening the Diaper sheet`() {
        freshInstall()
        val now = diaperSheet()
        assertTrue("the sheet opened at the time of the tap", now in recentClocks())
        assertEquals("Diaper · $now", sheetTitle())
        assertShows(now)
        assertCanTap("Cancel")
        for (nudge in listOf("−15m", "−10m", "−5m")) assertCanTap(nudge)
        assertCannotTap("+5m")
        assertFalse(isTicked("Pee"))
        assertFalse(isTicked("Poop"))
        assertCanTap("Save")
    }

    @Test
    fun `saving a pee`() {
        freshInstall()
        val at = peeSaved()
        assertGone("Undo")
        assertShows("just now · $at")
        waitForLastDiaper("pee", at)
        assertTrue(todayCounts(), todayCounts().contains(", 1 pee, 0 poop,"))
    }

    @Test
    fun `saving pee and poop, nudged earlier`() {
        freshInstall()
        val now = diaperSheet()
        tap("Pee")
        tap("Poop")
        tap("−5m")
        tap("−5m")
        val at = sheetTime()
        assertEquals(10, clockMinutesBetween(at, now))
        assertShows("$at · was $now")
        saveTheDiaper()
        assertShows("Both")
        waitForLastDiaper("pee and poop", at)
        assertTrue(todayCounts(), todayCounts().contains(", 1 pee, 1 poop,"))
    }

    @Test
    fun `saving with nothing ticked logs a plain change`() {
        freshInstall()
        val at = diaperSheet()
        saveTheDiaper()
        assertShows("just now · $at")
        waitForLastDiaper("", at)
        assertTrue(todayCounts(), todayCounts().contains(", 0 pee, 0 poop,"))
    }

    @Test
    fun `cancel logs nothing`() {
        freshInstall()
        diaperSheet()
        tap("Pee")
        tap("Cancel")
        waitUntil("the sheet closed") { sheetTitle() == null }
        assertNoDiaperRow()
        tap("Diaper")
        waitFor("Save")
        assertFalse(isTicked("Pee"))
    }

    @Test
    fun `going back logs nothing`() {
        freshInstall()
        diaperSheet()
        tap("Pee")
        goBack()
        waitUntil("the sheet closed") { sheetTitle() == null }
        assertNoDiaperRow()
    }
}
