package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.cloud.NoopCloudSync
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.FirebaseConfig
import com.ffmathy.pooparazzi.phone.account.NoAccounts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Accounts off, as in a build without a Firebase configuration (CI) and in every unit test, even on a machine that has
 * one: no Firebase code runs, the Welcome never appears and Settings says the backup isn't set up
 * (BABIES_AND_BACKUP §3.1–3.2, docs/BACKEND.md).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class AccountsOffTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        Core.reset()
        AppAccounts.reset()
    }

    @Test
    fun theConfigurationIsReadByNameAndTheTestsNeverStartFirebase() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The Google services plugin runs only with the file (the tests run in app/).
        val configured = File("google-services.json").exists()
        assertEquals(configured, FirebaseConfig.isPresent(app))
        if (!configured) assertNull("no Google button without a configuration", FirebaseConfig.webClientId(app))
        assertSame(NoAccounts, AppAccounts.accounts)
        assertNull("no backend for the core", AppAccounts.init(app))
        assertSame(NoopCloudSync, Core.cloud)
    }

    @Test
    fun noWelcomeAndSettingsSaysTheBackupIsNotSetUp() {
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 10_000)
        compose.onNodeWithText("No account needed").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntilAtLeastOneExists(
            hasText("Backup isn't set up in this build. Everything is saved on this phone."),
            timeoutMillis = 5_000,
        )
        // Not a way into the Account screen.
        compose.onNodeWithText("Backup isn't set up in this build. Everything is saved on this phone.").performClick()
        compose.onNodeWithText("Not signed in").assertDoesNotExist()
        compose.onNodeWithText("Font licences").assertExists()
    }
}
