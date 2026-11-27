// ail-derived-from: FeedWhileAsleep@0d00cc3aa4385a34
package com.ffmathy.pooparazzi.checks

import org.junit.Test

class FeedWhileAsleepShots : Phone() {

    private val wakesNote = "Woke up will be logged too"

    /** Today shows a duration as one text ("10m"); its spoken name spells it out. */
    private fun todayShowsSleepOf(minutes: Int): Boolean =
        shows("${minutes}m") || showsPart(spokenMinutes(minutes))

    @Test
    fun `the sheet says the sleep will end`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        waitFor("Woke up")

        tap("Feed")
        waitFor("−15m")

        waitFor(wakesNote)
        assertShows(wakesNote)
        assertShows("−15m")
        assertShows("+5m")

        saveScreen("the-sheet-says-the-sleep-will-end")
    }

    @Test
    fun `a nudge to before the sleep began hides the note`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        waitFor("Woke up")
        tap("Feed")
        waitFor(wakesNote)

        tap("−15m")
        tap("−10m")

        assertShowsAt("{time}", -25)
        waitUntilGone(wakesNote)
        assertGone(wakesNote)

        tap("+5m")
        waitFor(wakesNote)
        assertShows(wakesNote)

        saveScreen("a-nudge-to-before-the-sleep-began-hides-the-note")
    }

    @Test
    fun `a feed before the sleep began leaves it running`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        waitFor("Woke up")
        tap("Feed")
        waitFor("−15m")
        tap("−15m")
        tap("−10m")
        waitUntilGone(wakesNote)

        tap("Just the time")
        waitUntilGone("Just the time")

        waitUntil("the Lamp reads Asleep since $start") {
            showsPart("Asleep, ") && showsPart("since $start")
        }
        assertGone("Awake")
        assertShows("Woke up")
        assertCanTap("Woke up")
        assertGone("Start sleep")

        saveScreen("a-feed-before-the-sleep-began-leaves-it-running")
    }

    @Test
    fun `saving the feed ends the sleep`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        waitFor("Woke up")
        tap("Feed")
        waitFor(wakesNote)
        tap("−10m")
        val feedAtNudge = clock(-10)
        assertShows(wakesNote)

        tap("Food")
        val feedAfterTap = clock(-10)
        waitUntilGone("Just the time")

        waitFor("Start sleep")
        assertCanTap("Start sleep")
        waitFor("Awake")
        assertShows("Awake")
        val feedTime = listOf(feedAtNudge, feedAfterTap).firstOrNull { showsPart(it) }
        waitUntil("Home reads Awake since the feed's time") { feedTime != null }

        val slept = clockMinutesBetween(start, feedTime!!)
        waitUntil("Today counts the sleep of $slept minutes") { todayShowsSleepOf(slept) }

        saveScreen("saving-the-feed-ends-the-sleep")
    }

    @Test
    fun `a feed seconds after the sleep began cancels it`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        waitFor("Woke up")
        tap("Feed")
        waitFor("−15m")
        tap("−15m")
        tap("−5m")

        tap("Food")
        waitUntilGone("Just the time")

        waitFor("Start sleep")
        assertCanTap("Start sleep")
        assertGone("Woke up")
        assertGone("Awake")
        waitUntil("Today counts no sleep") { !todayShowsSleepOf(0) && !todayShowsSleepOf(1) }

        saveScreen("a-feed-seconds-after-the-sleep-began-cancels-it")
    }
}
