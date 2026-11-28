// ail-derived-from: LogWidget@b9b2c88bf4276232
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for LogWidget: the 4×2 widget's buttons and their lines, on the real phone app. */
open class LogWidgetChecks : Phone() {
    /** Its scenarios end on the phone's home screen, so a derived check pictures the widget there, not the app. */
    override fun pictureTheScreen(name: String) = pictureTheHomeScreen(name)

    @Test
    fun `a new widget has nothing to show yet`() {
        freshInstall()
        val widget = placeLogWidget()
        assertEquals(
            listOf("Log feed", "Feed", "Log diaper", "Diaper", "Start sleep", "Start sleep"),
            widgetReads(widget),
        )
    }

    @Test
    fun `the sleep button says how long the baby has slept`() {
        freshInstall()
        val start = sleepStarted15MinutesAgo()
        val widget = placeLogWidget()
        // Redrawn at each minute: the minute may have turned since it was drawn.
        fun shownNow() = clockMinutesBetween(start, clock()).let { listOf(it, it - 1) }.map(::shownDuration).firstOrNull { widgetShows(widget, it) }
        waitUntilWidget(widget, "how long asleep") { widgetShows(widget, "Woke up") && shownNow() != null }
        assertWidgetShows(widget, "Woke up, end sleep, Asleep ${shownNow()}")
    }

    @Test
    fun `after waking the sleep button says how long the baby is awake`() {
        freshInstall()
        sleepStarted15MinutesAgo()
        val widget = placeLogWidget()
        tapWidget(widget, "Woke up, end sleep")
        val woke = widgetTimeAfter(widget, "Woke up at ")
        // "<1m" within the wake-up's minute ("1m" once it turns: the widget is redrawn at each minute).
        val awake = if (clockMinutesBetween(woke, clock()) == 0L) listOf("<1m") else listOf("<1m", "1m")
        waitUntilWidget(widget, "how long awake") { awake.any { widgetShows(widget, "Start sleep, Awake $it") && widgetShows(widget, it) } }
    }

    @Test
    fun `Feed says when the next feed is expected`() {
        freshInstall()
        feedsEveryThreeHours()
        feedJustTheTime()
        val widget = placeLogWidget()
        waitForWidget(widget, "3h 00m")
        // "just now", or "1m" if the minute has turned since the feed (the widget is redrawn at each minute).
        assertTrue(widgetScreen(widget), widgetShows(widget, "just now") || widgetShows(widget, "1m"))
        val spoken = widgetReads(widget).firstOrNull { it.startsWith("Log feed, Last feed ") && it.contains(", next feed expected around ") }
            ?: throw AssertionError("no spoken forecast:\n${widgetScreen(widget)}")
        // The expected time, rounded to five minutes.
        val around = spoken.substringAfter(", next feed expected around ")
        assertTrue(spoken, clockMinutesBetween(clock(), around) in 175L..185L && around.substringAfter(":").take(2).toInt() % 5 == 0)
    }

    @Test
    fun `a small log widget keeps only the icons`() {
        freshInstall()
        val widget = placeLogWidget(120, 100)
        assertEquals(listOf("Log feed", "Log diaper", "Start sleep"), widgetReads(widget))
    }

    @Test
    fun `a log widget that is not tall enough has no status row`() {
        freshInstall()
        val widget = placeLogWidget(240, 130)
        tapWidget(widget, "Start sleep")
        waitForWidget(widget, "Woke up")
        letThePhoneRun(500)
        for (label in listOf("Feed", "Diaper", "Woke up")) assertWidgetShows(widget, label)
        assertWidgetGone(widget, "Undo")
        assertEquals(listOf<String>(), widgetReads(widget).filter { it.startsWith("Sleep started") })
    }
}
