// ail-derived-from: BottleFeed@f376bdc8e9c401c3
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** A phone in the United Kingdom: English, 24-hour clock, millilitres. The checks' phone is otherwise a US one. */
private const val UK_PHONE = "en-rGB-w390dp-h844dp-xxhdpi"

/** End-to-end check for BottleFeed: the Bottle step, Milk or Formula, the amount and its unit, on the real phone app. */
open class BottleFeedChecks : Phone() {
    private fun bottleStep(): String {
        val at = feedSheet()
        tap("Bottle")
        waitFor("Amount · Optional")
        return at
    }

    @Test
    @Config(qualifiers = UK_PHONE)
    fun `the Bottle step`() {
        freshInstall()
        val at = bottleStep()
        assertEquals("Bottle · $at", sheetTitle())
        for (nudge in listOf("−15m", "−10m", "−5m")) assertCanTap(nudge)
        assertShows("Contents · Optional")
        assertFalse(isChosen("Milk"))
        assertFalse(isChosen("Formula"))
        assertShows("Amount · Optional")
        assertEquals("No amount", bottleAmount())
        assertCanTap("Log feed")
    }

    @Test
    @Config(qualifiers = UK_PHONE)
    fun `picking the contents and the amount`() {
        freshInstall()
        bottleStep()
        tap("Formula")
        slideAmountTo(12)
        assertTrue(isChosen("Formula"))
        assertFalse(isChosen("Milk"))
        assertEquals("120 ml", bottleAmount())
        slideAmountTo(30)
        assertEquals("300 ml", bottleAmount())
    }

    @Test
    fun `tapping the picked contents again clears it`() {
        freshInstall()
        bottleStep()
        tap("Formula")
        assertTrue(isChosen("Formula"))
        tap("Formula")
        assertFalse(isChosen("Formula"))
        assertFalse(isChosen("Milk"))
    }

    @Test
    @Config(qualifiers = UK_PHONE)
    fun `logging a bottle with its contents and amount`() {
        freshInstall()
        val at = bottleStep()
        tap("Formula")
        slideAmountTo(12)
        tap("Log feed")
        waitUntilGone("Log feed")
        assertGone("Undo")
        waitForLastFeed("bottle, formula", at)
        assertShows("$at · Bottle")
        tap("History")
        waitFor("Add entry")
        assertShows("Bottle · Formula")
        assertShows("$at · 120 ml")
    }

    @Test
    @Config(qualifiers = UK_PHONE)
    fun `logging a bottle with nothing picked`() {
        freshInstall()
        val at = bottleStep()
        tap("Log feed")
        waitUntilGone("Log feed")
        waitForLastFeed("bottle", at)
        assertShows("$at · Bottle")
    }

    @Test
    fun `a phone in the United States picks ounces`() {
        freshInstall()
        bottleStep()
        slideAmountTo(8)
        assertEquals("4 fl oz", bottleAmount())
        slideAmountTo(30)
        assertEquals("10 fl oz", bottleAmount())
    }
}
