// ail-derived-from: ForecastLines@b7b20072a7aa888f
package com.ffmathy.pooparazzi.checks

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Test

/** End-to-end check for ForecastLines: "what comes next" under Feed, Diaper and the sleep button, on the real phone app. */
open class ForecastLinesChecks : Phone() {
    @Test
    fun `no forecast without a week to go on`() {
        freshInstall()
        feedJustTheTime()
        for (forecast in listOf("next feed", "next poop", "sleeps again", "wakes up")) {
            assertFalse("no forecast line:\n${screen()}", showsPart(forecast))
        }
    }

    @Test
    fun `Feed says when the next feed is expected`() {
        freshInstall()
        feedsEveryThreeHours()
        feedJustTheTime()
        waitFor("3h 00m")
        assertShowsAt("next feed in about 3 hours, around {time}", 180)
    }

    @Test
    fun `the feed forecast reads soon in its last minutes`() {
        freshInstall()
        feedsEveryThreeHours()
        inHistory { addPastFeedAt(LocalDateTime.now().minusMinutes(178)) }
        waitFor("soon")
        assertShows("next feed soon")
    }

    @Test
    fun `the feed forecast reads now once it is due`() {
        freshInstall()
        feedsEveryThreeHours()
        inHistory { addPastFeedAt(LocalDateTime.now().minusMinutes(190)) }
        waitFor("now")
        assertShows("next feed expected now")
    }

    @Test
    fun `Diaper says when the next poop is expected`() {
        freshInstall()
        poopsEveryTwelveHours()
        diaperNow(poop = true)
        waitFor("12h 00m")
        assertShowsAt("next poop in about 12 hours, around {time}", 720)
    }

    @Test
    fun `Woke up says when the baby is expected to wake`() {
        freshInstall()
        napsOfNinetyMinutes()
        startSleepReceipt()
        tap("Done")
        waitOutTheGuard()
        waitFor("1h 30m")
        assertShowsAt("wakes up in about 1 hour 30 minutes, around {time}", 90)
    }
}
