package com.ffmathy.pooparazzi.checks

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.assertTrue

/*
 * The steps the first-run scenarios share. Phone.freshInstall() skips the first run; these go through it as a parent
 * does: Your baby, then the introduction.
 */

/** A new install's first screen, Your baby, once it takes taps. */
fun Phone.onYourBaby() {
    waitFor("Skip for now")
    pass(500)
}

/** Your baby answered with "Skip for now": the introduction's first page, once it takes taps. */
fun Phone.onIntroduction() {
    onYourBaby()
    tap("Skip for now")
    waitFor("Welcome to Pooparazzi")
    pass(500)
}

/** Skips the introduction and waits for Home, once it takes taps. */
fun Phone.skipIntroduction() {
    tap("Skip")
    waitFor("Start sleep")
    pass(1_000)
}

/** Home › Settings, once it has loaded. */
fun Phone.openSettingsFromHome() {
    tap("Settings")
    waitFor("Appearance")
    pass(500)
}

/** Today as a birth date pill reads it: "Born Nov 27, 2026" (English), "Født 27. nov. 2026" (Danish). */
fun birthDatePillToday(locale: Locale = Locale.US): String {
    val date = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    return if (locale.language == "da") "Født $date" else "Born $date"
}

/** Whether the texts appear on the screen in this order, top to bottom (each one exactly, as a text of its own). */
fun Phone.showsTextsInOrder(vararg texts: String): Boolean {
    val lines = screen().lines()
    var from = 0
    for (text in texts) {
        val at = (from until lines.size).firstOrNull { lines[it] == "Text = '[$text]'" } ?: return false
        from = at + 1
    }
    return true
}

fun Phone.assertShowsTextsInOrder(vararg texts: String) =
    assertTrue("expected ${texts.toList()} in this order on the screen:\n${screen()}", showsTextsInOrder(*texts))
