package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertFalse
import org.junit.Test

/** End-to-end check for StatisticsScreen: the empty state, the range, the four averages and the footnote, on the real phone app. */
class StatisticsScreenChecks : Phone() {
    private val yesterday = daysAgo(1)

    @Test
    fun `Statistics before anything is logged`() {
        freshInstall()
        openStatistics()
        assertShows("Statistics")
        assertCanTap("Back")
        assertCanTap("History")
        assertShows("Statistics start as soon as something is logged.")
    }

    @Test
    fun `averages per day over the last 30 days`() {
        freshInstall()
        fourOrdinaryDays()
        openStatistics()
        val from = rangeStart(30)
        assertShows("Last 30 days")
        assertShows("Last 30 days, ${spokenDate(from, from, yesterday)} to ${spokenDate(yesterday, from, yesterday)}, 3 of 30 days logged")
        assertShows("Feeds. Last 30 days: 2.0 a day.")
        assertShows("Pee. Last 30 days: 1.0 a day.")
        assertShows("Poop. Last 30 days: 0.3 a day.")
        assertShows("Sleep. Last 30 days: 1 hour 30 minutes a day.")
        for (average in listOf("2.0", "1.0", "0.3", "1h 30m")) assertShows(average)
        assertShowsPart("Averages count whole days up to yesterday.")
    }

    @Test
    fun `today so far until a whole day is logged`() {
        freshInstall()
        feedJustTheTime()
        openStatistics()
        assertShows("1.0")
        assertShows("No whole day has been logged yet, so these averages count today so far. From the first whole day on, they count whole days up to yesterday.")
    }

    @Test
    fun `changing the range`() {
        freshInstall()
        fourOrdinaryDays()
        openStatistics()
        tap("Last 30 days")
        waitFor("Choose dates…")
        for (range in listOf("Last 7 days", "Last 30 days", "Last 90 days", "All days")) assertShows(range)
        tapTop("Last 7 days")
        val from = rangeStart(7)
        waitFor("Last 7 days, ${spokenDate(from, from, yesterday)} to ${spokenDate(yesterday, from, yesterday)}, 3 of 7 days logged")
        tap("Last 7 days")
        waitFor("Choose dates…")
        tapTop("All days")
        val first = daysAgo(3)
        waitFor("All days, ${spokenDate(first, first, yesterday)} to ${spokenDate(yesterday, first, yesterday)}")
    }

    @Test
    fun `a row opens its chart`() {
        freshInstall()
        fourOrdinaryDays()
        openStatistics()
        assertShowsPart("Poop per day, ")
        tap("Feeds")
        waitUntil("Feeds' chart") { showsPart("Feeds per day, ") }
        assertFalse("Poop's chart closed:\n${screen()}", showsPart("Poop per day, "))
    }
}
