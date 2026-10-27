package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.phone.ui.theme.NightRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** DESIGN_SPEC §8.1: Auto is Night with the system dark theme, in the night window, or while a sleep runs. */
class NightRuleTest {
    private val noon = LocalTime.of(12, 0)
    private val evening = LocalTime.of(19, 0)
    private val small = LocalTime.of(3, 12)
    private val morning = LocalTime.of(7, 0)

    @Test
    fun fixedChoicesIgnoreEverythingElse() {
        assertTrue(NightRule.isNight(Appearance.NIGHT, systemDark = false, localTime = noon, sleepRunning = false))
        assertFalse(NightRule.isNight(Appearance.DAY, systemDark = true, localTime = small, sleepRunning = true))
    }

    @Test
    fun autoFollowsTheSystemDarkTheme() {
        assertTrue(NightRule.isNight(Appearance.AUTO, systemDark = true, localTime = noon, sleepRunning = false))
        assertFalse(NightRule.isNight(Appearance.AUTO, systemDark = false, localTime = noon, sleepRunning = false))
    }

    @Test
    fun autoIsNightInsideTheWindowThatWrapsMidnight() {
        assertTrue(NightRule.isNight(Appearance.AUTO, systemDark = false, localTime = evening, sleepRunning = false))
        assertTrue(NightRule.isNight(Appearance.AUTO, systemDark = false, localTime = small, sleepRunning = false))
        assertFalse("the window ends at 07:00", NightRule.isNight(Appearance.AUTO, systemDark = false, localTime = morning, sleepRunning = false))
        assertFalse(NightRule.isNight(Appearance.AUTO, systemDark = false, localTime = LocalTime.of(18, 59), sleepRunning = false))
    }

    @Test
    fun autoDimsForNapsUnlessTurnedOff() {
        assertTrue(NightRule.isNight(Appearance.AUTO, systemDark = false, localTime = noon, sleepRunning = true))
        assertFalse(NightRule.isNight(Appearance.AUTO, systemDark = false, localTime = noon, sleepRunning = true, dimForNaps = false))
    }

    @Test
    fun aWindowThatDoesNotWrap() {
        val start = LocalTime.of(1, 0)
        val end = LocalTime.of(5, 0)
        assertTrue(NightRule.inWindow(LocalTime.of(3, 0), start, end))
        assertFalse(NightRule.inWindow(LocalTime.of(5, 0), start, end))
        assertFalse(NightRule.inWindow(LocalTime.of(0, 59), start, end))
        assertFalse("an empty window", NightRule.inWindow(noon, noon, noon))
    }
}
