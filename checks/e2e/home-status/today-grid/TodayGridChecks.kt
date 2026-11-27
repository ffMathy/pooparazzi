// ail-derived-from: TodayGrid@a6952c607a5db159
package com.ffmathy.pooparazzi.checks

import java.time.LocalDate
import org.junit.Test

/** End-to-end check for TodayGrid: today's counts and the link to History, on the real phone app. */
class TodayGridChecks : Phone() {
    @Test
    fun `Today before anything is logged`() {
        freshInstall()
        assertShows("Today")
        assertShows("0")
        assertShows("<1m")
        for (caption in listOf("feeds", "pee", "poop", "sleep")) assertShows(caption)
        assertShows("Today: 0 feeds, 0 pee, 0 poop, less than a minute sleep")
    }

    @Test
    fun `Today counts what was logged today`() {
        freshInstall()
        feedJustTheTime()
        diaperNow(pee = true, poop = true)
        val start = sleepStarted20MinutesAgo()
        waitUntil("Today's counts") {
            val slept = clockMinutesBetween(start, clock())
            shows("Today: 1 feed, 1 pee, 1 poop, ${spokenDuration(slept)} sleep") && shows(shownDuration(slept))
        }
        assertShows("feed")
        assertGone("feeds")
    }

    @Test
    fun `tapping Today opens History`() {
        freshInstall()
        tap("Today")
        waitFor("Previous day")
        assertShows(historyDay(LocalDate.now()))
    }
}
