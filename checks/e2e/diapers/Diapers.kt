package com.ffmathy.pooparazzi.checks

/*
 * The data the diaper scenarios start from, made the way a parent makes it: with Diaper on Home and its sheet. The
 * sheet itself is opened with diaperSheet() (checks/e2e/feeds/Feeds.kt), which also reads the sheet's time.
 */

/** Saves the open Diaper sheet, and waits until it has closed. */
fun Phone.saveTheDiaper() {
    tap("Save")
    waitUntil("the Diaper sheet closed") { sheetTitle() == null }
}

/** A pee saved from the Diaper sheet now; returns its time as the sheet showed it. */
fun Phone.peeSaved(): String {
    val at = diaperSheet()
    tap("Pee")
    saveTheDiaper()
    return at
}
