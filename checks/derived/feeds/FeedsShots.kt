// ail-derived-from: Feeds@944d5eee84053f9c
package com.ffmathy.pooparazzi.checks

import org.junit.Test

class FeedsShots : Phone() {

    /** Opens the Feed sheet from Home and waits until it shows. */
    private fun openFeedSheet() {
        tap("Feed")
        waitFor("Cancel")
    }

    @Test
    fun `opening the Feed sheet`() {
        freshInstall()

        openFeedSheet()

        assertShows("Feed")
        assertShowsAt("{time}", 0)
        assertCanTap("Cancel")

        assertCanTap("−15m")
        assertCanTap("−10m")
        assertCanTap("−5m")
        assertShows("+5m")
        assertCannotTap("+5m")

        assertCanTap("Breast")
        assertShows("Then the sides, if you like")
        assertCanTap("Bottle")
        assertShows("Then milk or formula and the amount, if you like")
        assertCanTap("Food")
        assertCanTap("Just the time")

        saveScreen("opening-the-feed-sheet")
    }

    @Test
    fun `nudging the feed earlier`() {
        freshInstall()
        openFeedSheet()

        tap("−15m")
        tap("−5m")

        // The stamp is the time now (the minute may have turned since Feed was tapped).
        waitUntil("the time reads 20 minutes ago, was the time now") {
            (-1..0).any { d -> shows("${clock(d - 20)} · was ${clock(d)}") }
        }
        assertCanTap("+5m")

        saveScreen("nudging-the-feed-earlier")
    }

    @Test
    fun `logging food`() {
        freshInstall()
        openFeedSheet()
        tap("−15m")

        tap("Food")

        waitUntilGone("Cancel")
        assertGone("Undo")
        assertGone("Done")

        waitFor("Last fed")
        var feedTime: String? = null
        waitUntil("the Lamp shows the feed's time and Food") {
            feedTime = (-16..-15).map { clock(it) }.firstOrNull { shows("$it · Food") }
            feedTime != null
        }
        val shownFeed = feedTime!!
        val minutes = clockMinutesBetween(shownFeed, clock(0))
        assertShows("Last feed, food, ${spokenMinutes(minutes)} ago, at $shownFeed")

        assert(showsPart("1 feed")) { "Today should count 1 feed" }

        saveScreen("logging-food")
    }

    @Test
    fun `logging just the time`() {
        freshInstall()
        openFeedSheet()

        tap("Just the time")

        waitUntilGone("Cancel")
        waitFor("Last fed")
        assert(showsPart("just now")) { "The Lamp should read just now" }
        assertShowsAt("{time}", 0)
        for (d in -1..0) {
            val t = clock(d)
            assertGone("$t · Food")
            assertGone("$t · Breast")
            assertGone("$t · Bottle")
        }

        saveScreen("logging-just-the-time")
    }
}
