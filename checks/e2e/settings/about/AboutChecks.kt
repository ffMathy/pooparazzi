package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for About: Settings › About and the font licences, on the real phone app. */
class AboutChecks : Phone() {
    @Test
    fun `what about offers`() {
        freshInstall()
        openSettings()
        assertShowsInOrder("About", "Pooparazzi", "Version 0.1.0")
        assertShowsInOrder(
            "Introduction", "How to use Pooparazzi, step by step",
            "Privacy policy", "What is stored, where, and your rights",
            "Download my data", "The whole log and the babies' names as a ZIP file",
            "Import data", "Merge a downloaded ZIP file into the log",
            "Font licences", "Google Sans Flex and Atkinson Hyperlegible Next, SIL Open Font License 1.1",
        )
        for (row in listOf("Introduction", "Privacy policy", "Download my data", "Import data", "Font licences")) assertCanTap(row)
    }

    @Test
    fun `the font licences`() {
        freshInstall()
        openSettings()
        scrollAndTap("Font licences")
        waitFor("Google Sans Flex")
        assertShows("Font licences")
        assertShowsPart("Copyright 2015 The Google Sans Flex Authors")
        scrollUntilShown("Atkinson Hyperlegible Next")
        assertShows("Atkinson Hyperlegible Next")
        assertGone("Appearance")
        tap("Back")
        waitFor("Appearance")
    }
}
