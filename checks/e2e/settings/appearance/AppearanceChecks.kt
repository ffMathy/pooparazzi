package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for Appearance: Auto, Night and Day in Settings, on the real phone app. */
class AppearanceChecks : Phone() {
    private fun assertChosen(chosen: String, vararg notChosen: String) = waitUntil("$chosen chosen, not ${notChosen.toList()}") {
        val now = selected()
        chosen in now && notChosen.none { it in now }
    }

    @Test
    fun `a new install follows auto`() {
        freshInstall()
        openSettings()
        assertShowsInOrder("Appearance", "Auto", "Night", "Day")
        assertChosen("Auto", "Night", "Day")
        assertShows("Auto uses Night in the evening and at night, while a sleep runs, and when the phone is in dark theme.")
    }

    @Test
    fun `choosing night`() {
        freshInstall()
        openSettings()
        scrollAndTap("Night")
        assertChosen("Night", "Auto", "Day")
        goBack()
        waitFor("Start sleep")
        pass(1_000)
        openSettings()
        assertChosen("Night", "Auto", "Day")
    }

    @Test
    fun `choosing day`() {
        freshInstall()
        openSettings()
        scrollAndTap("Night")
        assertChosen("Night", "Auto", "Day")
        scrollAndTap("Day")
        assertChosen("Day", "Auto", "Night")
        assertTrue(shows("Day"))
    }
}
