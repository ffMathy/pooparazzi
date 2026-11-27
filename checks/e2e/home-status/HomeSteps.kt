package com.ffmathy.pooparazzi.checks

import java.time.LocalTime

/*
 * The data Home's scenarios start from, made the way a parent makes it: Feed and Diaper on Home with their sheets'
 * nudges, the sleep button (checks/e2e/sleep/Sleeps.kt), and for the past week History's Add entry
 * (checks/e2e/history-screen/PastEntries.kt). Each helper ends on Home with no sheet open, once Home takes taps again.
 */

/** Feed, the nudges [nudges] ("−15m", …), then Just the time; returns the feed's time as the sheet showed it. */
fun Phone.feedJustTheTime(nudges: List<String> = emptyList()): String {
    val time = openFeedSheet(nudges)
    tap("Just the time")
    waitUntilGone("Just the time")
    pass(1_000)
    return time
}

/** Feed, the nudges, Breast, the [sides] in order, then Log feed; returns the feed's time. */
fun Phone.breastFeed(nudges: List<String>, sides: List<String>): String {
    val time = openFeedSheet(nudges)
    tap("Breast")
    waitFor("Log feed")
    sides.forEach { tap(it) }
    tap("Log feed")
    waitUntilGone("Log feed")
    pass(1_000)
    return time
}

/** Diaper, Pee and Poop as given, then Save; returns the diaper's time. */
fun Phone.diaperNow(pee: Boolean = false, poop: Boolean = false): String {
    tap("Diaper")
    waitFor("Save")
    pass(600)
    val time = windowTitleStartingWith("Diaper · ")!!.removePrefix("Diaper · ")
    if (pee) tap("Pee")
    if (poop) tap("Poop")
    tap("Save")
    waitUntilGone("Save")
    pass(1_000)
    return time
}

private fun Phone.openFeedSheet(nudges: List<String>): String {
    tap("Feed")
    waitFor("Just the time")
    pass(600)
    nudges.forEach { tap(it) }
    // The sheet is titled with its time, which follows the nudges: "Feed · 3:56 PM".
    return windowTitleStartingWith("Feed · ")!!.removePrefix("Feed · ")
}

/** "1 hour 15 minutes", "1 hour", "20 minutes", "less than a minute": a duration of whole minutes as the app speaks it. */
fun spokenDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> spokenMinutes(m)
        m == 0L -> if (h == 1L) "1 hour" else "$h hours"
        else -> "${if (h == 1L) "1 hour" else "$h hours"} ${spokenMinutes(m)}"
    }
}

/** "just now", or "15 minutes ago": the time since [from], as the app speaks it. */
fun Phone.spokenSince(from: String): String = clockMinutesBetween(from, clock()).let { if (it == 0L) "just now" else "${spokenDuration(it)} ago" }

/** "15m", "1h 15m", "<1m": a duration of whole minutes as the app writes it. */
fun shownDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        minutes == 0L -> "<1m"
        h == 0L -> "${m}m"
        else -> "${h}h ${m.toString().padStart(2, '0')}m"
    }
}

/** Opens History, adds what [block] adds, and goes back to Home. */
fun Phone.inHistory(block: Phone.() -> Unit) {
    openHistory()
    block()
    goBack()
    waitFor("Start sleep")
    pass(1_000)
}

/** Feeds at 8:00 and 11:00 four days ago, and at 8:00, 11:00 and 14:00 three and two days ago: five gaps of 3 hours. */
fun Phone.feedsEveryThreeHours() = inHistory {
    for ((days, hours) in listOf(4L to listOf(8, 11), 3L to listOf(8, 11, 14), 2L to listOf(8, 11, 14))) {
        for (h in hours) addPastFeed(daysAgo(days), LocalTime.of(h, 0))
    }
}

/** Poop diapers at 9:00 and 21:00 on each of the last four days: seven gaps of 12 hours. */
fun Phone.poopsEveryTwelveHours() = inHistory {
    for (days in 4L downTo 1L) for (h in listOf(9, 21)) addPastDiaper(daysAgo(days), LocalTime.of(h, 0), poop = true)
}

/** Sleeps 12:00–13:30 and 15:00–16:30 two, three and four days ago: six sleeps of 1 hour 30 minutes. */
fun Phone.napsOfNinetyMinutes() = inHistory {
    for (days in 4L downTo 2L) {
        addPastSleep(daysAgo(days), LocalTime.of(12, 0), daysAgo(days), LocalTime.of(13, 30))
        addPastSleep(daysAgo(days), LocalTime.of(15, 0), daysAgo(days), LocalTime.of(16, 30))
    }
}
