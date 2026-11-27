// ail-derived-from: AddBaby@38f78232edc2652a
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
        openSettingsFromHome()
        nameFirstBaby("Noa")
        tapInTopWindow("Add a baby")
        waitForInTopWindow("Cancel")
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
        openSettingsFromHome()
        noaAndIdaInSettings()
        waitForInTopWindow("Babies")
        assertShows("Remove Noa")
        assertShows("Remove Ida")
        assertShows("Noa")
        assertShows("Ida")
        goBack()
        waitForInTopWindow("Noa. Switch baby")
    }

    @Test
    fun `adding a baby with a birth date`() {
        freshInstall()
        openSettingsFromHome()
        tapInTopWindow("Add a baby")
        waitForInTopWindow("Cancel")
        tapInTopWindow("Add birth date")
        waitForInTopWindow("Birth date")
        pickTodayAsBirthDate()
        waitForInTopWindow(birthDatePillToday())
        tapInTopWindow("Add")
        waitUntilGoneInTopWindow("Cancel")
        waitForInTopWindow("Babies")
        assertShows(birthDatePillToday())
    }

    @Test
    fun `cancelling adds nothing`() {
        freshInstall()
        openSettingsFromHome()
        tapInTopWindow("Add a baby")
        waitForInTopWindow("Cancel")
        typeBabyName("Ida")
        tapInTopWindow("Cancel")
        waitUntilGoneInTopWindow("Cancel")
        assertShows("Baby")
        assertGone("Babies")
        assertEquals(1, nameFields())
        assertGone("Ida")
    }

    @Test
    fun `an unnamed second baby`() {
        freshInstall()
        openSettingsFromHome()
        addBabyInSettings()
        waitForInTopWindow("Remove Baby 2")
        assertShows("Remove Baby 1")
        goBack()
        waitForInTopWindow("Baby 1. Switch baby")
    }

    @Test
    fun `at most eight babies`() {
        freshInstall()
        openSettingsFromHome()
        repeat(6) { addBabyInSettings() }
        waitForInTopWindow("Remove Baby 7")
        assertEquals(7, nameFields())
        addBabyInSettings()
        waitForInTopWindow("Remove Baby 8")
        assertGone("Add a baby")
        assertEquals(8, nameFields())
    }
}
