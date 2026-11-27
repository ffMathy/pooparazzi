// ail-derived-from: Sleep@46c93df58d2296fc
package com.ffmathy.pooparazzi.checks

import org.junit.Test

class SleepShots : Phone() {

    @Test
    fun `a second tap within two seconds changes nothing`() {
        freshInstall()

        // Given a sleep started a second ago, its receipt closed with Done
        val before = clock(0)
        tap("Start sleep")
        val after = clock(0)
        waitFor("Done")
        pass(300)
        tap("Done")
        waitUntilGone("Done")
        // Past the 600 ms in which Home ignores taps after a receipt closes,
        // still within two seconds of the tap that started the sleep.
        pass(700)

        // When the parent taps Woke up
        tap("Woke up")

        // Then the sleep keeps running and the sleep button still reads "Woke up"
        assertShows("Woke up")
        assertGone("Start sleep")

        // And the notice reads "Asleep" over "Already asleep since" the sleep's start
        assertShows("Asleep")
        val guardLine =
            if (shows("Already asleep since $before")) "Already asleep since $before"
            else "Already asleep since $after"
        assertShows(guardLine)

        // And two seconds later the notice is gone
        pass(2000)
        assertGone(guardLine)
        assertShows("Woke up")

        saveScreen("a-second-tap-within-two-seconds-changes-nothing")
    }
}
