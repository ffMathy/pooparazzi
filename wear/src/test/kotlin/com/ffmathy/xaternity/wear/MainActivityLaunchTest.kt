package com.ffmathy.xaternity.wear

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ffmathy.xaternity.wear.theme.AmbientSupport
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A cold start of the real activity, as the launcher, the tile, complications and the Ongoing Activity do it.
 *
 * Robolectric has neither the Wear SDK (`wear-sdk`, Wear OS 6+) nor the wearable shared library, like a Wear OS 3
 * watch without the Wear SDK: the app must still start, without always-on support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33, 34, 36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class MainActivityLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun startsWithoutTheWearSdk() {
        assertEquals(AmbientSupport.NONE, AmbientSupport.detect())
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Start sleep")).fetchSemanticsNodes().isNotEmpty() }
    }
}
