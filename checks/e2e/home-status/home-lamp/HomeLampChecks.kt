package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for HomeLamp: the Lamp's states and taps, on the real phone app. */
class HomeLampChecks : Phone() {
    @Test
    fun `the Lamp before the first feed`() {
        freshInstall()
        assertShows("Last fed")
        assertShows("–")
        assertShows("No feeds yet")
        assertShows("Last fed, No feeds yet")
        tap("No feeds yet")
        pass(800)
        assertGone("Edit feed")
    }

    @Test
    fun `the Lamp just after a feed`() {
        freshInstall()
        val fed = feedJustTheTime()
        waitUntil("the Lamp's last feed") { shows("Last feed, ${spokenSince(fed)}, at $fed") }
        assertShows("Last fed")
        if (clockMinutesBetween(fed, clock()) == 0L) assertShows("just now")
        assertShows(fed)
    }

    @Test
    fun `the Lamp counts the time since the last feed`() {
        freshInstall()
        val fed = breastFeed(List(5) { "−15m" }, listOf("Left", "Right"))
        waitUntil("the Lamp's breast feed") {
            shows("Last feed, breast, left then right, ${spokenSince(fed)}, at $fed")
        }
        val minutes = clockMinutesBetween(fed, clock())
        assertShows("${minutes / 60}")
        assertShows("${minutes % 60}")
        assertShows("$fed · Left → Right")
    }

    @Test
    fun `tapping the Lamp opens the last feed`() {
        freshInstall()
        val fed = feedJustTheTime(listOf("−15m"))
        tap("Last fed")
        waitFor("Edit feed")
        assertShows("Time, $fed")
        assertCanTap("Delete")
        assertCanTap("Cancel")
        assertCanTap("Save")
    }

    @Test
    fun `tapping the Lamp while asleep opens the running sleep`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        tap("Asleep")
        waitFor("Edit sleep")
        assertShows("Fell asleep, $start")
        assertShows("Woke up, Still asleep")
        assertCanTap("End now")
    }
}
