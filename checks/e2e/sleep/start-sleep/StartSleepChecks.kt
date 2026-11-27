// ail-derived-from: StartSleep@bab811e3dbd588a1
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for StartSleep: Start sleep, its receipt, the nudges, Undo and Edit, on the real phone app. */
class StartSleepChecks : Phone() {
    @Test
    fun `starting a sleep`() {
        freshInstall()
        val start = startSleepReceipt()
        assertShows("Sleep started")
        assertCanTap("Undo")
        for (nudge in listOf("−15m", "−10m", "−5m")) assertCanTap(nudge)
        assertCannotTap("+5m")
        assertCanTap("Edit")
        assertCanTap("Done")
        assertShows("Woke up, end sleep")
        assertShows("Asleep, ${spokenMinutes(clockMinutesBetween(start, clock()))}, since $start")
        assertShows(if (clockMinutesBetween(start, clock()) == 0L) "just now" else "m")
    }

    @Test
    fun `nudging the start earlier`() {
        freshInstall()
        val was = startSleepReceipt()
        tap("−15m")
        tap("−5m")
        var start = ""
        waitUntil("the start 20 minutes before $was") { recentClocks(-20).firstOrNull { showsPart("$it · was $was") }?.also { start = it } != null }
        assertEquals(20, clockMinutesBetween(start, was))
        assertShows("Asleep, ${spokenMinutes(clockMinutesBetween(start, clock()))}, since $start")
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
