package com.ffmathy.pooparazzi.checks

/*
 * The data the widget scenarios start from, made the way a parent makes it: widgets placed from the picker
 * (HomeScreen.kt), taps on them, and Home's sleep button with its receipt's nudges (checks/e2e/sleep/Sleeps.kt).
 */

/** The 4×2 "Log and status" widget at [widthDp] × [heightDp] (its reference size by default). */
fun Phone.placeLogWidget(widthDp: Int = 358, heightDp: Int = 208) = placeWidget("Log and status", widthDp, heightDp)

/** The 3×1 "Feed, diaper and sleep" widget at [widthDp] × 100. */
fun Phone.placeQuickWidget(widthDp: Int = 260) = placeWidget("Feed, diaper and sleep", widthDp, 100)

/** Waits until [widget] shows "[prefix]{time}" with the time now (or a minute ago), and returns that time. */
fun Phone.widgetTimeAfter(widget: HomeScreenWidget, prefix: String): String {
    var time = ""
    waitUntilWidget(widget, "\"$prefix{time}\"") { recentClocks().firstOrNull { widgetShows(widget, "$prefix$it") }?.also { time = it } != null }
    return time
}

/** Start sleep on [widget], and its receipt; returns the start the receipt shows. */
fun Phone.startSleepOnWidget(widget: HomeScreenWidget): String {
    tapWidget(widget, "Start sleep")
    return widgetTimeAfter(widget, "Sleep started at ")
}

/** A sleep started on Home and nudged 15 minutes back with its receipt's −15m, then Done; returns its start as shown. */
fun Phone.sleepStarted15MinutesAgo(): String {
    val now = startSleepReceipt()
    tap("−15m")
    var start = ""
    waitUntil("the start 15 minutes before $now") { recentClocks(-15).firstOrNull { showsPart("$it · was $now") }?.also { start = it } != null }
    tap("Done")
    waitUntilGone("Sleep started")
    waitOutTheGuard()
    return start
}

/** The app's Feed sheet is open on Home, titled "Feed · " and a time now (or a minute ago). */
fun Phone.assertFeedSheetOpenNow() {
    waitFor("Just the time")
    val title = windowTitleStartingWith("Feed · ") ?: throw AssertionError("no Feed sheet:\n${screen()}")
    if (recentClocks().none { title == "Feed · $it" }) throw AssertionError("the Feed sheet reads \"$title\", not the time now:\n${screen()}")
    for (option in listOf("Breast", "Bottle", "Food", "Just the time")) assertShows(option)
}
