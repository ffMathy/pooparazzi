package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for SwitchBaby: Home's switcher, the Switch baby sheet and each baby's own log, on the real phone app. */
class SwitchBabyChecks : Phone() {
    /** Two babies, Noa (current) and Ida, made in Settings; ends on Home. */
    private fun noaAndIdaOnHome() {
        freshInstall()
        openSettings()
        noaAndIdaInSettings()
        goBack()
        waitForInDialog("Noa. Switch baby")
        pass(1_000)
    }

    private fun openTheSheet() {
        tapInDialog("Noa. Switch baby")
        waitForInDialog("Switch baby")
    }

    @Test
    fun `the switch baby sheet`() {
        noaAndIdaOnHome()
        openTheSheet()
        assertShows("Noa")
        assertShows("Ida")
        assertCanTap("Cancel")
        val chosen = selected()
        assertTrue("Noa chosen, Ida not: $chosen", "Noa" in chosen && "Ida" !in chosen)
    }

    @Test
    fun `switching to the other baby`() {
        noaAndIdaOnHome()
        openTheSheet()
        tapInDialog("Ida")
        waitForInDialog("Ida. Switch baby")
        waitUntilGoneInDialog("Switch baby")
        assertGone("Noa. Switch baby")
    }

    @Test
    fun `cancelling keeps the baby`() {
        noaAndIdaOnHome()
        openTheSheet()
        tapInDialog("Cancel")
        waitUntilGoneInDialog("Switch baby")
        assertShows("Noa. Switch baby")
    }

    @Test
    fun `each baby has its own sleep`() {
        noaAndIdaOnHome()
        switchTo("Ida")
        startSleepReceipt()
        tap("Done")
        waitUntilGoneInDialog("Sleep started")
        waitOutTheGuard()
        assertShows("Woke up, end sleep")
        switchTo("Noa")
        waitForInDialog("Start sleep")
        assertGone("Woke up, end sleep")
        switchTo("Ida")
        waitForInDialog("Woke up, end sleep")
        assertGone("Start sleep")
    }

    @Test
    fun `history names the current baby`() {
        noaAndIdaOnHome()
        switchTo("Ida")
        tap("History")
        waitForInDialog("Add entry")
        assertShowsInOrder("History", "Ida")
        assertGone("Noa")
    }
}
