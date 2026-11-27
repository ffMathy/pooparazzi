// ail-derived-from: BackupConsent@e9f22a7792eefad9
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for BackupConsent: the consent before anything is uploaded, on the real phone app and the Firebase emulators. */
class BackupConsentChecks : BackedUpPhone() {
    private fun consentOnANewInstall() {
        waitFor("No account needed", 20_000)
        signInByEmail("parent@example.com")
        waitFor("Back up to Google Firebase?", 30_000)
    }

    private fun nothingInTheBackend() {
        assertEquals("users", 0, documentsInTheBackend("users"))
        assertEquals("families", 0, documentsInTheBackend("families"))
        assertEquals("invites", 0, documentsInTheBackend("invites"))
    }

    @Test
    fun `the consent after signing in`() {
        consentOnANewInstall()
        assertShowsTextsInOrder(
            "Back up to Google Firebase?",
            "You're signed in as parent@example.com. Nothing has been uploaded yet. Before anything is, please agree to this:",
            "What is uploaded",
            "Your log (feeds, diapers and sleep), your babies' names and any birth dates you give, and your email address. A log like this can say something about your baby's health.",
            "Who sees it",
            "You and the members of your family: everyone in it sees the family's log, the babies' names and the members' email addresses.",
            "Where",
            "Google Firebase. The database is in the EU; Google processes it for us and uses it for nothing else.",
            "Changing your mind",
            "Sign out to upload nothing more, leave the family, or delete your account (Settings › Account) to delete what is stored.",
            "Details are in the privacy policy.",
            "I agree",
            "Not now, sign out",
        )
        pass(2_000)
        nothingInTheBackend()
    }

    @Test
    fun `not now signs out`() {
        consentOnANewInstall()
        scrollDownAndTap("Not now, sign out")
        waitFor("Your baby", 30_000)
        assertShows("Names and birth dates stay on this phone and your watch.")
        assertGone("Back up to Google Firebase?")
        skipTheFirstRun()
        tap("Settings")
        waitFor("Not signed in", 20_000)
        nothingInTheBackend()
    }

    @Test
    fun `agreeing starts the backup`() {
        consentOnANewInstall()
        agreeToTheBackup()
        waitUntil("the family in the backend", 30_000) { documentsInTheBackend("families") == 1 && documentsInTheBackend("users") == 1 }
        skipTheFirstRun()
        tap("Settings")
        waitFor("You · Started the family", 30_000)
        assertShowsTextsInOrder("Family", "parent@example.com", "You · Started the family")
    }
}
