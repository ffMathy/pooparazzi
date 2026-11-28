// ail-derived-from: RepeatFeed@0bbd1bf1fa853f78
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for RepeatFeed: the same feed twice within five seconds, on the real phone app. */
open class RepeatFeedChecks : Phone() {
    /** The Feed sheet as soon as Home takes taps again after the last log, so the next feed comes within 5 s of it. */
    private fun feedSheetAgain() {
        pass(700)
        tap("Feed")
        waitFor("Just the time")
        pass(500)
    }

    @Test
    fun `the same feed again within five seconds`() {
        freshInstall()
        val first = feedSheet()
        tap("Food")
        feedSheetAgain()
        tap("Food")
        assertShows("Food")
        assertShows("Already logged at $first")
        // The notice stands where Today was; Today is back, unchanged, once it has gone.
        pass(2_100)
        assertGone("Already logged at $first")
        waitUntil("Today back") { todayCounts().isNotEmpty() }
        assertTrue(todayCounts(), todayCounts().startsWith("Today: 1 feed,"))
    }

    @Test
    fun `another type within five seconds is a feed of its own`() {
        freshInstall()
        feedSheet()
        tap("Food")
        feedSheetAgain()
        tap("Just the time")
        waitUntilGone("Just the time")
        assertTrue("no repeat notice:\n${screen()}", !showsPart("Already logged at"))
        waitUntil("2 feeds today") { todayCounts().startsWith("Today: 2 feeds,") }
    }
}
