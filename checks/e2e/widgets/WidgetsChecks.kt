// ail-derived-from: Widgets@b32be72879b79053
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Widgets: the 4×2 widget's Feed, Diaper and background open the app, on the real phone app. */
open class WidgetsChecks : Phone() {
    /** Its scenarios end on the phone's home screen or in the app a widget opened: a derived check pictures that. */
    override fun pictureTheScreen(name: String) = pictureTheHomeScreen(name)

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
        val fed = windowTitleStartingWith("Feed · ")!!.removePrefix("Feed · ")
        tap("Food")
        waitUntilGone("Just the time")
        waitOutTheCooldown()
        tapWidget(widget, "Log diaper")
        waitFor("Save")
        pass(600)
        val changed = windowTitleStartingWith("Diaper · ")!!.removePrefix("Diaper · ")
        tap("Poop")
        tap("Save")
        waitUntilGone("Save")
        // "just now" within the minute; the minute may have turned since (and the widget is redrawn at each minute).
        fun since(at: String) = clockMinutesBetween(at, clock()).let { listOf(it, it - 1) }.filter { it >= 0 }
            .map { if (it == 0L) "just now" else "${spokenMinutes(it)} ago" }
        waitUntilWidget(widget, "Feed's time since") { since(fed).any { widgetShows(widget, "Log feed, Last feed $it") } }
        waitUntilWidget(widget, "Diaper's time since") { since(changed).any { widgetShows(widget, "Log diaper, Last diaper $it") } }
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
