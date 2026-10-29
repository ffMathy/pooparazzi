package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.ui.account.AccountActions
import com.ffmathy.pooparazzi.phone.ui.account.AccountScreen
import com.ffmathy.pooparazzi.phone.ui.account.WelcomeScreen
import com.ffmathy.pooparazzi.phone.ui.preview.AccountSamples
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first-run Welcome and Settings › Account (BABIES_AND_BACKUP §3) on the reference phone (390 × 844 dp, xxhdpi), in
 * English and Danish: `app/screenshots/<lang>/24-…26-*.png`, continuing the tour.
 */
abstract class AccountScreenshots(private val language: String) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        // "Backed up · 14:31", as in the reference scenario; Danish always uses 24 h, English follows this setting.
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test fun welcome() = screen("24-welcome", night = false) {
        WelcomeScreen(AccountSamples.signedOut(), AccountActions.None, windowInsets = ReferenceInsets)
    }

    @Test fun accountSignedOut() = screen("25-account-signed-out", night = false) {
        AccountScreen(AccountSamples.signedOut(), AccountActions.None, windowInsets = ReferenceInsets)
    }

    @Test fun accountSignedIn() = screen("26-account-signed-in", night = true) {
        AccountScreen(AccountSamples.signedIn(), AccountActions.None, windowInsets = ReferenceInsets)
    }

    private fun screen(name: String, night: Boolean, content: @Composable () -> Unit) {
        compose.setContent { NightLightTheme(night = night, content = content) }
        compose.onRoot().captureRoboImage("screenshots/$language/$name.png")
    }

    private companion object {
        /** The reference device's 24 dp status and navigation bars (DESIGN_SPEC §3.3.1). */
        val ReferenceInsets = WindowInsets(top = 24.dp, bottom = 24.dp)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class AccountScreenshotsEnglish : AccountScreenshots("en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "da-w390dp-h844dp-xxhdpi", application = Application::class)
class AccountScreenshotsDanish : AccountScreenshots("da")
