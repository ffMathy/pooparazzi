// ail-derived-from: Feeds@e7367d3212ebac5c
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for Feeds: the Feed sheet, its nudges, Food and Just the time. */
open class FeedsChecks : Phone() {
    @Test
    fun `opening the Feed sheet`() {
        freshInstall()
        val now = feedSheet()
        assertTrue("the sheet opened at the time of the tap", now in recentClocks())
        assertEquals("Feed · $now", sheetTitle())
        assertShows("Feed")
        assertShows(now)
        assertCanTap("Cancel")
        for (nudge in listOf("−15m", "−10m", "−5m")) assertCanTap(nudge)
        assertCannotTap("+5m")
        assertCanTap("Breast")
        assertShows("Then the sides, if you like")
        assertCanTap("Bottle")
        assertShows("Then milk or formula and the amount, if you like")
        assertCanTap("Food")
        assertCanTap("Just the time")
    }

    @Test
    fun `nudging the feed earlier`() {
        freshInstall()
        val now = feedSheet()
        tap("−15m")
        tap("−5m")
        val at = sheetTime()
        assertEquals(20, clockMinutesBetween(at, now))
        assertShows("$at · was $now")
        assertCanTap("+5m")
    }

    @Test
    fun `logging food`() {
        freshInstall()
        feedSheet()
        val at = nudgeBack(15)
        tap("Food")
        waitUntilGone("Just the time")
        assertGone("Undo")
        assertShows("Last fed")
        waitForLastFeed("food", at)
        assertShows("$at · Food")
        assertTrue(todayCounts(), todayCounts().startsWith("Today: 1 feed,"))
    }

    @Test
    fun `logging just the time`() {
        freshInstall()
        val at = feedSheet()
        tap("Just the time")
        waitUntilGone("Just the time")
        assertGone("Undo")
        assertShows("Last fed")
        waitForLastFeed("", at)
        assertShows(at)
        assertGone("$at · Food")
    }
}
