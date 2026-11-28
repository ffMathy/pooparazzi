package com.ffmathy.pooparazzi.checks

/*
 * The data the watch scenarios start from, made the way a parent makes it on the watch: with Main's rows and sleep
 * edge button, the logging screens, and Logged's time (the Time screen). Each helper that logs returns the time Logged
 * showed; the ones that end "…AndClose" end on Main, with Logged closed by a tap on its background.
 */

/** "1 minute" or "21 minutes", as the watch speaks a duration of whole minutes (none: "less than a minute"). */
fun spokenWatchMinutes(minutes: Long): String = when (minutes) {
    0L -> "less than a minute"
    1L -> "1 minute"
    else -> "$minutes minutes"
}

/** "<1m", "1m" or "12m": how a running sleep's length is written ("Asleep <1m"), in whole clock minutes. */
fun asleepFor(minutes: Long): String = if (minutes == 0L) "<1m" else "${minutes}m"

/** "just now" or "12 minutes ago", as Main and the Tile speak a time since in whole clock minutes. */
fun spokenSince(minutes: Long): String = if (minutes == 0L) "just now" else "${spokenWatchMinutes(minutes)} ago"

/**
 * Waits until the screen shows what [expected] says for the whole clock minutes since [from], counted again on every
 * look: the minute may turn between the log and the look, and Main re-words itself only on that minute.
 */
fun Watch.waitForSince(from: String, what: String, expected: (minutes: Long) -> List<String>) =
    waitUntil(what) { expected(clockMinutesBetween(from, clock())).all { shows(it) || showsPart(it) } }

/** "just now", "1m" or "12m": how Main and the Tile write the time since, in whole clock minutes. */
fun shortSince(minutes: Long): String = if (minutes == 0L) "just now" else "${minutes}m"

/** The clock time on the screen that reads "[before]{time}[after]", the time a moment ago or now. */
fun Watch.shownTime(before: String, after: String = "", minutesFromNow: Long = 0): String {
    var time = ""
    waitUntil("\"$before{time}$after\"") { recentClocks(minutesFromNow).firstOrNull { shows("$before$it$after") }?.also { time = it } != null }
    return time
}

/** Waits out Logged's first second, in which it ignores taps. */
fun Watch.waitOutLoggedInertStart() = pass(1_100)

/** Closes Logged with a tap on its background, once it takes taps, and waits for Main. */
fun Watch.closeLogged() {
    waitOutLoggedInertStart()
    tapTheBackground()
    waitUntilGone("Undo")
    waitUntil("Main") { showsPart("Feed. ") }
}

/** The Feed row on Main: Feed type opens, and its half second of ignored taps passes. */
fun Watch.openFeedType() {
    tapStarting("Feed. ")
    waitFor("Just the time")
    pass(600)
}

/** The Diaper row on Main: Diaper opens, and its half second of ignored taps passes. */
fun Watch.openDiaper() {
    tapStarting("Diaper. ")
    waitFor("Save")
    pass(600)
}

/** Feed, then Food: Logged "Food at …". Returns the feed's time. */
fun Watch.logFood(): String {
    openFeedType()
    tap("Food")
    return shownTime("Food at ")
}

/** Diaper, then Save with nothing ticked: Logged "Diaper at …". Returns the diaper's time. */
fun Watch.logPlainDiaper(): String {
    openDiaper()
    tap("Save")
    return shownTime("Diaper at ")
}

/** Diaper, Pee, Save: Logged "Pee at …". Returns the diaper's time. */
fun Watch.logPee(): String {
    openDiaper()
    tap("Pee")
    tap("Save")
    return shownTime("Pee at ")
}

/** Start sleep on Main: Logged "Asleep at …". Returns the sleep's start. */
fun Watch.startSleepOnTheWatch(): String {
    tap("Start sleep")
    return shownTime("Asleep at ")
}

/**
 * A sleep that started [minutes] minutes ago (a multiple of 5): Start sleep, then its time on Logged moved back with
 * −5m on Time, Done, and Logged closed. Returns its start as shown.
 */
fun Watch.sleepStartedMinutesAgo(minutes: Int): String {
    val now = startSleepOnTheWatch()
    waitOutLoggedInertStart()
    tap("Asleep at $now")
    waitFor("5 minutes earlier")
    pass(600)
    repeat(minutes / 5) { tap("5 minutes earlier") }
    tap("Done")
    val start = shownTime("Asleep at ", minutesFromNow = -minutes.toLong())
    closeLogged()
    return start
}
