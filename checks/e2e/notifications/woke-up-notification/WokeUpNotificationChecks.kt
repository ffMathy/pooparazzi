package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** End-to-end check for WokeUpNotification: "Woke up at …" and its Earlier offsets, from the shade. */
class WokeUpNotificationChecks : Phone() {
    @Test
    fun `the woke-up notification`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        val start = sleepStartedMinutesAgo(20)
        val end = wokeUpOnTheNotification()
        val shown = notification("Woke up at $end")!!
        assertEquals("Slept ${shownDuration(clockMinutesBetween(start, end))} · $start – $end", shown.text)
        assertEquals(listOf("−5m", "−10m", "−15m"), shown.buttons)
        assertFalse("it can be swiped away: $shown", shown.ongoing)
    }

    @Test
    fun `an Earlier offset moves the wake-up`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        val start = sleepStartedMinutesAgo(20)
        val end = wokeUpOnTheNotification()
        pressNotificationButton("Woke up at $end", "−10m")
        val moved = clockOf(java.time.LocalTime.parse(end, timeFormat(end)).minusMinutes(10))
        val shown = waitForNotification("Woke up at $moved")
        assertEquals("Slept ${shownDuration(clockMinutesBetween(start, moved))} · $start – $moved", shown.text)
        assertEquals(listOf("−5m", "✓ −10m", "−15m"), shown.buttons)
    }

    @Test
    fun `the selected offset tapped again goes back to the tapped time`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        sleepStartedMinutesAgo(20)
        val end = wokeUpOnTheNotification()
        pressNotificationButton("Woke up at $end", "−10m")
        val moved = clockOf(java.time.LocalTime.parse(end, timeFormat(end)).minusMinutes(10))
        waitForNotification("Woke up at $moved")
        pressNotificationButton("Woke up at $moved", "✓ −10m")
        val back = waitForNotification("Woke up at $end")
        assertEquals(listOf("−5m", "−10m", "−15m"), back.buttons)
    }

    @Test
    fun `offsets before the start are left out`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        sleepStartedMinutesAgo(5)
        val end = wokeUpOnTheNotification()
        assertEquals(listOf("−5m"), notification("Woke up at $end")!!.buttons)
    }

    private fun timeFormat(shown: String) =
        java.time.format.DateTimeFormatter.ofPattern(if (shown.endsWith("M")) "h:mm a" else "HH:mm", java.util.Locale.US)
}
