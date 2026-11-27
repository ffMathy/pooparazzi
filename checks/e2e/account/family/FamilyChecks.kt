// ail-derived-from: Family@07754186835d08f8
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for Family: Settings › Family with one and two members, on the real phone app and the Firebase emulators. */
open class FamilyChecks : BackedUpPhone() {
    @Test
    fun `a family of one`() {
        newInstallSignedInAs("parent@example.com")
        tap("Settings")
        waitFor("You · Started the family", 30_000)
        assertShowsTextsInOrder(
            "Account",
            "parent@example.com",
            "Family",
            "parent@example.com",
            "You · Started the family",
            "Only you so far. Invite someone to share the log and the babies.",
            "Invite someone",
        )
        assertGone("Leave family")
        assertGone("Remove")
    }

    @Test
    fun `inviting someone`() {
        newInstallSignedInAs("parent@example.com")
        tap("Settings")
        waitFor("Invite someone", 30_000)
        scrollDownAndTap("Invite someone")
        var text: String? = null
        waitUntil("the share sheet", 30_000) { text = sharedText(); text != null }
        val shared = text!!
        assertTrue(shared, Regex("Join my family on Pooparazzi to share our baby log: https://demo-pooparazzi\\.web\\.app/join/[A-Za-z0-9_-]{22} \\(works once, for 6 days\\)").matches(shared))
    }

    @Test
    fun `a family of two`() {
        familyOfTwo()
        tap("Settings")
        waitFor("Started the family", 30_000)
        assertShowsTextsInOrder(
            "Family",
            "partner@example.com",
            "You",
            "parent@example.com",
            "Started the family",
            "Invite someone",
            "Leave family",
        )
        assertGone("Remove")
        assertGone("Only you so far. Invite someone to share the log and the babies.")
    }

    @Test
    fun `leaving the family`() {
        familyOfTwo()
        tap("Settings")
        waitFor("Leave family", 30_000)
        tapInTopWindow("Leave family")
        waitForInTopWindow("Leave the family?")
        assertShowsTextsInOrder(
            "Leave the family?",
            "You'll stop seeing its log and babies. Your phone keeps its log and starts a new family of your own.",
            "Cancel",
            "Leave",
        )
        tapInTopWindow("Cancel")
        waitUntilGoneInTopWindow("Leave the family?")
        assertShows("Started the family")
        assertShows("parent@example.com")
        tapInTopWindow("Leave family")
        waitForInTopWindow("Leave the family?")
        tapInTopWindow("Leave")
        waitInTopWindow("a family of one", 30_000) { shows("Only you so far. Invite someone to share the log and the babies.") }
        assertShowsTextsInOrder("Family", "partner@example.com", "You · Started the family", "Only you so far. Invite someone to share the log and the babies.")
        assertGone("parent@example.com")
        assertGone("Leave family")
    }

    @Test
    fun `removing a member`() {
        familyOfTwo()
        tap("Settings")
        signOutFromSettings("partner@example.com")
        signInByEmail("parent@example.com")
        agreeToTheBackup()
        waitFor("Start sleep", 30_000)
        pass(1_000)
        tap("Settings")
        waitFor("Remove partner@example.com", 30_000)
        assertShowsTextsInOrder("Family", "parent@example.com", "You · Started the family", "partner@example.com", "Remove", "Invite someone", "Leave family")
        tapInTopWindow("Remove partner@example.com")
        waitForInTopWindow("Remove partner@example.com?")
        assertShowsTextsInOrder(
            "Remove partner@example.com?",
            "They'll stop seeing the family's log from now on. What's already on their phone stays there.",
            "Cancel",
            "Remove",
        )
        tapInTopWindow("Remove")
        waitInTopWindow("a family of one", 30_000) { shows("Only you so far. Invite someone to share the log and the babies.") }
        assertGone("partner@example.com")
        assertGone("Remove partner@example.com")
    }
}
