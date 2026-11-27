// ail-derived-from: SleepNotification@c17f05441902f2ec
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for SleepNotification: the running sleep in the shade, and its Woke up. */
class SleepNotificationChecks : Phone() {
    @Test
    fun `the notification of a running sleep`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        val start = sleepStartedMinutesAgo(20)
        var shown = asleepNotification()
        waitUntilShade("the duration") {
            shown = asleepNotification()
            // Redrawn at each minute by the app's alarm: the minute may have turned since.
            val minutes = clockMinutesBetween(start, clock())
            shown.text in listOf(minutes, minutes - 1).map { "${shownDuration(it)} · since $start" }
        }
        val slept = shown.text.substringBefore(" · ")
        assertEquals(listOf(shown.text, "Today · 0 feeds · 0 pee · 0 poop · $slept sleep"), shown.expanded)
        assertEquals(listOf("Woke up"), shown.buttons)
        assertTrue("ongoing: $shown", shown.ongoing)
    }

    @Test
    fun `the last feed shows in the expanded notification`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        startSleepReceipt()
        tap("Done")
        waitOutTheGuard()
        val fed = foodLogged(15)
        waitUntilShade("the feed line") { asleepNotification().expanded.any { it.startsWith("Fed ") } }
        val shown = asleepNotification()
        // Drawn when the feed was logged, and again at each minute: the minute may have turned since.
        val minutes = clockMinutesBetween(fed, clock())
        assertTrue(shown.toString(), listOf(minutes, minutes - 1).any { shown.expanded.contains("Fed ${shownDuration(it)} ago · $fed · Food") })
        assertTrue(shown.toString(), shown.expanded.last().startsWith("Today · 1 feed · 0 pee · 0 poop · "))
    }

    @Test
    fun `Woke up on the notification ends the sleep`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        sleepStartedMinutesAgo(20)
        wokeUpOnTheNotification()
        assertNoNotification("Asleep")
        waitFor("Awake")
        assertShows("Start sleep")
    }

    @Test
    fun `Woke up within a minute cancels the sleep`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        startSleepReceipt()
        tap("Done")
        waitOutTheGuard()
        asleepNotification()
        pressNotificationButton("Asleep", "Woke up")
        waitUntilShade("the shade to empty") { shade().isEmpty() }
        pass(1_000)
        assertEquals(shadeScreen(), 0, shade().size)
        waitFor("Start sleep")
    }
}
