package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HINT = "Pale, red or black poop can be a reason to call your doctor or health visitor."
private val COLOURS = listOf("Yellow", "Green", "Brown", "Black", "Red", "Pale")
private val TEXTURES = listOf("Watery", "Seedy", "Mushy", "Formed", "Hard")

/** End-to-end check for PoopDetails: the poop's colour, texture and the doctor hint, on the real phone app. */
class PoopDetailsChecks : Phone() {
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
            assertFalse("$option picked", isPicked(option))
        }
        assertGone(HINT)
    }

    @Test
    fun `red poop shows the doctor hint`() {
        freshInstall()
        poopTicked()
        tap("Red")
        assertTrue(isPicked("Red"))
        assertShows(HINT)
        tap("Red")
        assertFalse(isPicked("Red"))
        waitUntilGone(HINT)
    }

    @Test
    fun `black and pale poop show the hint, other colours don't`() {
        freshInstall()
        poopTicked()
        for (colour in listOf("Black", "Pale")) {
            tap(colour)
            assertTrue(isPicked(colour))
            assertShows(HINT)
        }
        for (colour in listOf("Yellow", "Green", "Brown")) {
            tap(colour)
            assertTrue(isPicked(colour))
            waitUntilGone(HINT)
        }
    }

    @Test
    fun `unticking poop clears its details`() {
        freshInstall()
        poopTicked()
        tap("Pale")
        tap("Seedy")
        assertTrue(isPicked("Pale") && isPicked("Seedy"))
        tap("Poop")
        waitUntilGone("Colour")
        assertGone("Texture")
        tap("Poop")
        waitFor("Colour")
        assertFalse(isPicked("Pale"))
        assertFalse(isPicked("Seedy"))
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
        assertShows("Both")
        assertShows("just now · $at")
        assertTrue("no colour on Home:\n${screen()}", !showsPart("Pale") && !showsPart("Seedy"))
        tap("History")
        waitFor("Add entry")
        assertShows("Pee + poop")
        assertShows("$at · Pale · Seedy")
    }
}
