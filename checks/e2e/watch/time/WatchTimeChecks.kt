package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WatchTime: Time from Logged, −5m and +5m, the crown, Done, on the real watch app. */
class WatchTimeChecks : Watch() {
    /** A food feed, then its time on Logged: Time, once it takes taps. Returns the feed's time. */
    private fun openTimeForFood(): String {
        val fed = logFood()
        waitOutLoggedInertStart()
        tap("Food at $fed")
        waitFor("Feed, $fed")
        pass(600)
        return fed
    }

    @Test
    fun `moving a feed's time`() {
        freshInstall()
        val fed = logFood()
        waitOutLoggedInertStart()
        tap("Food at $fed")
        waitFor("Feed, $fed")
        assertShows("Feed")
        assertShows(fed)
        // The steps show "−5m" and "+5m"; they are spoken as below.
        assertShows("Done")
        assertCannotTap("5 minutes earlier")
        pass(600)
        assertCanTap("5 minutes earlier")
        assertCannotTap("5 minutes later")
        assertCanTap("Done")
    }

    @Test
    fun `moving a feed's time back ten minutes`() {
        freshInstall()
        val fed = openTimeForFood()
        tap("5 minutes earlier")
        tap("5 minutes earlier")
        val moved = shownTime("Feed, ", ", 10 minutes earlier", minutesFromNow = -10)
        assertShows("Feed · −10m")
        assertShows(moved)
        assertCanTap("5 minutes later")
        tap("Done")
        waitFor("Food at $moved")
        assertGone("Food at $fed")
        closeLogged()
        assertShows("Feed. Last feed, food, ${spokenWatchMinutes(clockMinutesBetween(moved, clock()))} ago, at $moved")
        assertShowsPart("${clockMinutesBetween(moved, clock())}m")
    }

    @Test
    fun `the crown moves the time a minute a step`() {
        freshInstall()
        openTimeForFood()
        turnCrown(-3)
        waitFor("Feed · −3m")
        turnCrown(1)
        waitFor("Feed · −2m")
        shownTime("Feed, ", ", 2 minutes earlier", minutesFromNow = -2)
    }

    @Test
    fun `turning the crown on logged opens time`() {
        freshInstall()
        val changed = logPlainDiaper()
        turnCrown(1)
        waitFor("Diaper, $changed")
        assertShows("Diaper")
        assertShows("Done")
    }

    @Test
    fun `swiping back from time keeps the time`() {
        freshInstall()
        val fed = openTimeForFood()
        tap("5 minutes earlier")
        waitFor("Feed · −5m")
        swipeToDismiss()
        waitFor("Food at $fed")
        assertGone("Feed · −5m")
        assertShows("Undo")
    }

    @Test
    fun `moving a sleep's start`() {
        freshInstall()
        val start = sleepStartedMinutesAgo(10)
        waitFor("Asleep ${clockMinutesBetween(start, clock())}m")
        assertShowsPart("Asleep since $start")
    }

    @Test
    fun `a wake-up stays a minute after the sleep's start`() {
        freshInstall()
        val start = sleepStartedMinutesAgo(10)
        tap("Woke up, end sleep")
        val woke = shownTime("Woke up at ")
        waitOutLoggedInertStart()
        tap("Woke up at $woke")
        waitFor("Woke up, $woke")
        pass(600)
        tap("5 minutes earlier")
        tap("5 minutes earlier")
        val offset = clockMinutesBetween(start, woke) - 1
        waitFor("Woke up · −${offset}m")
        assertCannotTap("5 minutes earlier")
        tap("Done")
        waitFor("Slept 1m")
        val earliest = shownTime("Woke up at ", minutesFromNow = -offset)
        assertShows("Woke up at $earliest")
    }
}
