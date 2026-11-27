package com.ffmathy.pooparazzi.checks

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Test

/** End-to-end check for YourBaby: the first run's name and birth date, on the real phone app. */
class YourBabyChecks : Phone() {
    @Test
    fun `what your baby asks`() {
        onYourBaby()
        assertShows("Your baby")
        assertShows("Both are optional. You can skip them now and add them later in Settings › Baby.")
        assertShows("Name")
        assertCanTap("Add birth date")
        assertCanTap("Continue")
        assertCanTap("Skip for now")
        assertShows("The birth date is optional, but it improves the predictions and estimates: they then follow the baby's age.")
        assertShows("Names and birth dates stay on this phone and your watch.")
    }

    @Test
    fun `naming the baby and giving a birth date`() {
        onYourBaby()
        typeBabyName("Noa")
        tapInTopWindow("Add birth date")
        waitForInTopWindow("Birth date")
        pickTodayAsBirthDate()
        waitForInTopWindow(birthDatePillToday())
        tap("Continue")
        waitForInTopWindow("Welcome to Pooparazzi")
        pass(500)
        skipIntroduction()
        assertShows("Noa")
        openSettingsFromHome()
        assertShows("Noa")
        assertShows(birthDatePillToday())
    }

    @Test
    fun `a birth date can't be in the future`() {
        onYourBaby()
        tapInTopWindow("Add birth date")
        waitForInTopWindow("Birth date")
        val today = LocalDate.now()
        assertShows(today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)))
        val tomorrow = today.plusDays(1)
        if (tomorrow.month == today.month) assertCannotTap(tomorrow.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)))
        tapInTopWindow("Cancel")
        waitUntilGoneInTopWindow("Birth date")
        assertShows("Add birth date")
    }

    @Test
    fun `continuing with nothing given`() {
        onYourBaby()
        tap("Continue")
        waitForInTopWindow("Welcome to Pooparazzi")
        pass(500)
        skipIntroduction()
        assertShows("Baby")
    }

    @Test
    fun `going back skips your baby`() {
        onYourBaby()
        typeBabyName("Noa")
        goBack()
        waitForInTopWindow("Welcome to Pooparazzi")
        pass(500)
        skipIntroduction()
        assertShows("Baby")
        assertGone("Noa")
    }
}
