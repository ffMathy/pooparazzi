// ail-derived-from: WatchDiaper@6c54b6b06ceaa9ad
package com.ffmathy.pooparazzi.checks

import org.junit.After
import org.junit.Rule
import org.junit.rules.TestName

/**
 * WatchDiaper's derived check: every scenario is played against the real watch app by
 * WatchDiaperChecks (checks/e2e/watch/diaper/WatchDiaperChecks.kt), one test per scenario,
 * named exactly as the scenario:
 *  - `the diaper screen`
 *  - `poop shows its colour and texture`
 *  - `logging pee and poop with a colour and texture`
 *  - `unticking poop keeps its choices hidden`
 *  - `a plain change`
 *  - `pee alone`
 * This class adds only the picture of the screen each scenario ends on
 * (saved into AIL_SHOTS_DIR when it is set, nothing otherwise).
 */
class WatchDiaperShots : WatchDiaperChecks() {

    @get:Rule
    val testName = TestName()

    @After
    fun pictureTheEnd() {
        val name = testName.methodName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        pictureTheScreen(name)
    }
}
