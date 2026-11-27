// ail-derived-from: WatchLogged@4c126603e5929359
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WatchLogged: Logged, Undo, its window and inert start, Woke up too, Undone, on the real watch app. */
open class WatchLoggedChecks : Watch() {
    @Test
    fun `logged closes after five seconds`() {
        freshInstall()
        val fed = logFood()
        pass(3_700)
        assertShows("Food at $fed")
        assertShows("Undo")
        waitUntilGone("Undo", 4_000)
        waitUntil("Main") { showsPart("Feed. Last feed, food, ") }
        assertShowsPart(shortSince(clockMinutesBetween(fed, clock())))
    }

    @Test
    fun `logged ignores taps for its first second`() {
        freshInstall()
        val fed = logFood()
        assertCannotTap("Undo")
        assertCannotTap("Food at $fed")
        tapTheBackground()
        tap("Undo")
        assertShows("Food at $fed")
        assertGone("Undone")
        pass(800)
        assertCanTap("Undo")
        assertCanTap("Food at $fed")
    }

    @Test
    fun `tapping the background closes logged`() {
        freshInstall()
        val fed = logFood()
        waitOutLoggedInertStart()
        tapTheBackground()
        waitUntilGone("Undo")
        waitUntil("Main") { showsPart("Feed. Last feed, food, ") }
        assertGone("Food at $fed")
    }

    @Test
    fun `undo takes a watch log back`() {
        freshInstall()
        logFood()
        waitOutLoggedInertStart()
        tap("Undo")
        waitFor("Undone")
        assertGone("Undo")
        waitUntilGone("Undone", 4_000)
        assertShows("Feed. No feeds yet")
    }

    @Test
    fun `undoing a wake-up resumes the sleep`() {
        freshInstall()
        val start = sleepStartedMinutesAgo(10)
        tap("Woke up, end sleep")
        shownTime("Woke up at ")
        waitOutLoggedInertStart()
        tap("Undo")
        waitFor("Undone")
        waitFor("Woke up, end sleep", 4_000)
        waitFor("Asleep ${clockMinutesBetween(start, clock())}m")
        assertShowsPart("Asleep since $start")
    }

    @Test
    fun `a feed during a sleep wakes the baby too`() {
        freshInstall()
        val start = sleepStartedMinutesAgo(5)
        val fed = logFood()
        assertShows("Food at $fed")
        waitFor("Woke up too")
        waitOutLoggedInertStart()
        tap("Undo")
        waitFor("Undone")
        waitFor("Woke up, end sleep", 4_000)
        waitFor("Asleep ${clockMinutesBetween(start, clock())}m")
        assertShows("Feed. No feeds yet")
    }

    @Test
    fun `a log within a minute of falling asleep cancels the sleep`() {
        freshInstall()
        startSleepOnTheWatch()
        closeLogged()
        val changed = logPlainDiaper()
        assertShows("Diaper at $changed")
        waitFor("Sleep cancelled too")
        closeLogged()
        waitFor("Start sleep")
        assertGonePart("Awake since")
        assertGonePart("Asleep")
    }

    @Test
    fun `logged says the log is kept on the watch`() {
        freshInstall()
        logFood()
        waitUntilGone("Undo", 8_000)
        // The watch has tried to reach the phone by now.
        pass(5_000)
        logPlainDiaper()
        waitFor("Saved on watch · will sync", 4_000)
    }
}
