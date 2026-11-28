// ail-derived-from: SleepNotification@c50f1a5ed1186255
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for SleepNotification: the running sleep in the shade, and its Woke up. */
open class SleepNotificationChecks : Phone() {
    /** Its scenarios end in the notification shade, so a derived check pictures the shade over the app. */
    override fun pictureTheScreen(name: String) = pictureTheShade(name)

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
        // Today counts the sleep's minutes since midnight only: a sleep that began yesterday counts from 00:00.
        val sinceMidnight = java.time.LocalTime.now().toSecondOfDay() / 60L
        val today = if (clockMinutesBetween(start, clock()) <= sinceMidnight) listOf(slept)
        else listOf(sinceMidnight, sinceMidnight - 1).filter { it >= 0 }.map { shownDuration(it) }
        assertEquals(shown.text, shown.expanded.first())
        assertTrue(shown.toString(), today.any { shown.expanded == listOf(shown.text, "Today · 0 feeds · 0 pee · 0 poop · $it sleep") })
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
        // A feed before midnight is yesterday's: Today counts it only when it was logged today.
        val feeds = if (minutes <= java.time.LocalTime.now().toSecondOfDay() / 60L) "1 feed" else "0 feeds"
        assertTrue(shown.toString(), shown.expanded.last().startsWith("Today · $feeds · 0 pee · 0 poop · "))
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
