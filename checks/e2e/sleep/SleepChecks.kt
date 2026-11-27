// ail-derived-from: Sleep@46c93df58d2296fc
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Sleep: the sleep button and its guard, on the real phone app. */
open class SleepChecks : Phone() {
    @Test
    fun `a second tap within two seconds changes nothing`() {
        freshInstall()
        tap("Start sleep")
        waitFor("Sleep started")
        pass(500)
        tap("Done")
        // About 1.7 s after Start sleep: past Home's 600 ms cooldown after the receipt, inside the two-second guard.
        pass(600)
        tap("Woke up")
        assertShows("Woke up, end sleep")
        assertShows("Asleep")
        assertShowsAt("Already asleep since {time}")
        assertGone("Sleep cancelled")
        pass(2_100)
        assertGone("Already asleep since ${clock()}")
        assertGone("Already asleep since ${clock(-1)}")
        assertShows("Woke up, end sleep")
    }
}
