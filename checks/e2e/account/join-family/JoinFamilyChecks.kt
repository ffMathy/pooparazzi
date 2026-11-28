// ail-derived-from: JoinFamily@9e572cd59712a146
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for JoinFamily: invite links opened in the app, on the real phone app and the Firebase emulators. */
open class JoinFamilyChecks : BackedUpPhone() {
    /**
     * parent@example.com signs in on a new install and invites someone; then partner@example.com signs in on the phone
     * (the checks run one phone) and agrees: Home. Returns the invite link, not opened yet.
     */
    private fun partnerWithAnInvite(): String {
        newInstallSignedInAs("parent@example.com")
        val link = inviteSomeone()
        signOutFromSettings("parent@example.com")
        signInByEmail("partner@example.com")
        agreeToTheBackup()
        waitFor("Start sleep", 30_000)
        pass(1_000)
        return link
    }

    private fun joinScreenFor(link: String) {
        openLink(link)
        waitFor("Family invite", 20_000)
        waitUntilGone("Opening the invite…", 30_000)
    }

    @Test
    fun `an invite asks first`() {
        val link = partnerWithAnInvite()
        openLink(link)
        waitFor("Family invite", 20_000)
        waitFor("Join parent@example.com's family?", 30_000)
        assertShowsTextsInOrder(
            "Family invite",
            "Join parent@example.com's family?",
            "Your babies and your log on this phone are added to the family's and shared with its members. You'll see theirs too.",
            "Your first baby and the family's first baby become one baby.",
            "Join",
            "Not now",
        )
        assertShows("Close")
    }

    @Test
    fun `joining`() {
        joinScreenFor(partnerWithAnInvite())
        tap("Join")
        waitFor("You're in the family. Your log and babies are being shared.", 30_000)
        assertShows("Done")
        tap("Done")
        waitFor("Start sleep", 20_000)
        pass(1_000)
        tap("Settings")
        waitFor("Started the family", 30_000)
        assertShowsTextsInOrder("Family", "partner@example.com", "You", "parent@example.com", "Started the family")
    }

    @Test
    fun `not now`() {
        joinScreenFor(partnerWithAnInvite())
        tap("Not now")
        waitFor("Start sleep", 20_000)
        pass(1_000)
        tap("Settings")
        waitFor("You · Started the family", 30_000)
        assertShowsTextsInOrder("Family", "partner@example.com", "You · Started the family", "Only you so far. Invite someone to share the log and the babies.")
        assertGone("parent@example.com")
    }

    @Test
    fun `an invite opened while signed out`() {
        newInstallSignedInAs("parent@example.com")
        val link = inviteSomeone()
        signOutFromSettings("parent@example.com")
        openLink(link)
        waitFor("Sign in to join the family.", 20_000)
        assertShows("Email me a sign-in link")
        signInByEmail("partner@example.com")
        agreeToTheBackup()
        waitFor("Join parent@example.com's family?", 30_000)
        assertShows("Join")
    }

    @Test
    fun `an invite that was used`() {
        val link = familyOfTwo()
        joinScreenFor(link)
        waitFor("This invite has expired or was already used. Ask for a new one.", 30_000)
        assertShows("Done")
        assertGone("Join")
    }

    @Test
    fun `an invite from your own family`() {
        familyOfTwo()
        val own = inviteSomeone()
        goBack()
        waitFor("Start sleep", 20_000)
        joinScreenFor(own)
        waitFor("You're already in this family.", 30_000)
        assertShows("Done")
        assertGone("Join")
    }

    @Test
    fun `a link that isn't an invite`() {
        newInstallSignedInAs("partner@example.com")
        openLink("https://demo-pooparazzi.web.app/join/short")
        waitFor("This invite link isn't valid.", 30_000)
        assertShows("Family invite")
        assertShows("Done")
    }
}
