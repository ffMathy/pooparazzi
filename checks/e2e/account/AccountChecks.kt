package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Account: a build without a backup (no Firebase configuration), on the real phone app. */
class AccountChecks : Phone() {
    @Test
    fun `a build without a backup`() {
        onYourBaby()
        assertShows("Your baby")
        assertGone("No account needed")
        assertGone("Continue with Google")
        assertGone("Email me a sign-in link")
        assertShows("Names and birth dates stay on this phone and your watch.")
        tap("Skip for now")
        waitFor("Welcome to Pooparazzi")
        pass(500)
        skipIntroduction()
        openSettingsFromHome()
        assertShowsTextsInOrder("Account", "Backup isn't set up in this build. Everything is saved on this phone.")
        assertCannotTap("Backup isn't set up in this build. Everything is saved on this phone.")
        assertGone("Family")
        assertGone("Not signed in")
    }
}
