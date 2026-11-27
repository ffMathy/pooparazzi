// ail-derived-from: WakeUp@af11900925aa29a3
package com.ffmathy.pooparazzi.checks

import androidx.test.espresso.Espresso
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeUpShots : Phone() {

    private val twentyMinutes = 20 * 60_000L

    private fun assertPart(part: String) {
        assertTrue("expected the screen to show a text containing \"$part\"", showsPart(part))
    }

    /** Starts a sleep, closes its receipt with Done and returns the clock time it started at. */
    private fun startSleepAndCloseReceipt(): String {
        tap("Start sleep")
        val started = clock(0)
        waitFor("Done")
        tap("Done")
        waitUntilGone("Done")
        return started
    }

    /** A sleep that started 20 minutes ago, ended with Woke up; returns its start time. */
    private fun sleepOfTwentyMinutesEndedWithWokeUp(): String {
        val started = startSleepAndCloseReceipt()
        pass(twentyMinutes)
        tap("Woke up")
        waitFor("Undo")
        return started
    }

    @Test
    fun `waking up`() {
        freshInstall()
        startSleepAndCloseReceipt()
        pass(twentyMinutes)

        tap("Woke up")
        waitFor("Undo")

        assertShows("Woke up")
        assertPart(clock(0))
        assertPart("Slept 20m")
        assertTrue(canTap("Undo"))
        assertTrue(canTap("−15m"))
        assertTrue(canTap("−10m"))
        assertTrue(canTap("−5m"))
        assertTrue(shows("+5m"))
        assertFalse(canTap("+5m"))
        assertShows("Start sleep")
        assertShows("Awake")

        saveScreen("waking-up")
    }

    @Test
    fun `undoing a wake-up`() {
        freshInstall()
        val started = sleepOfTwentyMinutesEndedWithWokeUp()

        tap("Undo")
        waitFor("Undone")

        assertShows("Undone")
        assertShows("Asleep")
        assertPart(started)
        assertShows("Woke up")

        saveScreen("undoing-a-wake-up")
    }

    @Test
    fun `going back from the receipt keeps the log`() {
        freshInstall()
        sleepOfTwentyMinutesEndedWithWokeUp()

        Espresso.pressBack()
        waitUntilGone("Undo")

        assertGone("Undo")
        assertShows("Awake")

        saveScreen("going-back-from-the-receipt-keeps-the-log")
    }

    @Test
    fun `a sleep ended within a minute is cancelled`() {
        freshInstall()
        startSleepAndCloseReceipt()
        pass(3_000)

        tap("Woke up")
        waitFor("Sleep cancelled")

        assertShows("Sleep cancelled")
        assertPart("Slept <1m")
        assertTrue(canTap("Undo"))
        assertTrue(canTap("Done"))
        assertGone("−15m")
        assertGone("−10m")
        assertGone("−5m")
        assertGone("+5m")
        assertGone("Edit")
        assertShows("Start sleep")

        saveScreen("a-sleep-ended-within-a-minute-is-cancelled")
    }

    @Test
    fun `undoing a cancelled sleep`() {
        freshInstall()
        val started = startSleepAndCloseReceipt()
        pass(3_000)
        tap("Woke up")
        waitFor("Sleep cancelled")

        tap("Undo")
        waitUntilGone("Sleep cancelled")

        assertShows("Asleep")
        assertPart(started)
        assertShows("Woke up")

        saveScreen("undoing-a-cancelled-sleep")
    }
}
