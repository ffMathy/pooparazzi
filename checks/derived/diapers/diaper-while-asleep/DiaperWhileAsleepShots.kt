// ail-derived-from: DiaperWhileAsleep@bc28c83959248426
package com.ffmathy.pooparazzi.checks

import org.junit.After
import org.junit.Rule
import org.junit.rules.TestName

/**
 * Pictures the screen each DiaperWhileAsleep scenario ends on.
 *
 * The scenarios themselves are played by [DiaperWhileAsleepChecks]:
 * - `the Diaper sheet says the sleep will end`
 * - `saving the diaper ends the sleep`
 * - `a diaper before the sleep began leaves it running`
 *
 * After each one, the screen the parent is looking at is saved into
 * AIL_SHOTS_DIR (nothing is saved when it is unset), named after the
 * scenario's title in lower case, each run of other characters one "-".
 */
class DiaperWhileAsleepShots : DiaperWhileAsleepChecks() {

    @get:Rule
    val shotName = TestName()

    @After
    fun pictureTheEnd() {
        pictureTheScreen(shotFileName(shotName.methodName))
    }

    private fun shotFileName(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}
