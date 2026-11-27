package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Widgets: the 4×2 widget's Feed, Diaper and background open the app, on the real phone app. */
class WidgetsChecks : Phone() {
    @Test
    fun `Feed on a widget opens the Feed sheet`() {
        freshInstall()
        val widget = placeLogWidget()
        tapWidget(widget, "Log feed")
        assertFeedSheetOpenNow()
    }

    @Test
    fun `Diaper on a widget opens the Diaper sheet`() {
        freshInstall()
        val widget = placeLogWidget()
        tapWidget(widget, "Log diaper")
        waitFor("Save")
        val title = windowTitleStartingWith("Diaper · ")
        assertShowsTitleNow(title, "Diaper · ")
        assertShows("Pee")
        assertShows("Poop")
    }

    @Test
    fun `what the sheets log shows on the widget`() {
        freshInstall()
        val widget = placeLogWidget()
        tapWidget(widget, "Log feed")
        waitFor("Food")
        pass(600)
        tap("Food")
        waitUntilGone("Just the time")
        waitOutTheCooldown()
        tapWidget(widget, "Log diaper")
        waitFor("Save")
        pass(600)
        tap("Poop")
        tap("Save")
        waitUntilGone("Save")
        waitForWidget(widget, "Log feed, Last feed just now")
        waitForWidget(widget, "Log diaper, Last diaper just now")
        assertWidgetShows(widget, "just now")
    }

    @Test
    fun `the widget's background opens Home`() {
        freshInstall()
        val widget = placeLogWidget()
        tapWidgetBackground(widget)
        pass(500)
        assertShows("Start sleep")
        assertGone("Bottom Sheet")
        assertGone("Undo")
    }

    private fun assertShowsTitleNow(title: String?, prefix: String) {
        if (title == null || recentClocks().none { title == "$prefix$it" }) throw AssertionError("expected \"$prefix\" and the time now, got \"$title\":\n${screen()}")
    }
}
