package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for Introduction: the six pages, Next, Back, Skip and Get started, on the real phone app. */
class IntroductionChecks : Phone() {
    private val pages = listOf(
        "Welcome to Pooparazzi" to "Log sleep, feeds and diapers with one or two taps, made for one hand at 3am. Here is how it works; it takes a minute.",
        "Sleep" to "Tap Start sleep when the baby falls asleep, and the same button, now Woke up, when they wake. While the baby sleeps, Home shows how long; tap the ring to change when the sleep started.",
        "Feeds and diapers" to "Tap Feed or Diaper. Only the time is needed: the type, the breast side, pee or poop and the rest are optional, so Just the time is fine. Did it happen a little earlier? −15m, −10m and −5m move the time back.",
        "Fix anything" to "Every log can be changed. Tap Undo right after a sleep, or tap a time on Home (Last fed, a since row) or any row in History to edit it or delete it.",
        "What comes next" to "Under each button, Home shows what the last 7 days expect next, such as the next feed. History shows each day on a 24-hour ribbon, and Statistics the averages per day.",
        "Always at hand" to "Add a widget to your home screen, and install Pooparazzi on your Wear OS watch for its tile and complications. Signed in, your family can log too. You can see this introduction again in Settings › About.",
    )

    private fun next(to: String) {
        tap("Next")
        waitFor(to)
        pass(500)
    }

    @Test
    fun `the six pages`() {
        onIntroduction()
        pages.forEachIndexed { index, (title, body) ->
            if (index > 0) next(title)
            assertShows(title)
            assertShows(body)
            assertShows("Page ${index + 1} of 6")
        }
        assertCanTap("Get started")
        assertGone("Next")
        assertGone("Skip")
    }

    @Test
    fun `back on the first page can't be tapped`() {
        onIntroduction()
        assertCanTap("Skip")
        assertCanTap("Next")
        assertCannotTap("Back")
    }

    @Test
    fun `back turns to the page before`() {
        onIntroduction()
        next("Sleep")
        tap("Back")
        waitFor("Welcome to Pooparazzi")
        assertShows("Page 1 of 6")
    }

    @Test
    fun `skipping the introduction`() {
        onIntroduction()
        next("Sleep")
        next("Feeds and diapers")
        tap("Skip")
        waitFor("Start sleep")
        assertGone("Feeds and diapers")
    }

    @Test
    fun `getting started`() {
        onIntroduction()
        for ((title, _) in pages.drop(1)) next(title)
        tap("Get started")
        waitFor("Start sleep")
        assertGone("Always at hand")
    }

    @Test
    fun `the introduction from settings`() {
        freshInstall()
        openSettingsFromHome()
        scrollDownAndTap("Introduction")
        waitFor("Welcome to Pooparazzi")
        pass(500)
        goBack()
        waitFor("Appearance")
        assertGone("Welcome to Pooparazzi")
        pass(500)
        scrollDownAndTap("Introduction")
        waitFor("Welcome to Pooparazzi")
        pass(500)
        next("Sleep")
        goBack()
        waitFor("Welcome to Pooparazzi")
        assertShows("Page 1 of 6")
    }
}
