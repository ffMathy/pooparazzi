// ail-derived-from: WatchSettings@d6e0e1797e59ea49
package com.ffmathy.pooparazzi.checks

import org.junit.After
import org.junit.Rule
import org.junit.rules.TestName

/**
 * Derived check for WatchSettings: it plays the node's scenarios exactly as its end-to-end check does (checks/e2e/settings/watch-settings/WatchSettingsChecks.kt, which
 * passes against the app) and, with AIL_SHOTS_DIR set, saves the screen each scenario ends on, under the name ail gives
 * it, for a model to judge against the node's look and copy.
 */
class WatchSettingsShots : WatchSettingsChecks() {
    @get:Rule
    val scenario = TestName()

    /** The screen the scenario ended on, before the app closes: its title in lower case, other characters as one "-". */
    @After
    fun pictureTheLastScreen() {
        val name = scenario.methodName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).ifEmpty { "test" }
        saveScreen(name)
    }
}
