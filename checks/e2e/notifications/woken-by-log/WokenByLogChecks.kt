package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for WokenByLog: a feed or diaper that ends a sleep says so in the shade. */
class WokenByLogChecks : Phone() {
    @Test
    fun `a diaper ends the sleep`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        val start = sleepStartedMinutesAgo(20)
        diaperNow(pee = true)
        val end = notificationTimeAfter("Woke up at ")
        val shown = notification("Woke up at $end")!!
        val slept = "Slept ${shownDuration(clockMinutesBetween(start, end))}"
        assertEquals("The diaper change ended the sleep · $slept", shown.text)
        assertEquals("The diaper change ended the sleep", shown.expanded[0])
        assertEquals("$slept · $start – $end", shown.expanded[1])
        assertEquals("Pee ${shownDuration(clockMinutesBetween(end, clock())).let { if (it == "<1m") "just now" else "$it ago" }} · $end", shown.expanded[2])
        assertEquals(true, shown.expanded[3].startsWith("Today · "))
        assertEquals(listOf("−5m", "−10m", "−15m"), shown.buttons)
    }

    @Test
    fun `a feed ends the sleep`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        val start = sleepStartedMinutesAgo(20)
        foodLogged()
        val end = notificationTimeAfter("Woke up at ")
        assertEquals(
            "The feed ended the sleep · Slept ${shownDuration(clockMinutesBetween(start, end))}",
            notification("Woke up at $end")!!.text,
        )
    }

    @Test
    fun `a log that ended the sleep over half an hour ago says nothing`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        sleepStartedMinutesAgo(50)
        asleepNotification()
        foodLogged(35)
        waitUntilShade("the sleep's notification to go") { notification("Asleep") == null }
        waitFor("Start sleep")
        pass(1_500)
        assertEquals(shadeScreen(), 0, shade().size)
    }
}
