// ail-derived-from: TopBar@b8921dc8f0c4bb0b
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for TopBar: the baby's name, the watch icon and the three buttons, on the real phone app. */
open class TopBarChecks : Phone() {
    @Test
    fun `the top bar of a new install`() {
        freshInstall()
        assertShows("Baby")
        for (button in listOf("History", "Statistics", "Settings")) assertCanTap(button)
        assertShows("Syncing with watch…")
    }

    @Test
    fun `the top bar opens History, Statistics and Settings`() {
        freshInstall()
        tap("History")
        waitFor("Previous day")
        assertShows("History")
        goBack()
        waitFor("Start sleep")
        pass(1_000)
        tap("Statistics")
        waitFor("Statistics start as soon as something is logged.")
        goBack()
        waitFor("Start sleep")
        pass(1_000)
        tap("Settings")
        waitUntilGone("Start sleep")
        assertShows("Settings")
        goBack()
        waitFor("Start sleep")
    }
}
