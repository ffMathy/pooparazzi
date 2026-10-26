package com.ffmathy.xaternity.wear

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class WearSpikeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun watch() {
        compose.setContent { WearSpike() }
        compose.onRoot().captureRoboImage("build/spike/watch.png")
    }
}
