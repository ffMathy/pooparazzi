// ail-derived-from: WakeUp@ed92e347798f21fe
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertTrue
import org.junit.Test

class WakeUpShots : Phone() {

    private fun isNow(shown: String): Boolean = shown == clock(0) || shown == clock(-1)

    private fun lampReadsAsleepSince(start: String): Boolean =
        shows("Asleep, ${spokenMinutes(clockMinutesBetween(start, clock(0)))}, since $start")

    @Test
    fun `waking up`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        waitOutTheGuard()

        val end = wokeUpReceipt()
        waitFor("Woke up")

        assertTrue("the receipt's time $end should be the time now (${clock(0)})", isNow(end))
        val slept = clockMinutesBetween(start, end)
        waitUntil("the receipt reads Slept ${slept}m") { showsPart("Slept ${slept}m") }
        assertTrue(showsPart(end))
        assertCanTap("Undo")

        assertCanTap("−15m")
        assertCanTap("−10m")
        assertCanTap("−5m")
        assertCannotTap("+5m")

        waitFor("Start sleep")
        assertShows("Start sleep")
        waitFor("Awake")
        assertShows("Awake")

        pictureTheScreen("waking-up")
    }

    @Test
    fun `undoing a wake-up`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        waitOutTheGuard()
        wokeUpReceipt()
        waitFor("Undo")

        tap("Undo")

        waitFor("Undone")
        assertShows("Undone")
        waitUntil("the Lamp reads Asleep since $start") { lampReadsAsleepSince(start) }
        waitFor("Woke up")
        assertShows("Woke up")

        pictureTheScreen("undoing-a-wake-up")
    }

    @Test
    fun `going back from the receipt keeps the log`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        waitOutTheGuard()
        wokeUpReceipt()
        waitFor("Undo")

        goBack()

        waitUntilGone("Undo")
        assertGone("Undo")
        waitFor("Awake")
        assertShows("Awake")

        pictureTheScreen("going-back-from-the-receipt-keeps-the-log")
    }

    @Test
    fun `a sleep ended within a minute is cancelled`() {
        freshInstall()
        startSleepReceipt()
        tap("Done")
        waitUntilGone("Done")
        waitOutTheGuard()

        tap("Woke up")

        waitFor("Sleep cancelled")
        assertShows("Sleep cancelled")
        waitUntil("the receipt reads Slept <1m") { showsPart("Slept <1m") }
        assertCanTap("Undo")
        assertCanTap("Done")

        assertGone("−15m")
        assertGone("−10m")
        assertGone("−5m")
        assertGone("+5m")
        assertGone("Edit")

        waitFor("Start sleep")
        assertShows("Start sleep")

        pictureTheScreen("a-sleep-ended-within-a-minute-is-cancelled")
    }

    @Test
    fun `undoing a cancelled sleep`() {
        freshInstall()
        val start = startSleepReceipt()
        tap("Done")
        waitUntilGone("Done")
        waitOutTheGuard()
        tap("Woke up")
        waitFor("Sleep cancelled")

        tap("Undo")

        waitUntilGone("Sleep cancelled")
        waitUntil("the Lamp reads Asleep since $start") { lampReadsAsleepSince(start) }
        waitFor("Woke up")
        assertShows("Woke up")

        pictureTheScreen("undoing-a-cancelled-sleep")
    }
}
