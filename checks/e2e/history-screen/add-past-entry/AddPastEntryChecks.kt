package com.ffmathy.pooparazzi.checks

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for AddPastEntry: History's Add entry, every kind, and its rules, on the real phone app. */
class AddPastEntryChecks : Phone() {
    @Test
    fun `Add entry opens a feed 15 minutes ago`() {
        freshInstall()
        openHistory()
        tap("Add entry")
        waitFor("Add past entry")
        pass(600)
        assertShows(historyDay(LocalDate.now()))
        for (kind in listOf("Sleep", "Feed", "Diaper")) assertShows(kind)
        assertTrue("Feed picked:\n${screen()}", isPicked("Feed"))
        assertShows("Type · Optional")
        assertTrue("Breast picked:\n${screen()}", isPicked("Breast"))
        for (part in listOf("Bottle", "Food", "Side · Optional", "Left", "Right", "No side")) assertShows(part)
        assertShows("Time day, Today")
        assertShowsAt("Time, {time}", -15)
        for (nudge in listOf("5 minutes earlier", "1 minute earlier", "1 minute later", "5 minutes later")) assertShows("Time, $nudge")
        assertCanTap("Cancel")
        assertCanTap("Save")
    }

    @Test
    fun `a past sleep starts as the last 15 minutes`() {
        freshInstall()
        openHistory()
        openAddPastEntry("Sleep")
        assertShowsAt("Fell asleep, {time}", -15)
        assertShowsAt("Woke up, {time}", 0)
        assertShows("Fell asleep day, Today")
        assertShows("Woke up day, Today")
        assertShows("Duration 15m")
    }

    @Test
    fun `adding a past sleep`() {
        freshInstall()
        openHistory()
        addPastSleep(daysAgo(1), LocalTime.of(13, 0), daysAgo(1), LocalTime.of(14, 0))
        assertShows(historyDay(LocalDate.now()))
        assertShows("Nothing logged on this day.")
        tap("Previous day")
        waitFor("Sleep · 1h 00m")
    }

    @Test
    fun `a past sleep can't overlap another`() {
        freshInstall()
        openHistory()
        addPastSleep(daysAgo(1), LocalTime.of(13, 0), daysAgo(1), LocalTime.of(14, 0))
        openAddPastEntry("Sleep")
        setDay("Fell asleep", daysAgo(1))
        setTime("Fell asleep", LocalTime.of(13, 30))
        setDay("Woke up", daysAgo(1))
        setTime("Woke up", LocalTime.of(15, 0))
        waitFor("Overlaps the ${clockOf(LocalTime.of(13, 0))} sleep. Sleeps can't overlap.")
        assertCannotTap("Save")
    }

    @Test
    fun `a time that hasn't happened yet offers yesterday`() {
        val later = LocalTime.now().plusMinutes(10).let { it.withMinute(it.minute / 5 * 5).withSecond(0).withNano(0) }.plusMinutes(5)
        freshInstall()
        openHistory()
        openAddPastEntry("Feed")
        setTime("Time", later)
        waitFor("${clockOf(later)} hasn't happened yet. Log it for yesterday?")
        assertCannotTap("Save")
        tapTop("Use yesterday")
        waitFor("Time day, Yesterday")
        assertGone("${clockOf(later)} hasn't happened yet. Log it for yesterday?")
        tapTop("Save")
        waitUntilGone("Add past entry")
        tap("Previous day")
        waitFor(historyDay(daysAgo(1)))
        assertShowsPart(clockOf(later))
    }

    @Test
    fun `every kind of past entry is listed by its kind`() {
        freshInstall()
        openHistory()
        val y = daysAgo(1)
        addPastFeed(y, LocalTime.of(8, 0), "Bottle", contents = "Milk")
        addPastFeed(y, LocalTime.of(9, 0), "Food")
        addPastFeed(y, LocalTime.of(10, 0), "Breast", sides = listOf("Left"))
        addPastDiaper(y, LocalTime.of(11, 0))
        addPastDiaper(y, LocalTime.of(12, 0), pee = true, poop = true)
        tap("Previous day")
        waitFor(historyDay(y))
        val titles = listOf("Pee + poop", "Diaper", "Breast · Left", "Food", "Bottle · Milk")
        val at = titles.map { texts().indexOf(it) }
        assertTrue("$titles in that order:\n${screen()}", at.all { it >= 0 } && at == at.sorted())
        for (h in 8..12) assertShows(clockOf(LocalTime.of(h, 0)))
    }
}
