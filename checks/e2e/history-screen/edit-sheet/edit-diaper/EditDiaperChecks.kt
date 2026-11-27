package com.ffmathy.pooparazzi.checks

import java.time.LocalTime
import org.junit.Test

/** End-to-end check for EditDiaper: a diaper's Pee, Poop, colour, texture and time, on the real phone app. */
class EditDiaperChecks : Phone() {
    private val nine = LocalTime.of(9, 0)

    /** A pee diaper yesterday at 9:00 AM, its edit sheet open from History. */
    private fun editYesterdaysPee() {
        openHistory()
        addPastDiaper(daysAgo(1), nine, pee = true)
        tap("Previous day")
        waitFor("Pee, ${clockOf(nine)}")
        tap("Pee, ${clockOf(nine)}")
        waitFor("Edit diaper")
        pass(500)
    }

    @Test
    fun `a pee diaper's sheet`() {
        freshInstall()
        editYesterdaysPee()
        assertShows("Time day, Yesterday")
        assertShows("Time, ${clockOf(nine)}")
        assertShows("Pee")
        assertShows("Poop")
        assertGone("Colour")
    }

    @Test
    fun `ticking Poop shows its colour and texture`() {
        freshInstall()
        editYesterdaysPee()
        tapTop("Poop")
        waitFor("Colour")
        for (colour in listOf("Yellow", "Green", "Brown", "Black", "Red", "Pale")) assertShows(colour)
        assertShows("Texture")
        for (texture in listOf("Watery", "Seedy", "Mushy", "Formed", "Hard")) assertShows(texture)
        tapTop("Save")
        waitUntilGone("Edit diaper")
        waitFor("Pee + poop")
        assertShows(clockOf(nine))
    }

    @Test
    fun `a diaper with nothing ticked is a plain change`() {
        freshInstall()
        editYesterdaysPee()
        tapTop("Pee")
        tapTop("Save")
        waitUntilGone("Edit diaper")
        waitFor("Diaper, ${clockOf(nine)}")
    }
}
