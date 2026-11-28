// ail-derived-from: WatchBottle@f1524795cba41ff7
package com.ffmathy.pooparazzi.checks

import org.junit.After
import org.junit.Rule
import org.junit.rules.TestName

/**
 * Derived check for WatchBottle. It plays every scenario of the end-to-end
 * check (checks/e2e/watch/feed/bottle/WatchBottleChecks.kt) against the real
 * watch app. After each scenario it pictures the screen the scenario ends on,
 * named after the scenario's title.
 */
class WatchBottleShots : WatchBottleChecks() {

    @get:Rule
    val testName = TestName()

    @After
    fun pictureTheScenario() {
        val name = testName.methodName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        pictureTheScreen(name)
    }
}
