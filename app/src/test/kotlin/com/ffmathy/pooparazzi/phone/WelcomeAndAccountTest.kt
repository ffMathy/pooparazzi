package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.MemoryConsent
import com.ffmathy.pooparazzi.phone.account.WelcomePrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real app with a fake account backend (BABIES_AND_BACKUP §3.1–3.3): the Welcome comes first on a phone that has
 * not answered it, "Continue without an account" or signing in (Google, or an email link) leads to Home, and Settings ›
 * Account opens the Account screen with Google first. No passwords anywhere.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class WelcomeAndAccountTest {
    private val accounts = FakeAccounts()

    /** Before the activity starts: the app has set up its (absent) Firebase accounts by then; replace them. */
    @get:Rule(order = 0)
    val fakeAccounts = object : ExternalResource() {
        // The address the Account screen test signs in with agreed earlier; the Welcome's Google account has not.
        override fun before() = AppAccounts.install(accounts, consent = MemoryConsent(setOf(accounts.nextUser.uid)))
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        Core.reset()
        AppAccounts.reset()
    }

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun continuingWithoutAnAccountOpensHomeAndIsRemembered() {
        compose.waitUntilAtLeastOneExists(hasText("Your log stays on this phone"), timeoutMillis = 10_000)
        compose.onNodeWithText("Continue with Google").assertExists()
        compose.onNodeWithText("or use email").assertExists()
        compose.onNodeWithText("Continue without an account").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        assertTrue(WelcomePrefs(app).seen)
        assertTrue("nothing was asked of the backend", accounts.calls.isEmpty())
    }

    @Test
    fun backCountsAsContinuing() {
        compose.waitUntilAtLeastOneExists(hasText("Your log stays on this phone"), timeoutMillis = 10_000)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        assertTrue(WelcomePrefs(app).seen)
    }

    @Test
    fun signingInOnTheWelcomeAsksForTheConsentThenOpensHome() {
        compose.waitUntilAtLeastOneExists(hasText("Your log stays on this phone"), timeoutMillis = 10_000)
        compose.onNodeWithText("Continue with Google").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Back up to Google Firebase?"), timeoutMillis = 5_000)
        compose.onNodeWithText("What is uploaded").assertExists()
        compose.onNodeWithText("Details are in the privacy policy.").performScrollTo().assertExists()
        compose.onNodeWithText("I agree").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        assertTrue(accounts.googleUser.uid in AppAccounts.consent.agreed.value)
        assertTrue(accounts.calls == listOf("google"))
        assertTrue(WelcomePrefs(app).seen)
    }

    @Test
    fun theWelcomeOffersAnEmailLinkAndThePrivacyPolicy() {
        compose.waitUntilAtLeastOneExists(hasText("Your log stays on this phone"), timeoutMillis = 10_000)
        compose.onNode(hasText("Email") and hasSetTextAction()).performTextInput("parent@example.com")
        compose.onNodeWithText("Email me a sign-in link").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Check your email"), timeoutMillis = 5_000)
        assertTrue(accounts.calls == listOf("sendLink:parent@example.com"))
        compose.onNodeWithText("By continuing you agree to the privacy policy.").performScrollTo().assertExists()
        compose.onNode(hasText("Password") and hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun settingsOpensTheAccountScreen() {
        compose.waitUntilAtLeastOneExists(hasText("Continue without an account"), timeoutMillis = 10_000)
        compose.onNodeWithText("Continue without an account").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Not signed in"), timeoutMillis = 5_000)
        compose.onNodeWithText("Your log is only on this phone. Sign in to back it up.").assertExists()
        compose.onNodeWithText("Not signed in").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Continue with Google"), timeoutMillis = 5_000)
        compose.onNodeWithText("Email me a sign-in link").assertExists()
        compose.onNodeWithText("Forgot password?").assertDoesNotExist()

        // Signed in elsewhere (here: by the fake), the screen shows the account.
        accounts.signInAs(accounts.nextUser)
        compose.waitUntilAtLeastOneExists(hasText("parent@example.com"), timeoutMillis = 5_000)
        compose.onNodeWithText("Back up now").assertExists()
        compose.onNodeWithText("Delete account…").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Delete account?"), timeoutMillis = 5_000)
        compose.onNodeWithText("We'll email you a link to confirm. Open it on this phone.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Delete account?")).fetchSemanticsNodes().isEmpty() }
    }
}
