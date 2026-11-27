// ail-derived-from: StartSleep@429c94253680b6bf
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for StartSleep: Start sleep, its receipt, the nudges, Undo and Edit, on the real phone app. */
class StartSleepChecks : Phone() {
    @Test
    fun `starting a sleep`() {
        freshInstall()
        startSleepReceipt()
        assertShows("Sleep started")
        assertShowsAt("{time}")
        assertCanTap("Undo")
        for (nudge in listOf("−15m", "−10m", "−5m")) assertCanTap(nudge)
        assertCannotTap("+5m")
        assertCanTap("Edit")
        assertCanTap("Done")
        assertShows("Woke up, end sleep")
        assertShowsAt("Asleep, less than a minute, since {time}")
        assertShows("just now")
    }

    @Test
    fun `nudging the start earlier`() {
        freshInstall()
        startSleepReceipt()
        tap("−15m")
        tap("−5m")
        val was = recentClocks().first { showsPart("· was $it") }
        assertShowsAt("{time} · was $was", -20)
        assertShowsAt("Asleep, 20 minutes, since {time}", -20)
    }

    @Test
    fun `done keeps the sleep running`() {
        freshInstall()
        startSleepReceipt()
        tap("Done")
        waitUntilGone("Sleep started")
        assertGone("Done")
        assertShows("Woke up, end sleep")
    }

    @Test
    fun `undoing a sleep start`() {
        freshInstall()
        startSleepReceipt()
        tap("Undo")
        assertGone("Sleep started")
        assertShows("Undone")
        assertShows("Start sleep")
        assertGone("Woke up, end sleep")
        pass(2_100)
        assertGone("Undone")
    }

    @Test
    fun `edit opens the sleep`() {
        freshInstall()
        startSleepReceipt()
        tap("Edit")
        waitFor("Edit sleep")
        assertGone("Sleep started")
        assertShowsAt("Fell asleep, {time}")
        assertShows("Woke up, Still asleep")
    }
}
