// ail-derived-from: WatchComplications@b366367486c5638c
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for WatchComplications: the four complications, as a watch face asks for them, on the real watch app. */
class WatchComplicationsChecks : Watch() {
    /** "<1m" or "1m": a complication's time since, in whole clock minutes. */
    private fun short(minutes: Long) = if (minutes == 0L) "<1m" else "${minutes}m"

    private fun spokenAgo(minutes: Long) = if (minutes == 0L) "just now" else "${spokenWatchMinutes(minutes)} ago"

    @Test
    fun `complications before anything is logged`() {
        freshInstall()
        assertEquals("--\nNo feeds yet", complication("Since last feed"))
        assertEquals("--\nNo diapers yet", complication("Last diaper"))
        assertEquals("--\nNo sleep yet", complication("Asleep or awake"))
        assertEquals("No sleep yet\nNo sleep yet", complication("Sleep and last feed"))
    }

    @Test
    fun `complications after a feed and a pee`() {
        freshInstall()
        val fed = logFood()
        closeLogged()
        val peed = logPee()
        closeLogged()
        val feedSince = clockMinutesBetween(fed, clock())
        val peeSince = clockMinutesBetween(peed, clock())
        assertEquals("${short(feedSince)}\nLast feed ${spokenAgo(feedSince)}", complication("Since last feed"))
        assertEquals("${short(peeSince)}\nPee\nLast diaper, pee, ${spokenAgo(peeSince)}", complication("Last diaper"))
    }

    @Test
    fun `complications while asleep`() {
        freshInstall()
        val start = startSleepOnTheWatch()
        closeLogged()
        val asleep = clockMinutesBetween(start, clock())
        assertEquals("${asleepFor(asleep)}\nAsleep\nAsleep ${spokenWatchMinutes(asleep)}, since $start", complication("Asleep or awake"))
        assertEquals("Asleep ${asleepFor(asleep)}\nAsleep ${spokenWatchMinutes(asleep)}, since $start", complication("Sleep and last feed"))
    }

    @Test
    fun `tapping a complication opens the app`() {
        freshInstall()
        startSleepOnTheWatch()
        closeLogged()
        // The app closed to the watch face: on a screen of its own, Feed type.
        openFeedType()
        tapComplication("Asleep or awake")
        waitFor("Woke up, end sleep")
        assertGone("Just the time")
    }
}
