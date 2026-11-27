// ail-derived-from: SinceRows@ea47e612e15d3a63
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for SinceRows: Awake or Last fed, the last diaper, and their taps, on the real phone app. */
open class SinceRowsChecks : Phone() {
    @Test
    fun `a diaper shows in the second row`() {
        freshInstall()
        val at = diaperNow(pee = true)
        waitUntil("the diaper row") { shows("Last diaper, pee, ${spokenSince(at)}, at $at") }
        assertShows("Pee")
        if (clockMinutesBetween(at, clock()) == 0L) assertShows("just now · $at")
    }

    @Test
    fun `tapping the diaper row opens the diaper`() {
        freshInstall()
        val at = diaperNow(poop = true)
        tap("Poop")
        waitFor("Edit diaper")
        assertShows("Time, $at")
        for (part in listOf("Pee", "Poop", "Colour", "Texture")) assertShows(part)
    }

    @Test
    fun `after waking the first row reads Awake`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        val end = wokeUpReceipt()
        tap("Done")
        waitUntilGone("Done")
        pass(800)
        waitUntil("the Awake row") { shows("Awake ${spokenMinutes(clockMinutesBetween(end, clock()))}, since $end") }
        assertShows("Awake")
        if (clockMinutesBetween(end, clock()) == 0L) assertShows("<1m · $end")
    }

    @Test
    fun `tapping the Awake row opens the last sleep`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        val end = wokeUpReceipt()
        tap("Done")
        waitUntilGone("Done")
        pass(800)
        tap("Awake")
        waitFor("Edit sleep")
        assertShows("Fell asleep, $start")
        assertShows("Woke up, $end")
    }

    @Test
    fun `while asleep the first row is the last feed`() {
        freshInstall()
        val fed = feedJustTheTime(listOf("−15m"))
        startSleepReceipt()
        tap("Done")
        waitOutTheGuard()
        assertShows("Last fed")
        waitUntil("the Last fed row") { shows("${shownDuration(clockMinutesBetween(fed, clock()))} · $fed") }
        tap("Last fed")
        waitFor("Edit feed")
        assertShows("Time, $fed")
    }
}
