package com.ffmathy.pooparazzi.checks

import android.app.Application
import android.text.format.DateFormat
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The log the History, edit sheet and Statistics scenarios start from, made the way a parent makes it: History's
 * Add entry, its kind, its day chip (Today, Yesterday or Earlier date… and the calendar), its time (the clock dial:
 * the hour, Select minutes, the minutes, AM or PM, OK, then +1m for a minute between fives), then Save. Each helper
 * ends where it started, with no sheet open.
 */

/** Taps the last thing on the screen that reads [text]: the one in the sheet or dialog on top, not the screen under it. */
fun Phone.tapTop(text: String) {
    val matcher = (hasText(text) or hasContentDescription(text)) and hasClickAction()
    val merged = compose.onAllNodes(matcher)
    val nodes = if (merged.fetchSemanticsNodes().isNotEmpty()) merged else compose.onAllNodes(hasText(text) or hasContentDescription(text), useUnmergedTree = true)
    val count = nodes.fetchSemanticsNodes().size
    if (count == 0) throw AssertionError("nothing reads \"$text\":\n${screen()}")
    nodes[count - 1].performClick()
    pass(300)
}

/** Every text or spoken name on the screen, top to bottom. */
fun Phone.texts(): List<String> =
    screen().lines().filter { it.contains("'[") }.map { it.substringAfter("'[").substringBeforeLast("]'") }

/** The first text or spoken name on the screen that starts with [prefix] ("Fell asleep, " gives "Fell asleep, 4:47 PM"). */
fun Phone.spokenStartingWith(prefix: String): String? = texts().firstOrNull { it.startsWith(prefix) }

/** The title of the window on top that starts with [prefix] ("Feed · " gives "Feed · 4:58 PM"). */
fun Phone.windowTitleStartingWith(prefix: String): String? =
    screen().lines().filter { it.startsWith("PaneTitle = '") }.map { it.removePrefix("PaneTitle = '").removeSuffix("'") }
        .lastOrNull { it.startsWith(prefix) }

/** Whether the choice that reads [text] is the picked one. */
fun Phone.isPicked(text: String): Boolean =
    compose.onAllNodes(hasText(text) and isSelected()).fetchSemanticsNodes().isNotEmpty()

private fun is24Hour(): Boolean = DateFormat.is24HourFormat(ApplicationProvider.getApplicationContext<Application>())

/** How the phone writes [time]: "14:30", or "2:30 PM" on a 12-hour phone. */
fun clockOf(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern(if (is24Hour()) "HH:mm" else "h:mm a", Locale.US))

/** The day [days] days before today. */
fun daysAgo(days: Long): LocalDate = LocalDate.now().minusDays(days)

/** Sets the day chip of the time block [block] ("Time", "Fell asleep", "Woke up") to [day]. */
fun Phone.setDay(block: String, day: LocalDate) {
    val chip = spokenStartingWith("$block day, ") ?: throw AssertionError("no day chip for $block:\n${screen()}")
    tapTop(chip)
    waitFor("Earlier date…")
    val today = LocalDate.now()
    when (day) {
        today -> tapTop("Today")
        today.minusDays(1) -> tapTop("Yesterday")
        else -> {
            tapTop("Earlier date…")
            waitFor("Select date")
            val name = day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))
            var tries = 0
            while (!showsPart(name) && tries++ < 24) tapTop("Change to previous month")
            tapTop(name)
            tapTop("OK")
        }
    }
    pass(300)
}

/** Sets the time of the block [block] with the clock dial; [time]'s minutes must be a multiple of 5. */
fun Phone.setTime(block: String, time: LocalTime) {
    require(time.minute % 5 == 0) { "the dial picks minutes in fives" }
    val field = texts().first { it.startsWith("$block, ") && it.substringAfter(", ").first().isDigit() }
    tapTop(field)
    waitFor("Select hour")
    val hour = if (is24Hour()) time.hour else (time.hour % 12).let { if (it == 0) 12 else it }
    tapTop("$hour o'clock")
    tapTop("Select minutes")
    tapTop("${time.minute} minutes")
    if (!is24Hour()) tapTop(if (time.hour < 12) "AM" else "PM")
    tapTop("OK")
    waitUntilGone("Select hour")
}

/** Sets [block]'s time to [time]: the dial to the five minutes at or before it, then +1m for the rest. */
fun Phone.setExactTime(block: String, time: LocalTime) {
    setTime(block, time.withMinute(time.minute / 5 * 5))
    repeat(time.minute % 5) { tapTop("$block, 1 minute later") }
}

/** History's Add entry, opened on [kind] ("Sleep", "Feed" or "Diaper"). */
fun Phone.openAddPastEntry(kind: String) {
    tap("Add entry")
    waitFor("Add past entry")
    pass(600)
    tapTop(kind)
}

/**
 * Adds a feed on [day] at [time] through History's Add entry; [type] "Breast", "Bottle" or "Food" (none: just the time),
 * then a bottle's [contents] ("Milk", "Formula") or a breast feed's [sides] in the order ticked.
 */
fun Phone.addPastFeed(day: LocalDate, time: LocalTime, type: String? = null, contents: String? = null, sides: List<String> = emptyList()) {
    openAddPastEntry("Feed")
    // A new feed starts with a type already picked; tap so that only [type] is picked (none for just the time).
    for (t in listOf("Breast", "Bottle", "Food")) if ((t == type) != isPicked(t)) tapTop(t)
    contents?.let { tapTop(it) }
    sides.forEach { tapTop(it) }
    setDay("Time", day)
    setExactTime("Time", time)
    tapTop("Save")
    waitUntilGone("Add past entry")
    pass(500)
}

/** Adds a feed with just its time at [at], to the minute. */
fun Phone.addPastFeedAt(at: LocalDateTime) = addPastFeed(at.toLocalDate(), at.toLocalTime().withSecond(0).withNano(0))

/** Adds a diaper on [day] at [time] through History's Add entry, with Pee and Poop ticked as given. */
fun Phone.addPastDiaper(day: LocalDate, time: LocalTime, pee: Boolean = false, poop: Boolean = false) {
    openAddPastEntry("Diaper")
    if (pee) tapTop("Pee")
    if (poop) tapTop("Poop")
    setDay("Time", day)
    setExactTime("Time", time)
    tapTop("Save")
    waitUntilGone("Add past entry")
    pass(500)
}

/** Adds a sleep from [fromDay] at [from] to [toDay] at [to] through History's Add entry. */
fun Phone.addPastSleep(fromDay: LocalDate, from: LocalTime, toDay: LocalDate, to: LocalTime) {
    openAddPastEntry("Sleep")
    setDay("Fell asleep", fromDay)
    setExactTime("Fell asleep", from)
    setDay("Woke up", toDay)
    setExactTime("Woke up", to)
    tapTop("Save")
    waitUntilGone("Add past entry")
    pass(500)
}

/** Opens History from Home and waits for it. */
fun Phone.openHistory() {
    tap("History")
    waitFor("Previous day")
    pass(500)
}

/** History's day as it reads it: "Today · Fri 27 Nov", "Yesterday · Thu 26 Nov", or "Wed 25 Nov". */
fun historyDay(day: LocalDate): String = when (day) {
    LocalDate.now() -> "Today · ${shortDate(day)}"
    LocalDate.now().minusDays(1) -> "Yesterday · ${shortDate(day)}"
    else -> shortDate(day)
}

/** "Fri 27 Nov", as History and the chart's readout write a day. */
fun shortDate(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))

/** "Wed", a day's short name, as History writes it before a sleep that began the day before. */
fun shortWeekday(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEE", Locale.UK))
