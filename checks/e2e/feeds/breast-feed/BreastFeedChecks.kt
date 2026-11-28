// ail-derived-from: BreastFeed@617f56cc6964116c
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for BreastFeed: the Breast step, the side order and Log feed, on the real phone app. */
open class BreastFeedChecks : Phone() {
    private fun breastStep() {
        tap("Breast")
        waitFor("Log feed")
    }

    @Test
    fun `the Breast step`() {
        freshInstall()
        val now = feedSheet()
        val at = nudgeBack(20)
        breastStep()
        assertEquals("Breast · $at", sheetTitle())
        assertShows("$at · was $now")
        for (nudge in listOf("−15m", "−10m", "−5m", "+5m")) assertCanTap(nudge)
        assertShows("Tick the sides in the order you fed. Optional.")
        assertFalse(isTicked("Left"))
        assertFalse(isTicked("Right"))
        assertShows("No side")
        assertCanTap("Log feed")
    }

    @Test
    fun `ticking the sides in order`() {
        freshInstall()
        feedSheet()
        breastStep()
        tap("Right")
        tap("Left")
        assertTrue(isTicked("Right, first"))
        assertTrue(isTicked("Left, second"))
        assertShows("Right → Left")
        assertShows("Right then left")
    }

    @Test
    fun `unticking a side takes it out of the order`() {
        freshInstall()
        feedSheet()
        breastStep()
        tap("Right")
        tap("Left")
        tap("Right, first")
        assertFalse(isTicked("Right"))
        assertShows("Left, first")
        tap("Right")
        assertTrue(isTicked("Left, first"))
        assertTrue(isTicked("Right, second"))
        assertShows("Left → Right")
    }

    @Test
    fun `logging a breast feed with its order`() {
        freshInstall()
        feedSheet()
        val at = nudgeBack(20)
        breastStep()
        tap("Left")
        tap("Right")
        tap("Log feed")
        waitUntilGone("Log feed")
        assertGone("Undo")
        waitForLastFeed("breast, left then right", at)
        assertShows("$at · Left → Right")
    }

    @Test
    fun `logging a breast feed with no side`() {
        freshInstall()
        val at = feedSheet()
        breastStep()
        tap("Log feed")
        waitUntilGone("Log feed")
        waitForLastFeed("breast", at)
        assertShows("$at · Breast")
    }
}
