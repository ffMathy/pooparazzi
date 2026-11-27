// ail-derived-from: WatchSide@871c05d941a10ee7
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WatchSide: Side after Breast, Left and Right in tick order, on the real watch app. */
class WatchSideChecks : Watch() {
    /** Feed, Breast: Side, once it takes taps. Returns the time of the Feed tap. */
    private fun openSide(): String {
        tapStarting("Feed. ")
        val stamp = shownTime("Feed · ")
        pass(600)
        tap("Breast")
        waitFor("Which side?")
        pass(600)
        return stamp
    }

    @Test
    fun `the side screen`() {
        freshInstall()
        val stamp = openSide()
        assertShows("Breast, $stamp")
        assertShows("Left")
        assertShows("Right")
        assertGone("Left, first")
        assertGone("Right, first")
        assertShows("No side")
        assertShows("Save")
    }

    @Test
    fun `ticking both sides records their order`() {
        freshInstall()
        val stamp = openSide()
        tap("Left")
        tap("Right")
        assertShows("Left, first")
        assertShows("Right, second")
        assertShows("1")
        assertShows("2")
        assertShows("Left → Right")
        assertShows("Left then right")
        tap("Save")
        waitFor("Breast at $stamp")
        // Logged keeps the line whole: no-break spaces around the arrow.
        assertShows("Left\u00A0→\u00A0Right")
        assertShows("Left then right")
    }

    @Test
    fun `unticking a side moves the other one first`() {
        freshInstall()
        openSide()
        tap("Left")
        tap("Right")
        tap("Left, first")
        waitFor("Right, first")
        assertGone("Left, first")
        assertGone("Left → Right")
        assertShows("Right")
    }

    @Test
    fun `saving a breast feed with no side`() {
        freshInstall()
        val stamp = openSide()
        tap("Save")
        waitFor("Breast at $stamp")
        assertGone("No side")
        assertGone("Left")
        assertGone("Right")
    }

    @Test
    fun `swiping back from side returns to feed type`() {
        freshInstall()
        openSide()
        swipeToDismiss()
        waitFor("Just the time")
        assertGone("Which side?")
        swipeToDismiss()
        waitFor("Feed. No feeds yet")
    }
}
