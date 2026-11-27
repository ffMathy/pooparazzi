// ail-derived-from: NotificationSettings@ed30d41936915461
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for NotificationSettings: Settings › Notifications on a new install, on the real phone app. */
class NotificationSettingsChecks : Phone() {
    @Test
    fun `notifications not allowed yet`() {
        freshInstall()
        openSettingsFromHome()
        assertShowsTextsInOrder("Notifications", "Notifications are off", "Turn them on to see a running sleep outside the app")
        assertShowsTextsInOrder("What comes next", "Next feed", "Off", "Next poop", "Off", "Next sleep and wake-up", "Off")
        assertShows("What Home expects next, as a silent notification while the app is closed. Tap one to turn it on or off in Android's notification settings.")
        assertGone("On")
    }
}
