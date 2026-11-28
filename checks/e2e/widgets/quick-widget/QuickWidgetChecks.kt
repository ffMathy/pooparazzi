// ail-derived-from: QuickWidget@058a728f52c399af
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for QuickWidget: the 3×1 widget at its sizes, on the real phone app. */
open class QuickWidgetChecks : Phone() {
    /** Its scenarios end on the phone's home screen, so a derived check pictures the widget there, not the app. */
    override fun pictureTheScreen(name: String) = pictureTheHomeScreen(name)

    @Test
    fun `the quick widget shows three labelled buttons`() {
        freshInstall()
        val widget = placeQuickWidget(260)
        assertEquals(listOf("Log feed", "Feed", "Log diaper", "Diaper", "Start sleep", "Start sleep"), widgetReads(widget))
    }

    @Test
    fun `the sleep button logs in place`() {
        freshInstall()
        val widget = placeQuickWidget(260)
        tapWidget(widget, "Start sleep")
        waitForWidget(widget, "Woke up, end sleep")
        assertWidgetShows(widget, "Woke up")
        waitUntil("Home asleep") { showsPart("Asleep, ") }
        assertWidgetGone(widget, "Undo")
        if (widgetShowsPart(widget, "Sleep started")) throw AssertionError("a receipt on the 3×1:\n${widgetScreen(widget)}")
    }

    @Test
    fun `a narrow quick widget shows icons only`() {
        freshInstall()
        val widget = placeQuickWidget(180)
        assertEquals(listOf("Log feed", "Log diaper", "Start sleep"), widgetReads(widget))
    }

    @Test
    fun `Feed on the quick widget opens the Feed sheet`() {
        freshInstall()
        val widget = placeQuickWidget(260)
        tapWidget(widget, "Log feed")
        assertFeedSheetOpenNow()
    }
}
