// ail-derived-from: ForecastNotifications@606c1ed27365489b
package com.ffmathy.pooparazzi.checks

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** End-to-end check for ForecastNotifications: what comes next in the shade, and which kinds are on. */
open class ForecastNotificationsChecks : Phone() {
    @Test
    fun `the next feed in a notification`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        feedsEveryThreeHours()
        feedJustTheTime()
        letThePhoneRun(500)
        val shown = waitForNotification("Next feed in 3h 05m")
        // "Around" the expected time, rounded to five minutes.
        val around = shown.text.removePrefix("Around ")
        assertTrue(shown.toString(), shown.text.startsWith("Around ") && clockMinutesBetween(clock(), around) in 175L..185L && around.substringAfter(":").take(2).toInt() % 5 == 0)
        assertTrue("ongoing: $shown", shown.ongoing)
    }

    @Test
    fun `the next feed soon`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        feedsEveryThreeHours()
        inHistory { addPastFeedAt(LocalDateTime.now().minusMinutes(178)) }
        letThePhoneRun(500)
        assertTrue(waitForNotification("Next feed soon").text.startsWith("Around "))
    }

    @Test
    fun `the next feed due now`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        feedsEveryThreeHours()
        inHistory { addPastFeedAt(LocalDateTime.now().minusMinutes(190)) }
        letThePhoneRun(500)
        assertTrue(waitForNotification("Feed due now").text.startsWith("Around "))
    }

    @Test
    fun `no week to go on, no prediction`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        feedJustTheTime()
        letThePhoneRun(1_500)
        assertEquals(shadeScreen(), 0, shade().size)
    }

    @Test
    fun `a kind turned off in the system's settings`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        feedsEveryThreeHours()
        feedJustTheTime()
        letThePhoneRun(500)
        waitForNotification("Next feed in 3h 05m")
        switchNotificationCategory("Next feed", on = false)
        // A second feed within five seconds would be the same feed logged twice.
        pass(5_500)
        feedJustTheTime()
        letThePhoneRun(1_500)
        waitUntilShade("no Next feed") { shade().none { it.title.startsWith("Next feed") || it.title == "Feed due now" } }
        tap("Settings")
        waitFor("What comes next")
        val states = texts().dropWhile { it != "What comes next" }.take(7)
        assertEquals(listOf("What comes next", "Next feed", "Off", "Next poop", "On", "Next sleep and wake-up", "On"), states)
    }

    @Test
    fun `Settings opens a kind's system page`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        tap("Settings")
        waitFor("What comes next")
        pass(500)
        // The last "Next feed" is the one under What comes next, low on the screen: scrolled to, as a parent does.
        val rows = compose.onAllNodes(hasText("Next feed") and hasClickAction())
        rows[rows.fetchSemanticsNodes().size - 1].performScrollTo().performClick()
        pass(500)
        assertEquals("Next feed", openedNotificationSettings())
    }
}
