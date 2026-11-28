// ail-derived-from: WatchFeed@54c33c8e881959d5
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WatchFeed: Feed type, Food, Just the time and Already logged, on the real watch app. */
open class WatchFeedChecks : Watch() {
    @Test
    fun `the feed type screen`() {
        freshInstall()
        tapStarting("Feed. ")
        val stamp = shownTime("Feed · ")
        for (type in listOf("Breast", "Bottle", "Food", "Just the time")) assertShows(type)
        assertCannotTap("Breast")
        assertCannotTap("Food")
        pass(600)
        assertCanTap("Breast")
        assertCanTap("Food")
        assertShows("Feed · $stamp")
    }

    @Test
    fun `logging food on the watch`() {
        freshInstall()
        tapStarting("Feed. ")
        val stamp = shownTime("Feed · ")
        pass(600)
        tap("Food")
        waitFor("Food at $stamp")
        assertShows("Undo")
        closeLogged()
        waitForSince(stamp, "the Feed row") { listOf("Feed. Last feed, food, ${spokenSince(it)}, at $stamp", shortSince(it)) }
    }

    @Test
    fun `logging just the time of a feed`() {
        freshInstall()
        tapStarting("Feed. ")
        val stamp = shownTime("Feed · ")
        pass(600)
        scrollTo("Just the time")
        tap("Just the time")
        waitFor("Feed at $stamp")
        assertShows("Undo")
    }

    @Test
    fun `the same feed twice within five seconds`() {
        freshInstall()
        val first = logFood()
        waitOutLoggedInertStart()
        tapTheBackground()
        waitUntilGone("Undo")
        tapStarting("Feed. ")
        waitFor("Just the time")
        pass(600)
        tap("Food")
        waitFor("Already logged")
        assertShows("Food at $first")
        closeLogged()
        // One feed: Main still reads the first feed's time.
        assertShowsPart("Feed. Last feed, food, ")
        assertShowsPart(", at $first")
    }

    @Test
    fun `swiping back from feed type logs nothing`() {
        freshInstall()
        openFeedType()
        swipeToDismiss()
        waitFor("Feed. No feeds yet")
        assertGone("Just the time")
        assertShows("No feeds yet")
    }
}
