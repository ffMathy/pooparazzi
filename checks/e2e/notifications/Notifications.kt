package com.ffmathy.pooparazzi.checks

/*
 * The steps the notification scenarios share, done the way a parent does them: on Home (checks/e2e/sleep/Sleeps.kt,
 * checks/e2e/home-status/HomeSteps.kt) and in the shade (Shade.kt).
 */

/** Waits until the shade shows a notification titled "[prefix]{time}" with the time now (or a minute ago); returns the time. */
fun Phone.notificationTimeAfter(prefix: String, minutesFromNow: Long = 0): String {
    var time = ""
    waitUntilShade("a notification \"$prefix{time}\"") {
        recentClocks(minutesFromNow).firstOrNull { notification("$prefix$it") != null }?.also { time = it } != null
    }
    return time
}

/** The running sleep's notification, once the shade shows it. */
fun Phone.asleepNotification(): ShadeNotification = waitForNotification("Asleep")

/** Woke up on the running sleep's notification; returns the wake-up time its woke-up notification shows. */
fun Phone.wokeUpOnTheNotification(): String {
    asleepNotification()
    pressNotificationButton("Asleep", "Woke up")
    return notificationTimeAfter("Woke up at ")
}

/** A sleep started on Home and nudged [minutes] back (a multiple of 5) with its receipt, then Done; returns its start. */
fun Phone.sleepStartedMinutesAgo(minutes: Int): String {
    val now = startSleepReceipt()
    var left = minutes
    while (left >= 15) { tap("−15m"); left -= 15 }
    if (left >= 10) { tap("−10m"); left -= 10 }
    if (left >= 5) { tap("−5m"); left -= 5 }
    var start = ""
    waitUntil("the start $minutes minutes before $now") { recentClocks(-minutes.toLong()).firstOrNull { showsPart("$it · was $now") }?.also { start = it } != null }
    tap("Done")
    waitUntilGone("Sleep started")
    waitOutTheGuard()
    return start
}
