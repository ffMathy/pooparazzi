package com.ffmathy.pooparazzi.checks

/*
 * The data the sleep scenarios start from, made the way a parent makes it: with the sleep button and the receipt's
 * nudges on Home. Each helper ends on Home with no sheet open, once the guard and the cooldown are over.
 */

/** Start sleep, and the receipt once it takes taps; returns the start as the receipt shows it. */
fun Phone.startSleepReceipt(): String {
    tap("Start sleep")
    waitFor("Sleep started")
    pass(600)
    var start = ""
    waitUntil("the receipt's time") { recentClocks().firstOrNull { shows(it) }?.also { start = it } != null }
    return start
}

/** Waits out the two-second guard after a tap on the sleep button, and Home's cooldown after a sheet. */
fun Phone.waitOutTheGuard() = pass(2_500)

/** A sleep that started 20 minutes ago: Start sleep, then −15m and −5m on its receipt, and Done. Returns its start as shown. */
fun Phone.sleepStarted20MinutesAgo(): String {
    val now = startSleepReceipt()
    tap("−15m")
    tap("−5m")
    var start = ""
    waitUntil("the start 20 minutes before $now") { recentClocks(-20).firstOrNull { showsPart("$it · was $now") }?.also { start = it } != null }
    tap("Done")
    waitUntilGone("Sleep started")
    waitOutTheGuard()
    return start
}

/** "1 minute" or "21 minutes", as the app speaks a duration of whole minutes (none: "less than a minute"). */
fun spokenMinutes(minutes: Long): String = when (minutes) {
    0L -> "less than a minute"
    1L -> "1 minute"
    else -> "$minutes minutes"
}

/** Woke up, and its receipt once it takes taps; returns the end as the receipt shows it. */
fun Phone.wokeUpReceipt(): String {
    tap("Woke up")
    waitFor("Undo")
    pass(600)
    var end = ""
    waitUntil("the receipt's time") { recentClocks().firstOrNull { showsPart("$it · Slept") || shows("Sleep cancelled") }?.also { end = it } != null }
    return end
}
