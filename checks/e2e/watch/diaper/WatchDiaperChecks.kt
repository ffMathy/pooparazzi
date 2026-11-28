// ail-derived-from: WatchDiaper@3652073d703b260f
package com.ffmathy.pooparazzi.checks

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for WatchDiaper: Pee and Poop, colour and texture and the cooldowns, on the real watch app. */
open class WatchDiaperChecks : Watch() {
    private val colours = listOf("Yellow", "Green", "Brown", "Black", "Red", "Pale")
    private val textures = listOf("Watery", "Seedy", "Mushy", "Formed", "Hard")

    /** Diaper from Main, once it takes taps. Returns the time of the Diaper tap. */
    private fun openDiaperScreen(): String {
        tapStarting("Diaper. ")
        val stamp = shownTime("Diaper · ")
        pass(600)
        return stamp
    }

    private fun ticked(text: String): Boolean = compose.onAllNodes(hasText(text) and SemanticsMatcher("ticked") {
        it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
    }).fetchSemanticsNodes().isNotEmpty()

    private fun chosen(text: String): Boolean = compose.onAllNodes(hasText(text) and SemanticsMatcher("chosen") {
        it.config.getOrNull(SemanticsProperties.Selected) == true
    }).fetchSemanticsNodes().isNotEmpty()

    private fun assertTicked(text: String, on: Boolean) =
        assertTrue("expected \"$text\" ${if (on) "ticked" else "unticked"}:\n${screen()}", shows(text) && ticked(text) == on)

    private fun assertChosen(text: String, on: Boolean) =
        assertTrue("expected \"$text\" ${if (on) "chosen" else "not chosen"}:\n${screen()}", shows(text) && chosen(text) == on)

    @Test
    fun `the diaper screen`() {
        freshInstall()
        tapStarting("Diaper. ")
        shownTime("Diaper · ")
        assertTicked("Pee", false)
        assertTicked("Poop", false)
        assertCannotTap("Pee")
        assertGone("Colour")
        assertGone("Texture")
        assertShows("Save")
        pass(600)
        assertCanTap("Pee")
        assertCanTap("Save")
    }

    @Test
    fun `poop shows its colour and texture`() {
        freshInstall()
        openDiaperScreen()
        tap("Poop")
        waitFor("Colour")
        assertCannotTap("Yellow")
        pass(600)
        assertCanTap("Yellow")
        for (colour in colours) assertChosen(colour, false)
        scrollTo("Texture")
        for (texture in textures) {
            scrollTo(texture)
            assertChosen(texture, false)
        }
    }

    @Test
    fun `logging pee and poop with a colour and texture`() {
        freshInstall()
        val stamp = openDiaperScreen()
        tap("Pee")
        tap("Poop")
        pass(600)
        scrollTo("Yellow")
        tap("Yellow")
        assertChosen("Yellow", true)
        scrollTo("Seedy")
        tap("Seedy")
        assertChosen("Seedy", true)
        tap("Save")
        waitFor("Pee + poop at $stamp")
        assertShows("Pee and poop at $stamp")
        assertGone("Yellow")
        assertGone("Seedy")
    }

    @Test
    fun `unticking poop keeps its choices hidden`() {
        freshInstall()
        val stamp = openDiaperScreen()
        tap("Poop")
        pass(600)
        scrollTo("Yellow")
        tap("Yellow")
        scrollBackTo("Poop")
        tap("Poop")
        waitUntilGone("Colour")
        assertGone("Yellow")
        tap("Poop")
        waitFor("Colour")
        assertChosen("Yellow", true)
        pass(600)
        tap("Poop")
        waitUntilGone("Colour")
        tap("Save")
        waitFor("Diaper at $stamp")
    }

    @Test
    fun `a plain change`() {
        freshInstall()
        val stamp = openDiaperScreen()
        tap("Save")
        waitFor("Diaper at $stamp")
        closeLogged()
        waitForSince(stamp, "the Diaper row") { listOf("Diaper. Last diaper, diaper, ${spokenSince(it)}, at $stamp") }
    }

    @Test
    fun `pee alone`() {
        freshInstall()
        val stamp = openDiaperScreen()
        tap("Pee")
        assertTicked("Pee", true)
        tap("Save")
        waitFor("Pee at $stamp")
    }
}
