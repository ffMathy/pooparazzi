package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for FirstRun: what a new install shows before Home, on the real phone app. */
class FirstRunChecks : Phone() {
    @Test
    fun `a new install starts with your baby`() {
        waitFor("Your baby")
        assertShows("Skip for now")
        assertGone("Start sleep")
        assertGone("No account needed")
        assertGone("Continue with Google")
        assertGone("Back up to Google Firebase?")
    }

    @Test
    fun `skipping both ends on Home`() {
        onIntroduction()
        skipIntroduction()
        assertShows("Baby")
        assertShows("Last fed, No feeds yet")
        assertGone("Your baby")
        assertGone("Welcome to Pooparazzi")
    }
}
