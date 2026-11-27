// ail-derived-from: LeavingTheFeedSheet@60150664ab6b2d2e
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for LeavingTheFeedSheet: Cancel, back, a tap outside, and Home's cooldown after a feed. */
open class LeavingTheFeedSheetChecks : Phone() {
    @Test
    fun `cancel logs nothing`() {
        freshInstall()
        feedSheet()
        tap("Cancel")
        waitUntilGone("Just the time")
        assertShows("Last fed, No feeds yet")
        tap("Feed")
        waitFor("Just the time")
    }

    @Test
    fun `going back logs nothing`() {
        freshInstall()
        feedSheet()
        goBack()
        waitUntilGone("Just the time")
        assertShows("Last fed, No feeds yet")
    }

    @Test
    fun `a tap outside the sheet logs nothing`() {
        freshInstall()
        feedSheet()
        tapOutsideTheSheet()
        waitUntilGone("Just the time")
        assertShows("Last fed, No feeds yet")
    }

    @Test
    fun `Home rests a moment after a feed is logged`() {
        freshInstall()
        feedSheet()
        tap("Food")
        // tap() lets 300 ms go by: Feed comes within Home's 600 ms after the sheet closed.
        tap("Feed")
        assertGone("Just the time")
        pass(1_000)
        tap("Feed")
        waitFor("Just the time")
    }
}
