// ail-derived-from: WatchSleep@3c5cb5b945a3827c
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WatchSleep: the sleep edge button, its guard, Start sleep and Woke up, on the real watch app. */
class WatchSleepChecks : Watch() {
    @Test
    fun `starting a sleep on the watch`() {
        freshInstall()
        tap("Start sleep")
        val start = shownTime("Asleep at ")
        assertShows("Undo")
        closeLogged()
        waitUntil("Asleep for the minutes since $start") { shows("Asleep ${asleepFor(clockMinutesBetween(start, clock()))}") }
        assertShows("Woke up, end sleep")
        assertShowsPart("Asleep since $start")
    }

    @Test
    fun `a second tap within two seconds changes nothing on the watch`() {
        freshInstall()
        tap("Start sleep")
        shownTime("Asleep at ")
        // Logged takes taps after its first second; the guard lasts two seconds from Start sleep.
        pass(1_000)
        tapTheBackground()
        waitUntilGone("Undo")
        tap("Woke up, end sleep")
        waitFor("Already asleep")
        assertShows("Already\nasleep")
        assertGone("Sleep cancelled")
        assertGone("Undo")
        waitFor("Woke up, end sleep", 4_000)
        assertShows("Woke up")
        assertGone("Already asleep")
    }

    @Test
    fun `waking up on the watch`() {
        freshInstall()
        val start = sleepStartedMinutesAgo(10)
        tap("Woke up, end sleep")
        val woke = shownTime("Woke up at ")
        val slept = clockMinutesBetween(start, woke)
        assertShows("Slept ${slept}m")
        assertShows("Slept ${spokenWatchMinutes(slept)}")
        assertShows("Undo")
        closeLogged()
        waitFor("Start sleep")
        assertShowsPart("Awake since $woke")
    }

    @Test
    fun `waking up within a minute cancels the sleep on the watch`() {
        freshInstall()
        val start = startSleepOnTheWatch()
        closeLogged()
        // Past the two seconds of the guard.
        pass(1_500)
        tap("Woke up, end sleep")
        waitFor("Sleep cancelled")
        // Under a minute, but counted in clock minutes: "Slept 1m" when the sleep crossed a minute's turn.
        val slept = clockMinutesBetween(start, clock())
        val read = listOf(slept, slept - 1).filter { it in 0L..1L }
            .firstOrNull { shows("Slept ${asleepFor(it)}") && shows("Slept ${spokenWatchMinutes(it)}") }
        org.junit.Assert.assertNotNull("expected \"Slept <1m\" (or \"Slept 1m\" across a minute's turn):\n${screen()}", read)
        assertShows("Undo")
        assertGonePart("Sleep cancelled at")
        waitOutLoggedInertStart()
        turnCrown(2)
        pass(600)
        assertGone("5 minutes earlier")
        assertShows("Sleep cancelled")
        tapTheBackground()
        waitFor("Start sleep")
        assertGonePart("Awake since")
        assertGonePart("Asleep")
    }
}
