// ail-derived-from: SignedIn@30819036c84dce0d
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for SignedIn: the Account screen signed in, signing out and deleting, on the real phone app and the Firebase emulators. */
class SignedInChecks : BackedUpPhone() {
    /** A new install signed in as parent@example.com, on its Account screen once the first backup is done. */
    private fun accountScreenSignedIn() {
        newInstallSignedInAs("parent@example.com")
        openTheAccountScreen("parent@example.com")
        waitFor("Delete account…", 20_000)
        waitUntil("the first backup", 30_000) { showsAt("Backed up · {time}") }
    }

    @Test
    fun `the account screen signed in`() {
        accountScreenSignedIn()
        val shown = recentClocks().first { shows("Backed up · $it") }
        assertShowsTextsInOrder(
            "Signed in as",
            "parent@example.com",
            "Backed up · $shown",
            "Back up now",
            "Sign out",
            "Signing out keeps the log on this phone.",
            "Delete account…",
        )
    }

    @Test
    fun `signing out`() {
        accountScreenSignedIn()
        scrollDownAndTap("Sign out")
        waitFor("Email me a sign-in link", 30_000)
        assertShows("Email")
        assertGone("Signed in as")
        goBack()
        waitFor("Not signed in", 20_000)
        assertShows("Your log is only on this phone. Sign in to back it up.")
    }

    @Test
    fun `deleting the account asks first`() {
        accountScreenSignedIn()
        scrollDownAndTap("Delete account…")
        waitFor("Delete account?")
        assertShowsTextsInOrder(
            "Delete account?",
            "This deletes your account and your place in the family. If you're the family's only member, its backup is deleted too. The log on this phone stays.",
            "We'll email you a link to confirm. Open it on this phone.",
            "Cancel",
            "Email me a link",
        )
        assertGone("You'll sign in with Google again to confirm.")
        tap("Cancel")
        waitUntilGone("Delete account?")
        assertShows("Delete account…")
        assertEquals(listOf("parent@example.com"), accountsInTheBackend())
    }

    @Test
    fun `deleting the account with the emailed link`() {
        accountScreenSignedIn()
        scrollDownAndTap("Delete account…")
        waitFor("Delete account?")
        val before = signInEmailsTo("parent@example.com").size
        scrollDownAndTap("Email me a link")
        waitFor("Check your email. Tapping the link in it on this phone deletes your account.", 30_000)
        assertShows("Cancel")
        assertShows("Send again")
        assertEquals(listOf("parent@example.com"), accountsInTheBackend())
        openLink(waitForSignInLink("parent@example.com", before))
        waitFor("Email me a sign-in link", 30_000)
        assertGone("Signed in as")
        waitUntil("the account gone from the backend", 20_000) { accountsInTheBackend().isEmpty() }
    }
}
