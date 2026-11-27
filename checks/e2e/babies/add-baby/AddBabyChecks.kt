package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.test.hasSetTextAction
import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for AddBaby: the Add a baby dialog, labels and the limit of eight, on the real phone app. */
class AddBabyChecks : Phone() {
    private fun nameFields() = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size

    @Test
    fun `what add a baby asks`() {
        freshInstall()
        openSettings()
        nameFirstBaby("Noa")
        tapInDialog("Add a baby")
        waitFor("Cancel")
        assertEquals(2, nameFields())
        assertShows("Add birth date")
        assertShows("The birth date is optional, but it improves the predictions and estimates: they then follow the baby's age.")
        assertShows("Names and birth dates stay on this phone and your watch.")
        assertCanTap("Cancel")
        assertCanTap("Add")
    }

    @Test
    fun `adding a second baby`() {
        freshInstall()
        openSettings()
        noaAndIdaInSettings()
        waitFor("Babies")
        assertShows("Remove Noa")
        assertShows("Remove Ida")
        assertShows("Noa")
        assertShows("Ida")
        goBack()
        waitFor("Noa. Switch baby")
    }

    @Test
    fun `adding a baby with a birth date`() {
        freshInstall()
        openSettings()
        tapInDialog("Add a baby")
        waitFor("Cancel")
        tapInDialog("Add birth date")
        waitFor("Birth date")
        pickTodayAsBirthDate()
        waitFor(bornToday())
        tapInDialog("Add")
        waitUntilGone("Cancel")
        waitFor("Babies")
        assertShows(bornToday())
    }

    @Test
    fun `cancelling adds nothing`() {
        freshInstall()
        openSettings()
        tapInDialog("Add a baby")
        waitFor("Cancel")
        typeName("Ida")
        tapInDialog("Cancel")
        waitUntilGone("Cancel")
        assertShows("Baby")
        assertGone("Babies")
        assertEquals(1, nameFields())
        assertGone("Ida")
    }

    @Test
    fun `an unnamed second baby`() {
        freshInstall()
        openSettings()
        addBaby()
        waitFor("Remove Baby 2")
        assertShows("Remove Baby 1")
        goBack()
        waitFor("Baby 1. Switch baby")
    }

    @Test
    fun `at most eight babies`() {
        freshInstall()
        openSettings()
        repeat(6) { addBaby() }
        waitFor("Remove Baby 7")
        assertEquals(7, nameFields())
        addBaby()
        waitFor("Remove Baby 8")
        assertGone("Add a baby")
        assertEquals(8, nameFields())
    }
}
