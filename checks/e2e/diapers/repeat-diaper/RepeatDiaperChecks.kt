package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for RepeatDiaper: the same diaper twice within five seconds, on the real phone app. */
class RepeatDiaperChecks : Phone() {
    @Test
    fun `the same diaper again within five seconds`() {
        freshInstall()
        val first = peeSaved()
        // As soon as Home takes taps again, so the second pee comes within 5 s of the first.
        pass(700)
        tap("Diaper")
        waitFor("Save")
        pass(500)
        tap("Pee")
        tap("Save")
        assertShows("Pee")
        assertShows("Already logged at $first")
        // The notice stands where Today was; Today is back, unchanged, once it has gone.
        pass(2_100)
        waitUntil("Today back") { todayCounts().isNotEmpty() }
        assertTrue(todayCounts(), todayCounts().contains(", 1 pee,"))
    }
}
