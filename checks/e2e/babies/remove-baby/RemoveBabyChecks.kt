// ail-derived-from: RemoveBaby@12be13386295a5f5
package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.test.hasSetTextAction
import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end check for RemoveBaby: the remove button and its confirmation, on the real phone app. */
class RemoveBabyChecks : Phone() {
    private fun askToRemoveIda() {
        freshInstall()
        openSettingsFromHome()
        noaAndIdaInSettings()
        tapInTopWindow("Remove Ida")
        waitForInTopWindow("Remove Ida?")
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
        tapInTopWindow("Cancel")
        waitUntilGoneInTopWindow("Remove Ida?")
        assertShows("Babies")
        assertShows("Remove Noa")
        assertShows("Remove Ida")
    }

    @Test
    fun `removing a baby`() {
        askToRemoveIda()
        tapInTopWindow("Remove")
        waitUntilGoneInTopWindow("Remove Ida?")
        waitUntilGoneInTopWindow("Babies")
        assertShows("Baby")
        assertGone("Remove Noa")
        assertEquals(1, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        goBack()
        waitForInTopWindow("Start sleep")
        waitForInTopWindow("Noa")
        assertGone("Noa. Switch baby")
    }

    @Test
    fun `removing the current baby`() {
        freshInstall()
        openSettingsFromHome()
        noaAndIdaInSettings()
        goBack()
        waitForInTopWindow("Noa. Switch baby")
        switchBabyTo("Ida")
        openSettingsFromHome()
        tapInTopWindow("Remove Ida")
        waitForInTopWindow("Remove Ida?")
        tapInTopWindow("Remove")
        waitUntilGoneInTopWindow("Remove Ida?")
        waitUntilGoneInTopWindow("Babies")
        goBack()
        waitForInTopWindow("Start sleep")
        waitForInTopWindow("Noa")
        assertGone("Ida. Switch baby")
        assertGone("Noa. Switch baby")
    }
}
