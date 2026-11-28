// ail-derived-from: CoverWidget@d5b40d342897885d
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for CoverWidget: the 4×2 widget on a Samsung flip's cover screen, on the real phone app. */
open class CoverWidgetChecks : Phone() {
    /** Its scenarios end on the phone's home screen, so a derived check pictures the widget there, not the app. */
    override fun pictureTheScreen(name: String) = pictureTheHomeScreen(name)

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
