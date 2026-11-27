// ail-derived-from: HistoryScreen@3d39a73da09ca63e
package com.ffmathy.pooparazzi.checks

import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for HistoryScreen: the day, its counts and list, day prefixes, Statistics and Back, on the real phone app. */
open class HistoryScreenChecks : Phone() {
    private val one = LocalTime.of(13, 0)
    private val two = LocalTime.of(14, 0)

    @Test
    fun `History of a new install`() {
        freshInstall()
        openHistory()
        assertShows("History")
        assertCanTap("Back")
        assertCanTap("Statistics")
        assertShows(historyDay(daysAgo(0)))
        assertCannotTap("Previous day")
        assertCannotTap("Next day")
        assertShows("Today: 0 feeds, 0 pee, 0 poop, less than a minute sleep")
        assertShows("<1m")
        assertShows("Nothing logged on this day.")
        assertCanTap("Add entry")
    }

    @Test
    fun `today's entries, newest first`() {
        freshInstall()
        val fed = feedJustTheTime()
        val peed = diaperNow(pee = true)
        openHistory()
        val pee = texts().indexOf("Pee, $peed")
        val feed = texts().indexOf("Feed, $fed")
        assertTrue("Pee above Feed:\n${screen()}", pee >= 0 && feed > pee)
        assertShows("Today: 1 feed, 1 pee, 0 poop, less than a minute sleep")
        assertShows("feed")
    }

    @Test
    fun `stepping back to yesterday`() {
        freshInstall()
        openHistory()
        addPastSleep(daysAgo(1), one, daysAgo(1), two)
        tap("Previous day")
        waitFor(historyDay(daysAgo(1)))
        assertShows("1h 00m")
        assertShows("Sleep · 1h 00m")
        assertShows("${clockOf(one)} – ${clockOf(two)}")
        assertShows("Sleep, 1 hour, ${clockOf(one)} to ${clockOf(two)}")
        assertCannotTap("Previous day")
        tap("Next day")
        waitFor(historyDay(daysAgo(0)))
    }

    @Test
    fun `a sleep across midnight is listed on both days`() {
        freshInstall()
        openHistory()
        val ten = LocalTime.of(22, 0)
        val two = LocalTime.of(2, 0)
        addPastSleep(daysAgo(2), ten, daysAgo(1), two)
        tap("Previous day")
        waitFor(historyDay(daysAgo(1)))
        assertShows("Sleep · 4h 00m")
        assertShows("${shortWeekday(daysAgo(2))} ${clockOf(ten)} – ${clockOf(two)}")
        assertShows("2h 00m")
        tap("Previous day")
        waitFor(historyDay(daysAgo(2)))
        assertShows("Sleep · 4h 00m")
        assertShows("${clockOf(ten)} – ${clockOf(two)}")
        assertShows("2h 00m")
    }

    @Test
    fun `History opens Statistics, and Back returns`() {
        freshInstall()
        openHistory()
        tap("Statistics")
        waitFor("Statistics start as soon as something is logged.")
        tap("Back")
        waitFor("Previous day")
        tap("Back")
        waitFor("Start sleep")
    }
}
