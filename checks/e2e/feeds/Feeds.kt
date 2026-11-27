package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The data the feed and diaper scenarios start from, made the way a parent makes it: with Feed and Diaper on Home,
 * their sheets' nudges, and History › Add entry. Also what a parent sees on those sheets that the harness does not
 * read by itself: whether a tile or chip is ticked or picked, and the bottle amount the slider shows (and speaks).
 */

/** Feed, and its sheet once it takes taps (the picker's 500 ms); returns the sheet's time as it shows it. */
fun Phone.feedSheet(): String {
    tap("Feed")
    waitFor("Just the time")
    pass(600)
    return sheetTime()
}

/** Diaper, and its sheet once it takes taps; returns the sheet's time as it shows it. */
fun Phone.diaperSheet(): String {
    tap("Diaper")
    waitFor("Save")
    pass(600)
    return sheetTime()
}

/** The time a Feed or Diaper sheet stands at now, as its window title says it ("Feed · 4:39 PM" is "4:39 PM"). */
fun Phone.sheetTime(): String {
    var time = ""
    waitUntil("the sheet's time") { sheetTitle()?.substringAfter(" · ")?.also { time = it } != null }
    return time
}

/** The open sheet's window title: "Feed · 4:39 PM", "Breast · 4:39 PM", "Diaper · 5:02 PM". */
fun Phone.sheetTitle(): String? = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle), useUnmergedTree = true)
    .fetchSemanticsNodes().map { it.config[SemanticsProperties.PaneTitle] }
    .firstOrNull { Regex("^(Feed|Breast|Bottle|Diaper) · .+").matches(it) }

/** Waits out Home's 600 ms after a sheet that logged, and the two-second notice of a repeat log, if any. */
fun Phone.waitOutTheCooldown() = pass(1_000)

/** The Feed sheet nudged [minutes] back with its nudges (a multiple of 5); returns the sheet's time then. */
fun Phone.nudgeBack(minutes: Int): String {
    var left = minutes
    while (left >= 15) { tap("−15m"); left -= 15 }
    if (left >= 10) { tap("−10m"); left -= 10 }
    if (left >= 5) { tap("−5m"); left -= 5 }
    require(left == 0) { "nudges move by 5 minutes" }
    return sheetTime()
}

/** Food logged [minutesAgo] minutes ago (a multiple of 5) from the Feed sheet; returns its time as the sheet showed it. */
fun Phone.foodLogged(minutesAgo: Int = 0): String {
    feedSheet()
    val at = nudgeBack(minutesAgo)
    tap("Food")
    waitUntilGone("Just the time")
    waitOutTheCooldown()
    return at
}

/** Whether the checkbox that reads (or is spoken as) [text] is ticked. */
fun Phone.isTicked(text: String): Boolean = compose.onAllNodes(
    (hasText(text) or hasContentDescription(text, substring = true)) and hasClickAction(),
).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On }

/** Whether the choice (a chip, Milk or Formula) that reads [text] is picked. */
fun Phone.isPicked(text: String): Boolean = compose.onAllNodes(hasText(text) and hasClickAction())
    .fetchSemanticsNodes().any {
        it.config.getOrNull(SemanticsProperties.Selected) == true ||
            it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
    }

/** The bottle amount the slider shows and speaks: "No amount", "120 ml", "4 fl oz". */
fun Phone.bottleAmount(): String? = compose.onAllNodes(hasContentDescription("Amount"), useUnmergedTree = true).fetchSemanticsNodes()
    .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.StateDescription) }

/** Moves the amount slider to its [step] (0 is "No amount"), as TalkBack or a drag does. */
fun Phone.slideAmountTo(step: Int) {
    compose.onAllNodes(hasContentDescription("Amount") and SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
        .performSemanticsAction(SemanticsActions.SetProgress) { it(step.toFloat()) }
    pass(300)
}

/** A tap on the dimmed Home above an open sheet (the scrim). */
fun Phone.tapOutsideTheSheet() {
    compose.onAllNodes(isDialog())[0].performTouchInput { click(Offset(centerX, 60f)) }
    pass(800)
}

/** "Monday, November 23, 2026", as the date picker names a day on a US phone. */
private fun pickerName(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))

/**
 * History › Add entry on the day [daysAgo] before today, a feed at that day's clock time of 15 minutes ago (Add past
 * entry's default) moved [minutesEarlier] back with its −5m nudge, saved. The first day before yesterday is reached
 * with the day chip's "Earlier date…" (History goes back only as far as the log); later ones with Previous day.
 * Starts and ends in History, on that day.
 */
