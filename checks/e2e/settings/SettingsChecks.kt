package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Settings: its sections in order, the footer and Back, on the real phone app. */
class SettingsChecks : Phone() {
    @Test
    fun `the sections of settings`() {
        freshInstall()
        openSettings()
        assertShows("Settings")
        assertCanTap("Back")
        assertShowsInOrder("Account", "Baby", "Appearance", "Language", "Watch", "Notifications", "About")
        assertShowsInOrder("Made with ❤️ by a father in Aarhus, Denmark", "Pooparazzi is open source")
        assertCanTap("Source code on GitHub")
        assertCanTap("The maker on LinkedIn")
    }

    @Test
    fun `back returns to home`() {
        freshInstall()
        openSettings()
        tap("Back")
        waitFor("Start sleep")
        assertGone("Appearance")
    }
}
