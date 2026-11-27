// ail-derived-from: Watch@9670abdc33e4b196
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Watch: Main, idle and asleep, on the real watch app. */
class WatchChecks : Watch() {
    @Test
    fun `a fresh watch has nothing logged`() {
        freshInstall()
        assertShows("Feed. No feeds yet")
        assertShows("No feeds yet")
        assertShows("Diaper. No diapers yet")
        assertShows("No diapers yet")
        assertShows("Start sleep")
        assertGonePart("Asleep")
        assertGonePart("Awake since")
        assertGonePart("Last diaper")
    }

    @Test
    fun `main shows how long since the last feed and diaper`() {
        freshInstall()
        val fed = logFood()
        closeLogged()
        val peed = logPee()
        closeLogged()
        waitUntil("the Feed row") { showsPart("Feed. Last feed, food, ") }
        val feedSince = clockMinutesBetween(fed, clock())
        val peeSince = clockMinutesBetween(peed, clock())
        val feedAgo = if (feedSince == 0L) "just now" else "${spokenWatchMinutes(feedSince)} ago"
        val peeAgo = if (peeSince == 0L) "just now" else "${spokenWatchMinutes(peeSince)} ago"
        assertShows("Feed. Last feed, food, $feedAgo, at $fed")
        assertShows("Diaper. Last diaper, pee, $peeAgo, at $peed")
        assertShowsPart(shortSince(peeSince))
        assertShows("Last diaper, pee, $peeAgo")
    }

    @Test
    fun `main while the baby sleeps`() {
        freshInstall()
        val start = sleepStartedMinutesAgo(10)
        val minutes = clockMinutesBetween(start, clock())
        waitFor("Asleep ${minutes}m")
        assertShows("Asleep, ${spokenWatchMinutes(minutes)}, since $start")
        assertShows("Woke up, end sleep")
        assertShows("Woke up")
        assertShowsPart("${minutes}m")
        assertShowsPart("Asleep since $start")
    }

    @Test
    fun `main after waking up`() {
        freshInstall()
        sleepStartedMinutesAgo(10)
        tap("Woke up, end sleep")
        val woke = shownTime("Woke up at ")
        closeLogged()
        waitFor("Start sleep")
        assertShowsPart("Awake since $woke")
        assertShowsPart(shortSince(clockMinutesBetween(woke, clock())))
        assertGonePart("Asleep")
    }
}
