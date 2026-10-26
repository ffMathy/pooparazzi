package com.ffmathy.xaternity

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
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class SpikeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun phone() {
        compose.setContent { SpikeScreen() }
        compose.onRoot().captureRoboImage("build/spike/phone.png")
    }
}
