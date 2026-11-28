// ail-derived-from: TypicalDay@c84699cd2e366efc
package com.ffmathy.pooparazzi.checks

import java.time.LocalTime
import org.junit.Test

/** End-to-end check for TypicalDay: when things usually happen and the usual time between them, on the real phone app. */
open class TypicalDayChecks : Phone() {
    @Test
    fun `a typical day needs three logged days`() {
        freshInstall()
        inHistory { for (days in 2L downTo 1L) addPastDiaper(daysAgo(days), LocalTime.of(9, 0), pee = true) }
        openStatistics()
        waitFor("A typical day shows once 3 days in this range are logged.")
    }

    @Test
    fun `the usual time between feeds and the usual sleep`() {
        freshInstall()
        inHistory {
            for (days in 4L downTo 1L) {
                addPastFeed(daysAgo(days), LocalTime.of(8, 0))
                addPastFeed(daysAgo(days), LocalTime.of(11, 0))
                addPastSleep(daysAgo(days), LocalTime.of(12, 0), daysAgo(days), LocalTime.of(13, 30))
            }
        }
        openStatistics()
        waitFor("Typical day")
        for (text in listOf("By day", "By night", "Between feeds", "3h 00m", "3h 00m–3h 00m", "1h 30m–1h 30m", "Between diapers", "Awake")) assertShows(text)
        assertShows("Between feeds. By day: about 3 hours, usually 3 hours to 3 hours. By night: not logged.")
        assertShows("Sleep. By day: about 1 hour 30 minutes, usually 1 hour 30 minutes to 1 hour 30 minutes. By night: not logged.")
        assertShows("Between diapers. By day: not logged. By night: not logged.")
        assertShows("Awake. By day: not logged. By night: not logged.")
    }
}
