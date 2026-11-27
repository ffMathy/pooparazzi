// ail-derived-from: StartSleep@429c94253680b6bf
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartSleepShots : Phone() {

    // The receipt ignores taps for its first 500 ms, so let that pass before tapping in it.
    private fun pastReceiptGuard() = pass(600)

    private fun startSleepNow(): String {
        tap("Start sleep")
        val now = clock(0)
        waitFor("Sleep started")
        return now
    }

    @Test
    fun `starting a sleep`() {
        freshInstall()
        waitFor("Start sleep")

        val now = startSleepNow()

        assertShows("Sleep started")
        assertTrue("the receipt shows the time now ($now)", showsPart(now))
        assertShows("Undo")

        assertShows("−15m")
        assertShows("−10m")
        assertShows("−5m")
        assertShows("+5m")
        assertShows("Edit")
        assertShows("Done")

        pastReceiptGuard()
        assertTrue("−15m can be tapped", canTap("−15m"))
        assertTrue("−10m can be tapped", canTap("−10m"))
        assertTrue("−5m can be tapped", canTap("−5m"))
        assertFalse("+5m can't be tapped", canTap("+5m"))

        assertShows("Woke up")

        assertShows("Asleep")
        assertShows("just now")

        saveScreen("starting-a-sleep")
    }

    @Test
    fun `nudging the start earlier`() {
        freshInstall()
        waitFor("Start sleep")

        val now = startSleepNow()
        val twentyAgo = clock(-20)
        pastReceiptGuard()

        tap("−15m")
        tap("−5m")

        assertTrue("the receipt's time reads 20 minutes ago ($twentyAgo)", showsPart(twentyAgo))
        assertTrue("the receipt's time is followed by \"was $now\"", showsPart("was $now"))

        assertShows("Asleep")
        assertGone("just now")
        assertTrue("the Lamp reads 20 minutes", showsPart("20"))

        saveScreen("nudging-the-start-earlier")
    }

    @Test
    fun `done keeps the sleep running`() {
        freshInstall()
        waitFor("Start sleep")

        startSleepNow()
        pastReceiptGuard()

        tap("Done")
        waitUntilGone("Sleep started")

        assertGone("Sleep started")
        assertShows("Woke up")

        saveScreen("done-keeps-the-sleep-running")
    }

    @Test
    fun `undoing a sleep start`() {
        freshInstall()
        waitFor("Start sleep")

        startSleepNow()
        pastReceiptGuard()

        tap("Undo")
        waitUntilGone("Sleep started")

        assertGone("Sleep started")
        assertShows("Undone")
        assertShows("Start sleep")

        pass(2_200)
        assertGone("Undone")

        saveScreen("undoing-a-sleep-start")
    }

    @Test
    fun `edit opens the sleep`() {
        freshInstall()
        waitFor("Start sleep")

        val now = startSleepNow()
        pastReceiptGuard()

        tap("Edit")
        waitFor("Edit sleep")

        assertGone("Sleep started")
        assertShows("Edit sleep")
        assertShows("Fell asleep")
        assertTrue("Fell asleep reads the time now ($now)", showsPart(now))
        assertShows("Woke up")
        assertShows("Still asleep")

        saveScreen("edit-opens-the-sleep")
    }
}
