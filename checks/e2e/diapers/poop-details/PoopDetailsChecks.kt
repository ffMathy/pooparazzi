// ail-derived-from: PoopDetails@a1fd3f6bf25f05a0
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HINT = "Pale, red or black poop can be a reason to call your doctor or health visitor."
private val COLOURS = listOf("Yellow", "Green", "Brown", "Black", "Red", "Pale")
private val TEXTURES = listOf("Watery", "Seedy", "Mushy", "Formed", "Hard")

/** End-to-end check for PoopDetails: the poop's colour, texture and the doctor hint, on the real phone app. */
open class PoopDetailsChecks : Phone() {
    private fun poopTicked() {
        diaperSheet()
        tap("Poop")
        waitFor("Colour")
    }

    @Test
    fun `poop reveals its colour and texture`() {
        freshInstall()
        diaperSheet()
        assertGone("Colour")
        tap("Poop")
        waitFor("Colour")
        assertShows("Texture")
        for (option in COLOURS + TEXTURES) {
            assertCanTap(option)
            assertFalse("$option picked", isChosen(option))
        }
        assertGone(HINT)
    }

    @Test
    fun `red poop shows the doctor hint`() {
        freshInstall()
        poopTicked()
        tap("Red")
        assertTrue(isChosen("Red"))
        assertShows(HINT)
        tap("Red")
        assertFalse(isChosen("Red"))
        waitUntilGone(HINT)
    }

    @Test
    fun `black and pale poop show the hint, other colours don't`() {
        freshInstall()
        poopTicked()
        for (colour in listOf("Black", "Pale")) {
            tap(colour)
            assertTrue(isChosen(colour))
            assertShows(HINT)
        }
        for (colour in listOf("Yellow", "Green", "Brown")) {
            tap(colour)
            assertTrue(isChosen(colour))
            waitUntilGone(HINT)
        }
    }

    @Test
    fun `unticking poop clears its details`() {
        freshInstall()
        poopTicked()
        tap("Pale")
        tap("Seedy")
        assertTrue(isChosen("Pale") && isChosen("Seedy"))
        tap("Poop")
        waitUntilGone("Colour")
        assertGone("Texture")
        tap("Poop")
        waitFor("Colour")
        assertFalse(isChosen("Pale"))
        assertFalse(isChosen("Seedy"))
    }

    @Test
    fun `saving a poop with its details`() {
        freshInstall()
        val at = diaperSheet()
        tap("Pee")
        tap("Poop")
        waitFor("Colour")
        tap("Pale")
        tap("Seedy")
        saveTheDiaper()
        waitForLastDiaper("pee and poop", at)
        assertShows("Both")
        assertTrue("no colour on Home:\n${screen()}", !showsPart("Pale") && !showsPart("Seedy"))
        tap("History")
        waitFor("Add entry")
        assertShows("Pee + poop")
        assertShows("$at · Pale · Seedy")
    }
}
