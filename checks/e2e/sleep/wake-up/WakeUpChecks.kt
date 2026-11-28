// ail-derived-from: WakeUp@335bcb00ed3530f5
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WakeUp: Woke up, its receipt, a cancelled sleep and Undo, on the real phone app. */
open class WakeUpChecks : Phone() {
    @Test
    fun `waking up`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        val end = wokeUpReceipt()
        assertShows("Woke up")
        assertShows("$end · Slept ${clockMinutesBetween(start, end)}m")
        assertCanTap("Undo")
        for (nudge in listOf("−15m", "−10m", "−5m")) assertCanTap(nudge)
        assertCannotTap("+5m")
        assertShows("Start sleep")
        assertGone("Woke up, end sleep")
        assertShows("Awake")
        assertShows("Awake ${spokenMinutes(clockMinutesBetween(end, clock()))}, since $end")
    }

    @Test
    fun `undoing a wake-up`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        wokeUpReceipt()
        tap("Undo")
        assertShows("Undone")
        assertShows("Asleep, ${spokenMinutes(clockMinutesBetween(start, clock()))}, since $start")
        assertShows("Woke up, end sleep")
    }

    @Test
    fun `going back from the receipt keeps the log`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        val end = wokeUpReceipt()
        goBack()
        assertGone("Undo")
        assertShows("Start sleep")
        assertShows("Awake ${spokenMinutes(clockMinutesBetween(end, clock()))}, since $end")
    }

    @Test
    fun `a sleep ended within a minute is cancelled`() {
        freshInstall()
        startSleepReceipt()
        tap("Done")
        waitOutTheGuard()
        wokeUpReceipt()
        assertShows("Sleep cancelled")
        assertShows("Slept <1m")
        assertCanTap("Undo")
        assertCanTap("Done")
        for (nudge in listOf("−15m", "−10m", "−5m", "+5m")) assertGone(nudge)
        assertGone("Edit")
        assertShows("Start sleep")
    }

    @Test
    fun `undoing a cancelled sleep`() {
        freshInstall()
        val start = startSleepReceipt()
        tap("Done")
        waitOutTheGuard()
        wokeUpReceipt()
        tap("Undo")
        assertShows("Undone")
        assertShows("Asleep, ${spokenMinutes(clockMinutesBetween(start, clock()))}, since $start")
        assertShows("Woke up, end sleep")
    }
}
