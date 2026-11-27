// ail-derived-from: Sleep@46c93df58d2296fc
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertFalse
import org.junit.Test

class SleepShots : Phone() {

    @Test
    fun `a second tap within two seconds changes nothing`() {
        freshInstall()

        // Given a sleep started a second ago, its receipt closed with Done
        tap("Start sleep")
        waitFor("Done")
        tap("Done")
        waitUntilGone("Done")
        // Home ignores taps on the sleep button for 600 ms after a receipt closes,
        // so wait that out while staying inside the two-second guard of the start tap.
        pass(800)

        // When the parent taps Woke up
        tap("Woke up")

        // Then the sleep keeps running and the sleep button still reads "Woke up"
        assertShows("Woke up")
        assertGone("Start sleep")

        // And the notice reads "Asleep" over "Already asleep since" the sleep's start
        assertShows("Asleep")
        assertShowsAt("Already asleep since {time}", 0)

        // And two seconds later the notice is gone
        pass(2200)
        assertFalse(
            "the guard notice should be gone two seconds later",
            showsPart("Already asleep since"),
        )
        assertShows("Woke up")
        assertGone("Start sleep")

        saveScreen("a-second-tap-within-two-seconds-changes-nothing")
    }
}
