package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for FeedWhileAsleep: a feed logged during a sleep, and the sleep it ends, on the real phone app. */
class FeedWhileAsleepChecks : Phone() {
    @Test
    fun `the sheet says the sleep will end`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        feedSheet()
        assertShows("Woke up will be logged too")
    }

    @Test
    fun `a nudge to before the sleep began hides the note`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        feedSheet()
        tap("−15m")
        tap("−10m")
        val at = sheetTime()
        // 25 minutes back from a few seconds after the sleep began: 5 clock minutes before it, or 4 if the minute turned.
        assertTrue("$at is before $start", clockMinutesBetween(at, start) in 4..5)
        assertGone("Woke up will be logged too")
        tap("+5m")
        assertShows("Woke up will be logged too")
    }

    @Test
    fun `a feed before the sleep began leaves it running`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        feedSheet()
        nudgeBack(25)
        tap("Just the time")
        waitUntilGone("Just the time")
        waitOutTheCooldown()
        assertShows("Woke up, end sleep")
        waitUntil("the Lamp asleep since $start") {
            shows("Asleep, ${spokenMinutes(clockMinutesBetween(start, clock()))}, since $start")
        }
    }

    @Test
    fun `saving the feed ends the sleep`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        feedSheet()
        val at = nudgeBack(10)
        tap("Food")
        waitUntilGone("Just the time")
        waitOutTheCooldown()
        assertShows("Start sleep")
        assertGone("Woke up, end sleep")
        assertShows("Awake")
        waitUntil("Awake since $at") { shows("Awake ${spokenMinutes(clockMinutesBetween(at, clock()))}, since $at") }
        // 10 clock minutes, or 11 if the minute turned between Start sleep and Feed.
        val slept = clockMinutesBetween(start, at)
        assertTrue("slept $slept minutes", slept in 10..11)
        assertTrue(todayCounts(), todayCounts().endsWith(", $slept minutes sleep"))
    }

    @Test
    fun `a feed seconds after the sleep began cancels it`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        feedSheet()
        nudgeBack(20)
        tap("Food")
        waitUntilGone("Just the time")
        waitOutTheCooldown()
        assertShows("Start sleep")
        assertGone("Woke up, end sleep")
        assertGone("Awake")
        assertTrue(todayCounts(), todayCounts().endsWith(", less than a minute sleep"))
    }
}
