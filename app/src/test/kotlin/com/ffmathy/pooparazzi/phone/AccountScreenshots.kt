package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.phone.ui.account.AccountActions
import com.ffmathy.pooparazzi.phone.ui.account.AccountScreen
import com.ffmathy.pooparazzi.phone.ui.account.ConsentActions
import com.ffmathy.pooparazzi.phone.ui.account.ConsentScreen
import com.ffmathy.pooparazzi.phone.ui.account.WelcomeScreen
import com.ffmathy.pooparazzi.phone.ui.family.FamilyConfirm
import com.ffmathy.pooparazzi.phone.ui.family.JoinActions
import com.ffmathy.pooparazzi.phone.ui.family.JoinScreen
import com.ffmathy.pooparazzi.phone.ui.preview.AccountSamples
import com.ffmathy.pooparazzi.phone.ui.preview.FamilySamples
import com.ffmathy.pooparazzi.phone.ui.preview.SettingsSamples
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsActions
import com.ffmathy.pooparazzi.phone.ui.settings.SettingsScreen
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first-run Welcome, Settings › Account, Settings › Family and the join confirmation (BABIES_AND_BACKUP §3) on the
 * reference phone (390 × 844 dp, xxhdpi), in English and Danish: `app/screenshots/<lang>/27-…29-*.png`, `33-*.png` and
 * `37-…42-*.png`, continuing the tour (with the consent before the first upload, removing a member, and a deletion
 * link that asks again).
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

    /** "Check your email": the sign-in link is on its way (§3.3). */
    @Test fun accountLinkSent() = screen("39-account-link-sent", night = false) {
        AccountScreen(AccountSamples.linkSent(), AccountActions.None, windowInsets = ReferenceInsets)
    }

    /** Settings › Family (§3.4): the parent started the family; a partner and a grandparent joined. */
    @Test fun settingsFamily() = screen("37-settings-family", night = false) {
        SettingsScreen(SettingsSamples.state(), SettingsActions.None, windowInsets = ReferenceInsets, family = FamilySamples.state())
    }

    /** The join confirmation of an invite link (§3.5). */
    @Test fun joinFamily() = screen("38-join-family", night = true) {
        JoinScreen(FamilySamples.join, JoinActions.None, windowInsets = ReferenceInsets)
    }

    /** The explicit consent before the first upload (§3.6), right after signing in. */
    @Test fun consent() = screen("40-consent", night = false) {
        ConsentScreen(AccountSamples.consent(), ConsentActions.None, windowInsets = ReferenceInsets)
    }

    /** "Remove partner@example.com?" in Settings › Family: what the removed member keeps (§3.4). */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun familyRemove() {
        val state = FamilySamples.state().copy(confirm = FamilyConfirm.Remove(FamilySamples.family.members[1]))
        compose.setContent {
            NightLightTheme(night = false) {
                SettingsScreen(SettingsSamples.state(), SettingsActions.None, windowInsets = ReferenceInsets, family = state)
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$language/41-family-remove.png")
    }

    /** A deletion link that opened the app without the confirmation: it asks again before deleting (§3.3). */
    @Test fun accountDeleteLink() = screen("42-account-delete-link", night = true) {
        AccountScreen(AccountSamples.deleteLinkOpened(), AccountActions.None, windowInsets = ReferenceInsets)
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
