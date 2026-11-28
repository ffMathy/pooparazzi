// ail-derived-from: WatchOngoing@3f84241330eb0f16
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** End-to-end check for WatchOngoing: the Ongoing Activity on the watch face, on the real watch app. */
open class WatchOngoingChecks : Watch() {
    @Test
    fun `a running sleep shows on the watch face`() {
        freshInstall()
        val start = startSleepOnTheWatch()
        waitUntil("the Ongoing Activity") { ongoingActivity() != null }
        assertEquals("Asleep ${asleepFor(clockMinutesBetween(start, clock()))}", ongoingActivity())
        closeLogged()
        // Past the two seconds of the guard.
        pass(1_500)
        tap("Woke up, end sleep")
        waitFor("Sleep cancelled")
        waitUntil("no Ongoing Activity") { ongoingActivity() == null }
    }

    @Test
    fun `no ongoing activity while awake with nothing predicted`() {
        freshInstall()
        assertNull(ongoingActivity())
    }

    @Test
    fun `tapping the ongoing activity opens main`() {
        freshInstall()
        startSleepOnTheWatch()
        closeLogged()
        waitUntil("the Ongoing Activity") { ongoingActivity() != null }
        // The app closed to the watch face: on a screen of its own, Diaper.
        openDiaper()
        tapOngoingActivity()
        waitFor("Woke up, end sleep")
        assertGone("Save")
    }
}
