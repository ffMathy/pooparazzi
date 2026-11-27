// ail-derived-from: SignInLink@7bb2152c54a69556
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for SignInLink: the emailed sign-in link opened in the app, on the real phone app and the Firebase emulators. */
class SignInLinkChecks : BackedUpPhone() {
    /** The Welcome answered without an account, the first run skipped, then Settings › Account: signed out. */
    private fun accountScreenSignedOut() {
        waitFor("No account needed", 20_000)
        scrollDownAndTap("Continue without an account")
        skipTheFirstRun()
        openTheAccountScreen("Not signed in")
        waitFor("Email me a sign-in link")
    }

    @Test
    fun `the link in the email signs in`() {
        accountScreenSignedOut()
        val before = signInEmailsTo("parent@example.com").size
        typeEmail("parent@example.com")
        scrollDownAndTap("Email me a sign-in link")
        waitFor("Check your email", 30_000)
        openLink(waitForSignInLink("parent@example.com", before))
        waitFor("Back up to Google Firebase?", 30_000)
        assertShowsPart("You're signed in as parent@example.com.")
        agreeToTheBackup()
        waitFor("Start sleep", 30_000)
        waitUntil("Home's cloud icon", 30_000) { showsAt("Backed up · {time}") }
        pass(1_000)
        tap("Settings")
        waitFor("parent@example.com", 20_000)
        waitUntil("the Account row's status", 30_000) { showsAt("Backed up · {time}") }
        assertGone("Not signed in")
    }

    @Test
    fun `a link opened on a phone that didn't ask for it`() {
        waitFor("No account needed", 20_000)
        anotherPhoneAsksForASignInLink("parent@example.com")
        openLink(waitForSignInLink("parent@example.com"))
        waitFor("To finish signing in, enter the email address the link was sent to.", 20_000)
        assertShows("No account needed")
        assertShows("Sign in")
        assertGone("Email me a sign-in link")
        typeEmail("parent@example.com")
        scrollDownAndTap("Sign in")
        waitFor("Back up to Google Firebase?", 30_000)
        assertShowsPart("You're signed in as parent@example.com.")
    }

    @Test
    fun `a link that was used`() {
        waitFor("No account needed", 20_000)
        typeEmail("parent@example.com")
        scrollDownAndTap("Email me a sign-in link")
        waitFor("Check your email", 30_000)
        val link = waitForSignInLink("parent@example.com")
        openLink(link)
        agreeToTheBackup()
        skipTheFirstRun()
        tap("Settings")
        signOutFromSettings("parent@example.com")
        openLink(link)
        waitFor("To finish signing in, enter the email address the link was sent to.", 20_000)
        typeEmail("parent@example.com")
        scrollDownAndTap("Sign in")
        waitFor("That link has expired or was already used. Send a new one.", 30_000)
        assertGone("Back up to Google Firebase?")
    }
}
