package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Language: System default, English and Dansk in Settings, on the real phone app. */
class LanguageChecks : Phone() {
    @Test
    fun `a new install follows the phone's language`() {
        freshInstall()
        openSettings()
        assertShowsInOrder("Language", "System default", "English", "Dansk")
        waitUntil("System default chosen") { selected().let { "System default" in it && "English" !in it && "Dansk" !in it } }
    }

    @Test
    fun `choosing dansk`() {
        freshInstall()
        openSettings()
        scrollAndTap("Dansk")
        waitUntil("Dansk chosen") { selected().let { "Dansk" in it && "System default" !in it } }
    }
}
