// ail-derived-from: ReviewPrompt@5cdadf317452b1ab
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for ReviewPrompt: a new install is never asked for a rating, on the real phone app. */
open class ReviewPromptChecks : Phone() {
    @Test
    fun `a new install is not asked`() {
        freshInstall()
        startSleepReceipt()
        tap("Done")
        waitUntilGone("Sleep started")
        waitOutTheGuard()
        wokeUpReceipt()
        tap("Done")
        waitUntilGone("Undo")
        // Home is idle: well past the 1.5 s the prompt would wait.
        pass(3_000)
        assertShows("Start sleep")
        assertGone("Enjoying Pooparazzi?")
        assertGone("Rate on Google Play")
    }
}