fun Phone.addPastFeed(daysAgo: Long, minutesEarlier: Int) {
    openAddPastEntry(daysAgo)
    if (!shows("Time day, ${dayName(daysAgo)}")) {
        tap("Time day, Today")
        pass(500)
        if (daysAgo == 1L) {
            tap("Yesterday")
        } else {
            tap("Earlier date…")
            waitFor("Select date")
            val name = pickerName(LocalDate.now().minusDays(daysAgo))
            if (!shows(name)) tap("Change to previous month")
            tap(name)
            tap("OK")
        }
        pass(500)
    }
    repeat(minutesEarlier / 5) { tap("Time, 5 minutes earlier") }
    saveThePastEntry()
}

/**
 * History › Add entry on the day [daysAgo] before today, a sleep: Add past entry's default (from that day's clock time
 * of 15 minutes ago, for 15 minutes), both ends moved [minutesEarlier] back with the −5m nudges, saved. History must
 * already reach that day (a past feed made with [addPastFeed] on it or before it). Starts and ends in History.
 */
fun Phone.addPastSleep(daysAgo: Long, minutesEarlier: Int) {
    openAddPastEntry(daysAgo)
    tap("Sleep")
    waitFor("Fell asleep")
    check(shows("Fell asleep day, ${dayName(daysAgo)}")) { "History doesn't reach $daysAgo days ago:\n${screen()}" }
    repeat(minutesEarlier / 5) { tap("Fell asleep, 5 minutes earlier") }
    repeat(minutesEarlier / 5) { tap("Woke up, 5 minutes earlier") }
    saveThePastEntry()
}

private fun Phone.openAddPastEntry(daysAgo: Long) {
    showHistoryDay(daysAgo)
    tap("Add entry")
    waitFor("Add past entry")
    pass(600)
}

private fun Phone.saveThePastEntry() {
    tap("Save")
    waitUntilGone("Add past entry")
    pass(500)
}

/** "Expected 4:40 PM" under a sheet's nudges, if it is offered: returns the time it offers. */
fun Phone.expectedOffer(): String? = compose.onAllNodes(hasText("Expected ", substring = true) and hasClickAction())
    .fetchSemanticsNodes().firstNotNullOfOrNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull { it.text.startsWith("Expected ") }?.text?.removePrefix("Expected ")
    }

/** The time a parent hears for a time [minutes] ago, as the Lamp and the since-rows speak it ("just now", "15 minutes ago"). */
fun ago(minutes: Long): String = if (minutes == 0L) "just now" else "${spokenMinutes(minutes)} ago"

/** Waits until the Lamp speaks the last feed: "Last feed, food, 15 minutes ago, at 4:44 PM" ([what] "food", or "" for no type). */
fun Phone.waitForLastFeed(what: String, at: String) {
    val kind = if (what.isEmpty()) "" else "$what, "
    waitUntil("the Lamp's last feed, $what at $at") { shows("Last feed, $kind${ago(clockMinutesBetween(at, clock()))}, at $at") }
}

/** Waits until Home's diaper row speaks the last change: "Last diaper, pee and poop, 10 minutes ago, at 4:52 PM". */
fun Phone.waitForLastDiaper(what: String, at: String) {
    val kind = if (what.isEmpty()) "" else "$what, "
    waitUntil("the last diaper, $what at $at") { shows("Last diaper, $kind${ago(clockMinutesBetween(at, clock()))}, at $at") }
}

/** Today's counts, as Home speaks them: "Today: 1 feed, 0 pee, 0 poop, …". */
fun Phone.todayCounts(): String = compose.onAllNodes(hasContentDescription("Today: ", substring = true), useUnmergedTree = true)
    .fetchSemanticsNodes().firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() } ?: ""

private fun dayName(daysAgo: Long): String = when (daysAgo) {
    0L -> "Today"
    1L -> "Yesterday"
    else -> LocalDate.now().minusDays(daysAgo).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
}

/** Opens History (from Home, if needed) and steps back to [daysAgo] as far as the log reaches. */
private fun Phone.showHistoryDay(daysAgo: Long) {
    if (!shows("Add entry")) {
        tap("History")
        waitFor("Add entry")
    }
    repeat(14) {
        if (!canTap("Previous day") && !canTap("Next day")) return
        val shownAgo = (0L..14L).firstOrNull { ago -> showsPart(historyDayTitle(ago)) } ?: return
        when {
            shownAgo < daysAgo && canTap("Previous day") -> tap("Previous day")
            shownAgo > daysAgo && canTap("Next day") -> tap("Next day")
            else -> return
        }
        pass(300)
    }
}

/** History's day title, "Fri 27 Nov" (after "Today · " or "Yesterday · " on those days). */
private fun historyDayTitle(daysAgo: Long): String =
    LocalDate.now().minusDays(daysAgo).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
