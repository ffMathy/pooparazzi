// ail-derived-from: EditSheet@2374a7a7995e9da5
package com.ffmathy.pooparazzi.checks

import java.time.LocalTime
import org.junit.Test

/** End-to-end check for EditSheet: a sleep's blocks, nudges, "was", validation, Cancel and Save, on the real phone app. */
open class EditSheetChecks : Phone() {
    private val one = LocalTime.of(13, 0)
    private val two = LocalTime.of(14, 0)

    /** A sleep yesterday from 1:00 PM to 2:00 PM, its edit sheet open from History. */
    private fun editYesterdaysSleep() {
        openHistory()
        addPastSleep(daysAgo(1), one, daysAgo(1), two)
        tap("Previous day")
        waitFor("Sleep · 1h 00m")
        tap("Sleep · 1h 00m")
        waitFor("Edit sleep")
        pass(500)
    }

    @Test
    fun `a History row opens its entry`() {
        freshInstall()
        editYesterdaysSleep()
        assertCanTap("Delete")
        assertShows(historyDay(daysAgo(1)))
        assertShows("Fell asleep day, Yesterday")
        assertShows("Fell asleep, ${clockOf(one)}")
        assertShows("Woke up day, Yesterday")
        assertShows("Woke up, ${clockOf(two)}")
        for (block in listOf("Fell asleep", "Woke up")) {
            for (nudge in listOf("5 minutes earlier", "1 minute earlier", "1 minute later", "5 minutes later")) assertShows("$block, $nudge")
        }
        assertShows("Duration 1h 00m")
        assertCanTap("Cancel")
        assertCanTap("Save")
    }

    @Test
    fun `a nudge shows what the time was`() {
        freshInstall()
        editYesterdaysSleep()
        tapTop("Fell asleep, 5 minutes earlier")
        val earlier = clockOf(one.minusMinutes(5))
        assertShows("Fell asleep, $earlier, was ${clockOf(one)}")
        assertShows("was ${clockOf(one)}")
        assertShows("Duration 1h 05m · was 1h 00m")
        tapTop("Save")
        waitUntilGone("Edit sleep")
        waitFor("Sleep · 1h 05m")
        assertShows("$earlier – ${clockOf(two)}")
    }

    @Test
    fun `nudges add up and back`() {
        freshInstall()
        editYesterdaysSleep()
        tapTop("Fell asleep, 5 minutes earlier")
        repeat(5) { tapTop("Fell asleep, 1 minute later") }
        assertShows("Fell asleep, ${clockOf(one)}")
        assertGone("was ${clockOf(one)}")
        assertShows("Duration 1h 00m")
    }

    @Test
    fun `waking up before falling asleep can't be saved`() {
        freshInstall()
        editYesterdaysSleep()
        setTime("Woke up", LocalTime.of(12, 0))
        waitFor("Waking up must come after falling asleep.")
        assertShows("Duration <1m · was 1h 00m")
        assertCannotTap("Save")
    }

    @Test
    fun `Cancel keeps the entry as it was`() {
        freshInstall()
        editYesterdaysSleep()
        tapTop("Fell asleep, 5 minutes earlier")
        tapTop("Cancel")
        waitUntilGone("Edit sleep")
        assertShows("Sleep · 1h 00m")
        assertShows("${clockOf(one)} – ${clockOf(two)}")
    }
}
