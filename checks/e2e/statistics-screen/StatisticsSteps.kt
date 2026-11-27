package com.ffmathy.pooparazzi.checks

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The days the Statistics scenarios start from, made through History's Add entry (checks/e2e/history-screen/
 * PastEntries.kt), and the dates as Statistics writes and speaks them. Each helper ends on Home.
 */

private fun withYear(from: LocalDate, to: LocalDate) = from.year != to.year || to.year != LocalDate.now().year

/** "28 October", or "28 October 2025" when the range spans two years or is not this year, as the range is spoken. */
fun spokenDate(day: LocalDate, from: LocalDate, to: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern(if (withYear(from, to)) "d MMMM yyyy" else "d MMMM", Locale.UK))

/** "Wednesday 25 November", as the chart's summary speaks a bar's day. */
fun spokenDay(day: LocalDate, from: LocalDate, to: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern(if (withYear(from, to)) "EEEE d MMMM yyyy" else "EEEE d MMMM", Locale.UK))

/** "28 Oct", as the chart writes its first and last dates. */
fun chartDate(day: LocalDate, from: LocalDate, to: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern(if (withYear(from, to)) "d MMM yyyy" else "d MMM", Locale.UK))

/** The first day of the last [days] days: every range ends yesterday. */
fun rangeStart(days: Long): LocalDate = daysAgo(days)

/** Opens Statistics from Home and waits for it. */
fun Phone.openStatistics() {
    tap("Statistics")
    waitFor("Back")
    waitUntil("Statistics") { showsPart("Last 30 days") || shows("Statistics start as soon as something is logged.") }
    pass(500)
}

/** On each of the last four days: a feed at 8:00, a Bottle at 11:00, a pee at 9:00 (with poop four and two days ago), a sleep 12:00–13:30. */
fun Phone.fourOrdinaryDays() = inHistory {
    for (days in 4L downTo 1L) {
        val day = daysAgo(days)
        addPastFeed(day, LocalTime.of(8, 0))
        addPastDiaper(day, LocalTime.of(9, 0), pee = true, poop = days % 2 == 0L)
        addPastFeed(day, LocalTime.of(11, 0), "Bottle")
        addPastSleep(day, LocalTime.of(12, 0), day, LocalTime.of(13, 30))
    }
}
