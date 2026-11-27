// ail-derived-from: StatisticsChart@953bfa801efe867c
package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTouchInput
import java.time.LocalTime
import org.junit.Test

/** End-to-end check for StatisticsChart: the open row's chart, a pressed bar and an unusual day, on the real phone app. */
open class StatisticsChartChecks : Phone() {
    private val yesterday = daysAgo(1)
    private val from = rangeStart(30)

    /** Poop diapers at 9:00 on each of the last four days, and one more at 13:00 yesterday. */
    private fun poops() = inHistory {
        for (days in 4L downTo 1L) addPastDiaper(daysAgo(days), LocalTime.of(9, 0), poop = true)
        addPastDiaper(yesterday, LocalTime.of(13, 0), poop = true)
    }

    /** Presses the last bar of the chart spoken "[kind] per day, …": yesterday's, at the plot's end. */
    private fun pressYesterdaysBar(kind: String) {
        compose.onAllNodes(hasContentDescription("$kind per day, ", substring = true))[0]
            .performTouchInput { click(Offset(right - 5f, centerY)) }
        pass(600)
    }

    @Test
    fun `the open row's chart`() {
        freshInstall()
        poops()
        openStatistics()
        assertShows("2")
        assertShows(chartDate(from, from, yesterday))
        assertShows(chartDate(yesterday, from, yesterday))
        assertShows(
            "Poop per day, ${spokenDate(from, from, yesterday)} to ${spokenDate(yesterday, from, yesterday)}. " +
                "Highest 2 on ${spokenDay(yesterday, from, yesterday)}. Lowest 1 on ${spokenDay(daysAgo(2), from, yesterday)}. " +
                "Days before ${spokenDate(daysAgo(3), from, yesterday)} are left out.",
        )
    }

    @Test
    fun `pressing a bar shows its day and value`() {
        freshInstall()
        poops()
        openStatistics()
        pressYesterdaysBar("Poop")
        waitFor("${shortDate(yesterday)} · 2")
        pressYesterdaysBar("Poop")
        waitUntilGone("${shortDate(yesterday)} · 2")
        assertShows(chartDate(from, from, yesterday))
    }

    @Test
    fun `an unusual day is grey and left out of the average`() {
        freshInstall()
        inHistory {
            for (days in 5L downTo 1L) addPastSleep(daysAgo(days), LocalTime.of(12, 0), daysAgo(days), LocalTime.of(13, 30))
            addPastSleep(yesterday, LocalTime.of(15, 0), yesterday, LocalTime.of(19, 0))
        }
        openStatistics()
        tap("Sleep")
        waitUntil("Sleep's chart") { showsPart("Sleep per day, ") }
        assertShows("1h 30m")
        assertShows("Unusual day")
        assertShows("Grey bars are days unlike the others, such as a half-logged day. They are left out of the average.")
        assertShowsPart("1 day unlike the others is left out of the average.")
        pressYesterdaysBar("Sleep")
        waitFor("${shortDate(yesterday)} · 5h 30m · Unusual")
    }
}
