package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WidgetReceipt: the 4×2 widget's receipt row, Undo, the guard and its ten seconds. */
class WidgetReceiptChecks : Phone() {
    @Test
    fun `Start sleep on the widget logs at once`() {
        freshInstall()
        val widget = placeLogWidget()
        val start = startSleepOnWidget(widget)
        assertWidgetShows(widget, "Undo")
        assertWidgetShows(widget, "Undo sleep start")
        waitForWidget(widget, "Woke up")
        waitUntil("Home asleep") { showsPart("Asleep, ") && showsPart("since $start") }
        assertGone("Sleep started")
    }

    @Test
    fun `Undo on the widget takes the sleep back`() {
        freshInstall()
        val widget = placeLogWidget()
        startSleepOnWidget(widget)
        tapWidget(widget, "Undo")
        waitForWidget(widget, "Undone")
        assertWidgetShows(widget, "Start sleep")
        letThePhoneRun(2_500)
        waitUntilWidget(widget, "the row to empty") { !widgetShows(widget, "Undone") }
        assertWidgetGone(widget, "Undo")
    }

    @Test
    fun `a second tap within two seconds changes nothing`() {
        freshInstall()
        val widget = placeLogWidget()
        val start = startSleepOnWidget(widget)
        tapWidget(widget, "Woke up, end sleep")
        waitForWidget(widget, "Already asleep since $start")
        assertWidgetShows(widget, "Woke up")
        letThePhoneRun(2_200)
        waitForWidget(widget, "Sleep started at $start")
        assertWidgetShows(widget, "Woke up")
    }

    @Test
    fun `the receipt goes after ten seconds`() {
        freshInstall()
        val widget = placeLogWidget()
        startSleepOnWidget(widget)
        letThePhoneRun(10_500)
        waitUntilWidget(widget, "the receipt to go") { !widgetShows(widget, "Undo") }
        assertWidgetShows(widget, "Woke up")
        if (widgetShowsPart(widget, "Sleep started at")) throw AssertionError("the receipt stayed:\n${widgetScreen(widget)}")
    }

    @Test
    fun `Woke up on the widget`() {
        freshInstall()
        sleepStarted15MinutesAgo()
        val widget = placeLogWidget()
        tapWidget(widget, "Woke up, end sleep")
        widgetTimeAfter(widget, "Woke up at ")
        assertWidgetShows(widget, "Undo woke up")
    }

    @Test
    fun `the background opens the receipt in the app`() {
        freshInstall()
        val start = sleepStarted15MinutesAgo()
        val widget = placeLogWidget()
        tapWidget(widget, "Woke up, end sleep")
        val end = widgetTimeAfter(widget, "Woke up at ")
        tapWidgetBackground(widget)
        waitFor("Woke up · $end")
        assertShows("$end, Slept ${spokenMinutes(clockMinutesBetween(start, end))}")
        for (control in listOf("Undo", "−15m", "−10m", "−5m", "+5m", "Edit", "Done")) assertShows(control)
    }

    @Test
    fun `a sleep ended within a minute is cancelled on the widget`() {
        freshInstall()
        val widget = placeLogWidget()
        startSleepOnWidget(widget)
        letThePhoneRun(2_200)
        tapWidget(widget, "Woke up, end sleep")
        waitForWidget(widget, "Sleep cancelled")
        assertWidgetShows(widget, "Undo sleep cancelled")
        assertWidgetShows(widget, "Start sleep")
        tapWidget(widget, "Undo")
        waitForWidget(widget, "Woke up")
    }

    @Test
    fun `a feed logged from the widget's sheet leaves the receipt`() {
        freshInstall()
        sleepStarted15MinutesAgo()
        val widget = placeLogWidget()
        tapWidget(widget, "Woke up, end sleep")
        val end = widgetTimeAfter(widget, "Woke up at ")
        tapWidget(widget, "Log feed")
        waitFor("Food")
        pass(600)
        tap("Food")
        waitUntilGone("Just the time")
        waitForWidget(widget, "Log feed, Last feed just now")
        assertWidgetShows(widget, "Woke up at $end")
        assertWidgetShows(widget, "Undo woke up")
    }
}
