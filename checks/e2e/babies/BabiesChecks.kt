package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.test.hasSetTextAction
import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for Babies: the name and birth date fields of Settings › Baby, on the real phone app. */
class BabiesChecks : Phone() {
    @Test
    fun `naming the baby in settings`() {
        freshInstall()
        openSettings()
        nameFirstBaby("Noa")
        goBack()
        waitForInDialog("Start sleep")
        waitForInDialog("Noa")
    }

    @Test
    fun `one baby has no remove button`() {
        freshInstall()
        openSettings()
        assertShowsInOrder("Baby", "Name", "Add birth date", "Add a baby")
        assertEquals(1, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertGone("Babies")
        assertGone("Remove Baby")
        assertShows("The birth date is optional, but it improves the predictions and estimates: they then follow the baby's age.")
        assertShows("Names and birth dates stay on this phone and your watch.")
    }

    @Test
    fun `giving and removing a birth date in settings`() {
        freshInstall()
        openSettings()
        tapInDialog("Add birth date")
        waitForInDialog("Birth date")
        pickTodayAsBirthDate()
        waitForInDialog(bornToday())
        assertGone("The birth date is optional, but it improves the predictions and estimates: they then follow the baby's age.")
        tapInDialog(bornToday())
        waitForInDialog("Birth date")
        tapInDialog("Remove")
        waitForInDialog("Add birth date")
        assertGone(bornToday())
    }
}
