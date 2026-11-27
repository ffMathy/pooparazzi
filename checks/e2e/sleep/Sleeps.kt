package com.ffmathy.pooparazzi.checks

/*
 * The data the sleep scenarios start from, made the way a parent makes it: with the sleep button and the receipt's
 * nudges on Home. Each helper ends on Home with no sheet open, once the guard and the cooldown are over.
 */

/** Start sleep, and the receipt once it takes taps. */
fun Phone.startSleepReceipt() {
    tap("Start sleep")
    waitFor("Sleep started")
    pass(600)
}

/** Waits out the two-second guard after a tap on the sleep button, and Home's cooldown after a sheet. */
fun Phone.waitOutTheGuard() = pass(2_500)

/** A sleep that started 20 minutes ago: Start sleep, then −15m and −5m on its receipt, and Done. */
fun Phone.sleepStarted20MinutesAgo() {
    startSleepReceipt()
    tap("−15m")
    tap("−5m")
    tap("Done")
    waitUntilGone("Sleep started")
    waitOutTheGuard()
}

/** Woke up, and its receipt once it takes taps. */
fun Phone.wokeUpReceipt() {
    tap("Woke up")
    waitFor("Undo")
    pass(600)
}
