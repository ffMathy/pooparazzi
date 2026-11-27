// ail-derived-from: WakeUp@af11900925aa29a3
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WakeUp: Woke up, its receipt, a cancelled sleep and Undo, on the real phone app. */
class WakeUpChecks : Phone() {
    @Test
    fun `waking up`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        wokeUpReceipt()
        assertShows("Woke up")
        assertShowsAt("{time} · Slept 20m")
        assertCanTap("Undo")
        for (nudge in listOf("−15m", "−10m", "−5m")) assertCanTap(nudge)
        assertCannotTap("+5m")
        assertShows("Start sleep")
        assertGone("Woke up, end sleep")
        assertShows("Awake")
        assertShowsAt("Awake less than a minute, since {time}")
    }

    @Test
    fun `undoing a wake-up`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        wokeUpReceipt()
        tap("Undo")
        assertShows("Undone")
        assertShowsAt("Asleep, 20 minutes, since {time}", -20)
        assertShows("Woke up, end sleep")
    }

    @Test
    fun `going back from the receipt keeps the log`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        wokeUpReceipt()
        goBack()
        assertGone("Undo")
        assertShows("Start sleep")
        assertShowsAt("Awake less than a minute, since {time}")
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
        startSleepReceipt()
        val start = recentClocks().first { shows(it) }
        tap("Done")
        waitOutTheGuard()
        wokeUpReceipt()
        tap("Undo")
        assertShows("Undone")
        assertShows("Asleep, less than a minute, since $start")
        assertShows("Woke up, end sleep")
    }
}
