package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for LogWidget: the 4×2 widget's buttons and their lines, on the real phone app. */
class LogWidgetChecks : Phone() {
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
        waitUntilWidget(widget, "how long asleep") {
            widgetShows(widget, "Woke up") && widgetShows(widget, shownDuration(clockMinutesBetween(start, clock())))
        }
        assertWidgetShows(widget, "Woke up, end sleep, Asleep ${shownDuration(clockMinutesBetween(start, clock()))}")
    }

    @Test
    fun `after waking the sleep button says how long the baby is awake`() {
        freshInstall()
        sleepStarted15MinutesAgo()
        val widget = placeLogWidget()
        tapWidget(widget, "Woke up, end sleep")
        widgetTimeAfter(widget, "Woke up at ")
        waitForWidget(widget, "Start sleep, Awake <1m")
        assertWidgetShows(widget, "<1m")
    }

    @Test
    fun `Feed says when the next feed is expected`() {
        freshInstall()
        feedsEveryThreeHours()
        feedJustTheTime()
        val widget = placeLogWidget()
        waitForWidget(widget, "3h 00m")
        assertWidgetShows(widget, "just now")
        val spoken = recentClocks(180).map { "Log feed, Last feed just now, next feed expected around $it" }
        waitUntilWidget(widget, "the spoken forecast") { spoken.any { widgetShows(widget, it) } }
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
