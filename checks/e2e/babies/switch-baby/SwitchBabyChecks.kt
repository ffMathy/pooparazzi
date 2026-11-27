// ail-derived-from: SwitchBaby@f4b1e2cdc51f3e2d
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for SwitchBaby: Home's switcher, the Switch baby sheet and each baby's own log, on the real phone app. */
class SwitchBabyChecks : Phone() {
    /** Two babies, Noa (current) and Ida, made in Settings; ends on Home. */
    private fun noaAndIdaOnHome() {
        freshInstall()
        openSettingsFromHome()
        noaAndIdaInSettings()
        goBack()
        waitForInTopWindow("Noa. Switch baby")
        pass(1_000)
    }

    private fun openTheSheet() {
        tapInTopWindow("Noa. Switch baby")
        waitForInTopWindow("Switch baby")
    }

    @Test
    fun `the switch baby sheet`() {
        noaAndIdaOnHome()
        openTheSheet()
        assertShows("Noa")
        assertShows("Ida")
        assertCanTap("Cancel")
        val chosen = chosenOnScreen()
        assertTrue("Noa chosen, Ida not: $chosen", "Noa" in chosen && "Ida" !in chosen)
    }

    @Test
    fun `switching to the other baby`() {
        noaAndIdaOnHome()
        openTheSheet()
        tapInTopWindow("Ida")
        waitForInTopWindow("Ida. Switch baby")
        waitUntilGoneInTopWindow("Switch baby")
        assertGone("Noa. Switch baby")
    }

    @Test
    fun `cancelling keeps the baby`() {
        noaAndIdaOnHome()
        openTheSheet()
        tapInTopWindow("Cancel")
        waitUntilGoneInTopWindow("Switch baby")
        assertShows("Noa. Switch baby")
    }

    @Test
    fun `each baby has its own sleep`() {
        noaAndIdaOnHome()
        switchBabyTo("Ida")
        startSleepReceipt()
        tap("Done")
        waitUntilGoneInTopWindow("Sleep started")
        waitOutTheGuard()
        assertShows("Woke up, end sleep")
        switchBabyTo("Noa")
        waitForInTopWindow("Start sleep")
        assertGone("Woke up, end sleep")
        switchBabyTo("Ida")
        waitForInTopWindow("Woke up, end sleep")
        assertGone("Start sleep")
    }

    @Test
    fun `history names the current baby`() {
        noaAndIdaOnHome()
        switchBabyTo("Ida")
        tap("History")
        waitForInTopWindow("Add entry")
        assertShowsTextsInOrder("History", "Ida")
        assertGone("Noa")
    }
}
