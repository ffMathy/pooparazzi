package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.test.hasSetTextAction
import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for RemoveBaby: the remove button and its confirmation, on the real phone app. */
class RemoveBabyChecks : Phone() {
    private fun askToRemoveIda() {
        freshInstall()
        openSettings()
        noaAndIdaInSettings()
        tapInDialog("Remove Ida")
        waitFor("Remove Ida?")
    }

    @Test
    fun `removing asks first`() {
        askToRemoveIda()
        assertShows("Their log is deleted from this phone and your watch. This can't be undone.")
        assertCanTap("Cancel")
        assertCanTap("Remove")
    }

    @Test
    fun `cancelling keeps the baby`() {
        askToRemoveIda()
        tapInDialog("Cancel")
        waitUntilGone("Remove Ida?")
        assertShows("Babies")
        assertShows("Remove Noa")
        assertShows("Remove Ida")
    }

    @Test
    fun `removing a baby`() {
        askToRemoveIda()
        tapInDialog("Remove")
        waitUntilGone("Remove Ida?")
        waitUntilGone("Babies")
        assertShows("Baby")
        assertGone("Remove Noa")
        assertEquals(1, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        goBack()
        waitFor("Start sleep")
        waitFor("Noa")
        assertGone("Noa. Switch baby")
    }

    @Test
    fun `removing the current baby`() {
        freshInstall()
        openSettings()
        noaAndIdaInSettings()
        goBack()
        waitFor("Noa. Switch baby")
        switchTo("Ida")
        openSettings()
        tapInDialog("Remove Ida")
        waitFor("Remove Ida?")
        tapInDialog("Remove")
        waitUntilGone("Remove Ida?")
        waitUntilGone("Babies")
        goBack()
        waitFor("Start sleep")
        waitFor("Noa")
        assertGone("Ida. Switch baby")
        assertGone("Noa. Switch baby")
    }
}
