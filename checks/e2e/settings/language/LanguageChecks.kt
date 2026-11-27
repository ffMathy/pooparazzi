package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Language: System default, English and Dansk in Settings, on the real phone app. */
class LanguageChecks : Phone() {
    @Test
    fun `a new install follows the phone's language`() {
        freshInstall()
        openSettingsFromHome()
        assertShowsTextsInOrder("Language", "System default", "English", "Dansk")
        waitUntil("System default chosen") { chosenOnScreen().let { "System default" in it && "English" !in it && "Dansk" !in it } }
    }

    @Test
    fun `choosing dansk`() {
        freshInstall()
        openSettingsFromHome()
        scrollDownAndTap("Dansk")
        waitUntil("Dansk chosen") { chosenOnScreen().let { "Dansk" in it && "System default" !in it } }
    }
}
