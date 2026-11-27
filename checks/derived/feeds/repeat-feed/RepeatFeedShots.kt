// ail-derived-from: RepeatFeed@85c2aad195abb756
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatFeedShots : Phone() {

    private fun logFromTheFeedSheet(option: String) {
        tap("Feed")
        waitFor("Just the time")
        tap(option)
        waitUntilGone("Just the time")
    }

    @Test
    fun `the same feed again within five seconds`() {
        freshInstall()

        // Given Food logged from the Feed sheet a moment ago
        logFromTheFeedSheet("Food")

        // When the parent logs Food again from the Feed sheet, within five seconds of the first
        logFromTheFeedSheet("Food")

        // Then the notice reads "Food" over "Already logged at" the first feed's time
        waitUntil("the repeat notice shows") { showsPart("Already logged at") }
        assertShows("Food")
        assertShowsAt("Already logged at {time}", 0)

        // And two seconds later the notice is gone
        pass(2500)
        waitUntil("the repeat notice is gone") { !showsPart("Already logged at") }
        assertFalse(showsPart("Already logged at"))

        // And Today still counts 1 feed
        waitUntil("Today counts 1 feed") { showsPart("1 feed") }
        assertTrue(showsPart("1 feed"))
        assertFalse(showsPart("2 feeds"))

        saveScreen("the-same-feed-again-within-five-seconds")
    }

    @Test
    fun `another type within five seconds is a feed of its own`() {
        freshInstall()

        // Given Food logged from the Feed sheet a moment ago
        logFromTheFeedSheet("Food")

        // When the parent logs Just the time from the Feed sheet, within five seconds of the first
        logFromTheFeedSheet("Just the time")

        // Then no notice shows, and Today counts 2 feeds
        waitUntil("Today counts 2 feeds") { showsPart("2 feeds") }
        assertFalse(showsPart("Already logged at"))
        assertTrue(showsPart("2 feeds"))

        saveScreen("another-type-within-five-seconds-is-a-feed-of-its-own")
    }
}
