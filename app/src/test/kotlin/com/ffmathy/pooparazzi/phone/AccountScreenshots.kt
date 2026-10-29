package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.phone.ui.account.AccountActions
import com.ffmathy.pooparazzi.phone.ui.account.AccountScreen
import com.ffmathy.pooparazzi.phone.ui.account.WelcomeScreen
import com.ffmathy.pooparazzi.phone.ui.preview.AccountSamples
import com.ffmathy.pooparazzi.phone.ui.preview.SettingsSamples
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsActions
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsScreen
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
 * English and Danish: `app/screenshots/<lang>/27-…29-*.png`, `33-*.png` and `37-…38-*.png`, continuing the tour.
 */
abstract class AccountScreenshots(private val language: String) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        // "Backed up · 14:31", as in the reference scenario; Danish always uses 24 h, English follows this setting.
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test fun welcome() = screen("27-welcome", night = false) {
        WelcomeScreen(AccountSamples.signedOut(), AccountActions.None, windowInsets = ReferenceInsets)
    }

    @Test fun accountSignedOut() = screen("28-account-signed-out", night = false) {
        AccountScreen(AccountSamples.signedOut(), AccountActions.None, windowInsets = ReferenceInsets)
    }

    @Test fun accountSignedIn() = screen("29-account-signed-in", night = true) {
        AccountScreen(AccountSamples.signedIn(), AccountActions.None, windowInsets = ReferenceInsets)
    }

    /** The backup was deleted but the account was not (its deletion failed): nothing is backed up until "Back up now". */
    @Test fun accountBackupDeleted() = screen("33-account-backup-deleted", night = true) {
        AccountScreen(AccountSamples.signedIn(cloud = CloudStatus.Deleted), AccountActions.None, windowInsets = ReferenceInsets)
    }

    /** Signed in before the backup asked for consent (§3.4): paused, with the consent and "Agree and back up". */
    @Test fun accountPaused() = screen("37-account-paused", night = false) {
        AccountScreen(AccountSamples.signedInPaused(), AccountActions.None, windowInsets = ReferenceInsets)
    }

    /** Settings › Privacy (§3.4): the policy and "Download my data", scrolled into view. */
    @Test fun settingsPrivacy() = screen("38-settings-privacy", night = true, scrollTo = R.string.settings_export) {
        SettingsScreen(SettingsSamples.state(), SettingsActions.None, windowInsets = ReferenceInsets)
    }

    private fun screen(name: String, night: Boolean, @StringRes scrollTo: Int? = null, content: @Composable () -> Unit) {
        compose.setContent { NightLightTheme(night = night, content = content) }
        if (scrollTo != null) {
            val text = ApplicationProvider.getApplicationContext<Application>().getString(scrollTo)
            compose.onNodeWithText(text).performScrollTo()
        }
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
