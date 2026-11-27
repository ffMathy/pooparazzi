// ail-derived-from: EditFeed@faad01f2b6b381a3
package com.ffmathy.pooparazzi.checks

import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for EditFeed: a feed's type, side, contents and time, on the real phone app. */
class EditFeedChecks : Phone() {
    private val eleven = LocalTime.of(11, 0)

    /** A Bottle feed yesterday at 11:00 AM, its edit sheet open from History. */
    private fun editYesterdaysBottle() {
        openHistory()
        addPastFeed(daysAgo(1), eleven, "Bottle")
        tap("Previous day")
        waitFor("Bottle")
        tap("Bottle")
        waitFor("Edit feed")
        pass(500)
    }

    @Test
    fun `a bottle feed's sheet`() {
        freshInstall()
        editYesterdaysBottle()
        assertShows("Type · Optional")
        assertTrue("Bottle picked:\n${screen()}", isPicked("Bottle"))
        for (part in listOf("Contents · Optional", "Milk", "Formula", "Amount · Optional")) assertShows(part)
        assertShows("Time day, Yesterday")
        assertShows("Time, ${clockOf(eleven)}")
    }

    @Test
    fun `changing a feed's type to breast`() {
        freshInstall()
        editYesterdaysBottle()
        tapTop("Breast")
        waitFor("Side · Optional")
        assertGone("Contents · Optional")
        tapTop("Left")
        tapTop("Save")
        waitUntilGone("Edit feed")
        waitFor("Breast · Left")
        assertShows(clockOf(eleven))
    }

    @Test
    fun `tapping the picked type clears it`() {
        freshInstall()
        editYesterdaysBottle()
        tapTop("Bottle")
        assertFalse("no type picked:\n${screen()}", isPicked("Bottle"))
        assertGone("Contents · Optional")
        tapTop("Save")
        waitUntilGone("Edit feed")
        waitFor("Feed, ${clockOf(eleven)}")
    }

    @Test
    fun `moving a feed's time`() {
        freshInstall()
        editYesterdaysBottle()
        tapTop("Time, 5 minutes later")
        val later = clockOf(eleven.plusMinutes(5))
        assertShows("Time, $later, was ${clockOf(eleven)}")
        tapTop("Save")
        waitUntilGone("Edit feed")
        waitFor(later)
        assertShows("Bottle")
    }
}
