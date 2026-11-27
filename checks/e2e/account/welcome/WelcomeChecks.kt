// ail-derived-from: Welcome@bc885500011ad3aa
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Welcome: the first run of a build with a backup (on the Firebase emulators), on the real phone app. */
class WelcomeChecks : BackedUpPhone() {
    @Test
    fun `the welcome on a first run with backup`() {
        waitFor("No account needed", 20_000)
        assertShowsTextsInOrder(
            "No account needed",
            "Pooparazzi works fully without an account. Choose where your log is kept. You can change your mind later.",
            "Only on this phone",
            "Everything you log is saved only on this phone and your watch. If the phone is lost or reset, the log is lost with it.",
            "You can switch to cloud backup at any time in Settings › Account. Everything logged until then is backed up as soon as you do.",
            "Continue without an account",
            "Back up to the cloud",
            "Sign in to back up your log and share it with your family.",
            "Continue with Google",
            "or use email",
            "Email",
            "Email me a sign-in link",
            "By continuing you agree to the privacy policy.",
        )
        assertGone("Start sleep")
        assertGone("Your baby")
    }

    @Test
    fun `continuing without an account`() {
        waitFor("No account needed", 20_000)
        scrollDownAndTap("Continue without an account")
        waitFor("Your baby")
        assertShows("Names and birth dates stay on this phone and your watch.")
        assertGone("No account needed")
        skipTheFirstRun()
        assertGone("No account needed")
        tap("Settings")
        waitFor("Not signed in", 20_000)
        assertShows("Your log is only on this phone. Sign in to back it up.")
    }

    @Test
    fun `signing in on the welcome`() {
        waitFor("No account needed", 20_000)
        val before = signInEmailsTo("parent@example.com").size
        typeEmail("parent@example.com")
        scrollDownAndTap("Email me a sign-in link")
        waitFor("Check your email", 30_000)
        assertShows("No account needed")
        openLink(waitForSignInLink("parent@example.com", before))
        waitFor("Back up to Google Firebase?", 30_000)
        agreeToTheBackup()
        waitFor("Your baby", 30_000)
        assertShows("Names and birth dates are shared with your family and kept in your backup.")
        assertGone("No account needed")
    }
}
