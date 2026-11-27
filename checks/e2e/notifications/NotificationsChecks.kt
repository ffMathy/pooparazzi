package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for Notifications: the permission on the first sleep, and a tap on a notification. */
class NotificationsChecks : Phone() {
    @Test
    fun `the first sleep asks for notifications`() {
        freshInstall()
        val start = startSleepReceipt()
        waitUntil("the system's question") { asksToAllowNotifications() }
        answerNotificationDialog(allow = true)
        val shown = asleepNotification()
        assertTrue(shown.toString(), shown.text in listOf("<1m · since $start", "1m · since $start"))
        assertEquals(listOf("Woke up"), shown.buttons)
    }

    @Test
    fun `notifications not allowed show nothing`() {
        freshInstall()
        startSleepReceipt()
        waitUntil("the system's question") { asksToAllowNotifications() }
        answerNotificationDialog(allow = false)
        pass(1_000)
        assertEquals(shadeScreen(), 0, shade().size)
        tap("Undo")
        waitOutTheGuard()
        startSleepReceipt()
        pass(1_000)
        assertFalse("asked again", asksToAllowNotifications())
        assertEquals(shadeScreen(), 0, shade().size)
    }

    @Test
    fun `notifications allowed later in the system's settings`() {
        freshInstall()
        startSleepReceipt()
        waitUntil("the system's question") { asksToAllowNotifications() }
        answerNotificationDialog(allow = false)
        tap("Done")
        waitOutTheGuard()
        allowNotificationsInSystemSettings()
        leaveAndComeBackToTheApp()
        asleepNotification()
    }

    @Test
    fun `a tap on the notification opens Home`() {
        freshInstall()
        allowNotificationsInSystemSettings()
        startSleepReceipt()
        tap("Done")
        waitOutTheGuard()
        asleepNotification()
        tapNotification("Asleep")
        assertShowsPart("Asleep, ")
        assertShows("Woke up, end sleep")
    }
}
