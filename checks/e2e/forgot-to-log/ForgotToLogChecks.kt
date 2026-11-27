package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for ForgotToLog: "Expected" under the Feed sheet's and the sleep receipt's nudges. */
class ForgotToLogChecks : Phone() {
    /**
     * The week the scenarios' Given describe, made in History › Add entry: feeds 30 minutes apart at about this time of
     * day, three a day on the two days before today and two on the third. Ends on Home.
     */
    private fun feedsHalfAnHourApart() {
        addPastFeed(3, 0); addPastFeed(3, 30)
        addPastFeed(2, 0); addPastFeed(2, 30); addPastFeed(2, 60)
        addPastFeed(1, 0); addPastFeed(1, 30); addPastFeed(1, 60)
        backHome()
    }

    /**
     * Sleeps of 15 minutes with 15 minutes awake between them at about this time of day, three a day on the two days
     * before today and two on the third (a feed three days ago lets History reach that day). Ends on Home.
     */
    private fun sleepsAQuarterApart() {
        addPastFeed(3, 120)
        addPastSleep(3, 0); addPastSleep(3, 30)
        addPastSleep(2, 0); addPastSleep(2, 30); addPastSleep(2, 60)
        addPastSleep(1, 0); addPastSleep(1, 30); addPastSleep(1, 60)
        backHome()
    }

    private fun backHome() {
        goBack()
        waitFor("Start sleep")
        pass(1_000)
    }

    /** What "Expected" offers: on a 5-minute mark, [minutes] after [from] give or take the rounding. */
    private fun assertOffered(offered: String?, from: String, minutes: Long) {
        assertNotNull("an Expected chip:\n${screen()}", offered)
        val gap = clockMinutesBetween(from, offered!!)
        assertTrue("Expected $offered is about $minutes minutes after $from", gap in (minutes - 3)..(minutes + 3))
        assertEquals("on a 5-minute mark: $offered", 0, offered.substringAfter(":").take(2).toInt() % 5)
    }

    @Test
    fun `a forgotten feed is offered its expected time`() {
        freshInstall()
        feedsHalfAnHourApart()
        val last = foodLogged(60)
        feedSheet()
        val offered = expectedOffer()
        assertOffered(offered, last, 30)
        assertFalse(isPicked("Expected $offered"))
        tap("Cancel")
        pass(500)
        diaperSheet()
        assertNull("the Diaper sheet offers none", expectedOffer())
    }

    @Test
    fun `picking the expected time moves the feed there`() {
        freshInstall()
        feedsHalfAnHourApart()
        foodLogged(60)
        val now = feedSheet()
        val offered = expectedOffer()!!
        tap("Expected $offered")
        assertEquals("Feed · $offered", sheetTitle())
        assertShows("$offered · was $now")
        assertTrue(isPicked("Expected $offered"))
        tap("Food")
        waitUntilGone("Just the time")
        assertShows("$offered · Food")
        waitForLastFeed("food", offered)
    }

    @Test
    fun `a second tap goes back`() {
        freshInstall()
        feedsHalfAnHourApart()
        foodLogged(60)
        val now = feedSheet()
        val offered = expectedOffer()!!
        tap("Expected $offered")
        tap("Expected $offered")
        assertEquals("Feed · $now", sheetTitle())
        assertTrue("no was:\n${screen()}", !showsPart("· was"))
        assertFalse(isPicked("Expected $offered"))
    }

    @Test
    fun `an expected time close to now is not offered`() {
        freshInstall()
        feedsHalfAnHourApart()
        foodLogged(30)
        feedSheet()
        assertNull("no Expected chip:\n${screen()}", expectedOffer())
    }

    @Test
    fun `without a week to go on there is no Expected`() {
        freshInstall()
        foodLogged(60)
        feedSheet()
        assertNull("no Expected chip:\n${screen()}", expectedOffer())
    }

    @Test
    fun `a late Start sleep is offered its expected start`() {
        freshInstall()
        sleepsAQuarterApart()
        // A sleep today, from 45 minutes ago to 25 minutes ago.
        startSleepReceipt()
        tap("−15m"); tap("−15m"); tap("−15m")
        tap("Done")
        waitUntilGone("Sleep started")
        waitOutTheGuard()
        val woke = wokeUpReceipt()
        tap("−15m"); tap("−10m")
        var end = ""
        waitUntil("the end 25 minutes before $woke") { recentClocks(-25).firstOrNull { showsPart("$it · was $woke") }?.also { end = it } != null }
        tap("Done")
        waitUntilGone("Undo")
        waitOutTheGuard()

        val now = startSleepReceipt()
        val offered = expectedOffer()
        assertOffered(offered, end, 15)
        tap("Expected $offered")
        assertShows("$offered · was $now")
        assertTrue(isPicked("Expected $offered"))
        waitUntil("the Lamp asleep since $offered") {
            shows("Asleep, ${spokenMinutes(clockMinutesBetween(offered!!, clock()))}, since $offered")
        }
    }
}
