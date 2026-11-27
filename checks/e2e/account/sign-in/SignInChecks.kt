// ail-derived-from: SignIn@160d9b1f131cc848
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for SignIn: the Account screen signed out and the email link, on the real phone app and the Firebase emulators. */
class SignInChecks : BackedUpPhone() {
    /** The Welcome answered without an account, the first run skipped, then Settings › Account: signed out. */
    private fun accountScreenSignedOut() {
        waitFor("No account needed", 20_000)
        scrollDownAndTap("Continue without an account")
        skipTheFirstRun()
        openTheAccountScreen("Not signed in")
        waitFor("Email me a sign-in link")
    }

    @Test
    fun `the account screen signed out`() {
        waitFor("No account needed", 20_000)
        scrollDownAndTap("Continue without an account")
        skipTheFirstRun()
        tap("Settings")
        waitFor("Not signed in", 20_000)
        assertShowsTextsInOrder("Account", "Not signed in", "Your log is only on this phone. Sign in to back it up.")
        tap("Not signed in")
        waitFor("Email me a sign-in link")
        assertShowsTextsInOrder(
            "Account",
            "Without an account, everything you log is saved only on this phone and your watch. If the phone is lost or reset, the log is lost with it.",
            "You can create an account at any time in Settings. Everything logged until then is backed up as soon as you do, and you can share it with your family.",
            "Continue with Google",
            "or use email",
            "Email",
            "Email me a sign-in link",
            "By continuing you agree to the privacy policy.",
        )
        assertGone("Password")
        assertGone("Forgot password?")
        assertGone("Create account")
    }

    @Test
    fun `asking for a sign-in link`() {
        accountScreenSignedOut()
        val before = signInEmailsTo("parent@example.com").size
        typeEmail("parent@example.com")
        scrollDownAndTap("Email me a sign-in link")
        waitFor("Check your email", 30_000)
        waitUntil("the email to parent@example.com", 20_000) { signInEmailsTo("parent@example.com").size == before + 1 }
        assertShowsTextsInOrder(
            "Check your email",
            "We sent a sign-in link to parent@example.com. Tap the link in it on this phone.",
            "Send again",
            "Use another email",
        )
        tap("Send again")
        waitUntil("a second email to parent@example.com", 30_000) { signInEmailsTo("parent@example.com").size == before + 2 }
        assertShows("Check your email")
        tap("Use another email")
        waitFor("Email me a sign-in link")
        assertShows("Email")
        assertGone("Check your email")
    }

    @Test
    fun `an address that doesn't look right`() {
        accountScreenSignedOut()
        typeEmail("parent")
        scrollDownAndTap("Email me a sign-in link")
        waitFor("Enter a valid email address")
        assertGone("Check your email")
        pass(1_000)
        assertEquals(0, signInEmailsTo("parent").size)
    }
}
