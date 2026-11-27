package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for WatchBottle: contents, the amount with − / + and its chips, on the real watch app (US English). */
class WatchBottleChecks : Watch() {
    /** Feed, Bottle: Bottle, once it takes taps. Returns the time of the Feed tap. */
    private fun openBottle(): String {
        tapStarting("Feed. ")
        val stamp = shownTime("Feed · ")
        pass(600)
        tap("Bottle")
        waitFor("Bottle · $stamp")
        pass(600)
        return stamp
    }

    /** Whether the option or chip that reads (or is spoken as) [text] is chosen. */
    private fun chosen(text: String): Boolean {
        val selected = SemanticsMatcher("chosen") { it.config.getOrNull(SemanticsProperties.Selected) == true }
        return compose.onAllNodes((hasText(text) or hasContentDescription(text)) and selected).fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertChosen(text: String) = assertTrue("expected \"$text\" chosen:\n${screen()}", chosen(text))

    private fun assertNotChosen(text: String) = assertTrue("expected \"$text\" not chosen:\n${screen()}", shows(text) && !chosen(text))

    @Test
    fun `the bottle screen`() {
        freshInstall()
        openBottle()
        assertNotChosen("Milk")
        assertNotChosen("Formula")
        scrollTo("3 fl oz")
        assertShows("No amount")
        assertShows("Amount, No amount")
        assertCannotTap("0.5 fl oz less")
        assertCanTap("0.5 fl oz more")
        for (chip in listOf("2 fl oz", "3 fl oz", "4 fl oz")) assertNotChosen(chip)
        assertShows("Save")
    }

    @Test
    fun `a second tap within half a second keeps a choice`() {
        freshInstall()
        openBottle()
        tap("Milk")
        tap("Milk")
        assertChosen("Milk")
        pass(600)
        tap("Milk")
        waitUntil("Milk cleared") { !chosen("Milk") }
        assertNotChosen("Formula")
    }

    @Test
    fun `stepping the amount`() {
        freshInstall()
        openBottle()
        scrollTo("0.5 fl oz more")
        tap("0.5 fl oz more")
        assertShows("0.5 fl oz")
        assertShows("Amount, 0.5 fl oz")
        assertCanTap("0.5 fl oz less")
        repeat(19) { tap("0.5 fl oz more") }
        waitFor("Amount, 10 fl oz")
        assertCannotTap("0.5 fl oz more")
    }

    @Test
    fun `an amount chip sets the amount`() {
        freshInstall()
        openBottle()
        scrollTo("3 fl oz")
        tap("3 fl oz")
        assertChosen("3 fl oz")
        assertShows("Amount, 3 fl oz")
        tap("0.5 fl oz more")
        assertShows("Amount, 3.5 fl oz")
        for (chip in listOf("2 fl oz", "3 fl oz", "4 fl oz")) assertNotChosen(chip)
    }

    @Test
    fun `saving a bottle`() {
        freshInstall()
        val stamp = openBottle()
        tap("Formula")
        scrollTo("4 fl oz")
        tap("4 fl oz")
        tap("0.5 fl oz more")
        assertShows("Amount, 4.5 fl oz")
        tap("Save")
        waitFor("Bottle at $stamp")
        assertShows("Formula · 4.5 fl oz")
        closeLogged()
        val next = openBottle()
        tap("Save")
        waitFor("Bottle at $next")
        assertGonePart(" · ")
    }

    @Test
    fun `the chips follow the last bottle`() {
        freshInstall()
        openBottle()
        scrollTo("4 fl oz")
        tap("4 fl oz")
        tap("0.5 fl oz more")
        tap("Save")
        shownTime("Bottle at ")
        closeLogged()
        openBottle()
        scrollTo("4.5 fl oz, as last time")
        assertShows("3.5 fl oz")
        assertShows("5.5 fl oz")
        for (chip in listOf("3.5 fl oz", "4.5 fl oz, as last time", "5.5 fl oz")) assertNotChosen(chip)
        assertShows("Amount, No amount")
    }
}
