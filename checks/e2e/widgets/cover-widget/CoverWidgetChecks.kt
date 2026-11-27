package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for CoverWidget: the 4×2 widget on a Samsung flip's cover screen, on the real phone app. */
class CoverWidgetChecks : Phone() {
    @Test
    fun `the cover widget shows the three buttons`() {
        freshInstall()
        val widget = placeWidget("Cover screen", 352, 339)
        assertEquals(listOf("Log feed", "Feed", "Log diaper", "Diaper", "Start sleep", "Start sleep"), widgetReads(widget))
    }

    @Test
    fun `Start sleep on the cover widget logs at once`() {
        freshInstall()
        val widget = placeWidget("Cover screen", 352, 339)
        startSleepOnWidget(widget)
        assertWidgetShows(widget, "Undo")
        waitForWidget(widget, "Woke up")
    }
}
