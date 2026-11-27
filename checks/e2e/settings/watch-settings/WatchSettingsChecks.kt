package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WatchSettings: the watch's line, Sync now and On the watch face, on the real phone app. */
class WatchSettingsChecks : Phone() {
    @Test
    fun `a phone that can't reach the watch service`() {
        freshInstall()
        openSettings()
        waitUntil("the watch's error line", timeoutMillis = 30_000) { shows("Couldn't save · Tap to retry") }
        assertCanTap("Sync now")
        assertGone("No watch yet")
    }

    @Test
    fun `what the watch face shows`() {
        freshInstall()
        openSettings()
        assertShowsInOrder("On the watch face", "Next feed", "Next poop", "Next sleep and wake-up")
        assertShows("The watch face shows the earliest of these, while the baby sleeps and while awake.")
        val all = listOf("Next feed", "Next poop", "Next sleep and wake-up")
        waitUntil("all three ticked") { selected().containsAll(all) }
        scrollAndTap("Next poop")
        waitUntil("Next poop unticked") { selected().let { "Next poop" !in it && "Next feed" in it && "Next sleep and wake-up" in it } }
    }
}
