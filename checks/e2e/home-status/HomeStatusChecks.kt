package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertFalse
import org.junit.Test

/** End-to-end check for HomeStatus: what Home shows on a new install, on the real phone app. */
class HomeStatusChecks : Phone() {
    @Test
    fun `a new install has nothing to show yet`() {
        freshInstall()
        assertShows("Baby")
        for (button in listOf("History", "Statistics", "Settings")) assertCanTap(button)
        assertShows("Last fed")
        assertShows("–")
        assertShows("No feeds yet")
        assertGone("Awake")
        assertFalse("no diaper row:\n${screen()}", showsPart("Last diaper"))
        assertShows("Today: 0 feeds, 0 pee, 0 poop, less than a minute sleep")
        assertShows("<1m")
        for (forecast in listOf("next feed", "next poop", "sleeps again", "wakes up")) {
            assertFalse("no forecast line:\n${screen()}", showsPart(forecast))
        }
    }
}
