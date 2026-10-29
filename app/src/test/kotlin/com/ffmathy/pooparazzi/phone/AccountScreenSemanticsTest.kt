package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.phone.account.AccountError
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.ui.account.AccountActions
import com.ffmathy.pooparazzi.phone.ui.account.AccountScreen
import com.ffmathy.pooparazzi.phone.ui.account.DeleteState
import com.ffmathy.pooparazzi.phone.ui.account.DeleteProblem
import com.ffmathy.pooparazzi.phone.ui.preview.AccountSamples
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Account screen's layout and words (BABIES_AND_BACKUP §3.2–3.3): Google and email links, no passwords. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class AccountScreenSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun googleComesFirstThenEmail() {
        var google = 0
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(AccountSamples.signedOut(), object : AccountActions { override fun onGoogle() { google++ } })
            }
        }
        val button = compose.onNodeWithText("Continue with Google").assertHasClickAction().getBoundsInRoot()
        val divider = compose.onNodeWithText("or use email").getBoundsInRoot()
        val email = compose.onNode(hasText("Email") and hasSetTextAction()).getBoundsInRoot()
        assertTrue(button.bottom <= divider.top)
        assertTrue(divider.bottom <= email.top)
        compose.onNodeWithText("Continue with Google").performClick()
        assertEquals(1, google)
    }

    @Test
    fun withoutAWebClientIdThereIsNoGoogleButton() {
        compose.setContent {
            NightLightTheme(night = false) { AccountScreen(AccountSamples.signedOut(googleAvailable = false), AccountActions.None) }
        }
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        compose.onNodeWithText("or use email").assertDoesNotExist()
        compose.onNode(hasText("Email") and hasSetTextAction()).assertExists()
    }

    @Test
    fun thereAreNoPasswordsOnlyAnEmailLink() {
        var sent = 0
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(AccountSamples.signedOut(), object : AccountActions { override fun onSendLink() { sent++ } })
            }
        }
        compose.onNodeWithText("Email me a sign-in link").performScrollTo().performClick()
        assertEquals(1, sent)
        compose.onNode(hasText("Password") and hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Forgot password?").assertDoesNotExist()
        compose.onNodeWithText("Create account").assertDoesNotExist()
    }

    @Test
    fun errorsAreInWords() {
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(AccountSamples.signedOut().copy(error = AccountError.EMAIL_QUOTA), AccountActions.None)
            }
        }
        compose.onNodeWithText("Too many sign-in emails today. Try again tomorrow, or use Google.").assertExists()
    }

    @Test
    fun aSentLinkSaysWhereItWentWithSendAgainAndAnotherEmail() {
        val calls = mutableListOf<String>()
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(
                    AccountSamples.linkSent(),
                    object : AccountActions {
                        override fun onSendLink() { calls += "again" }
                        override fun onUseAnotherEmail() { calls += "another" }
                    },
                )
            }
        }
        compose.onNodeWithText("Check your email").assertExists()
        compose.onNodeWithText("We sent a sign-in link to parent@example.com. Tap the link in it on this phone.").assertExists()
        compose.onNodeWithText("Send again").performScrollTo().performClick()
        compose.onNodeWithText("Use another email").performScrollTo().performClick()
        assertEquals(listOf("again", "another"), calls)
    }

    @Test
    fun aLinkFromAnotherPhoneAsksForTheAddress() {
        var finished = 0
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(
                    AccountSamples.signedOut().copy(linkNeedsEmail = true),
                    object : AccountActions { override fun onFinishSignIn() { finished++ } },
                )
            }
        }
        compose.onNodeWithText("To finish signing in, enter the email address the link was sent to.").assertExists()
        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        assertEquals(1, finished)
    }

    @Test
    fun signingInAgreesToThePrivacyPolicyWhichIsALink() {
        var opened = 0
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(AccountSamples.signedOut(), object : AccountActions { override fun onOpenPrivacy() { opened++ } })
            }
        }
        compose.onNodeWithText("By continuing you agree to the privacy policy.").performScrollTo().performFirstLinkClick()
        assertEquals(1, opened)
    }

    @Test
    fun anInviteWaitingSaysToSignInToJoin() {
        compose.setContent {
            NightLightTheme(night = false) { AccountScreen(AccountSamples.signedOut().copy(joining = true), AccountActions.None) }
        }
        compose.onNodeWithText("Sign in to join the family.").assertExists()
    }

    @Test
    fun signedInShowsTheAccountAndTheBackup() {
        var retries = 0
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(AccountSamples.signedIn(CloudStatus.Error), object : AccountActions { override fun onBackUpNow() { retries++ } })
            }
        }
        compose.onNodeWithText("parent@example.com", substring = true).assertExists()
        compose.onNodeWithText("Couldn't back up · Tap to retry", substring = true).performClick()
        compose.onNodeWithText("Back up now").performClick()
        assertEquals(2, retries)
        compose.onNodeWithText("Sign out").assertExists()
    }

    @Test
    fun anEmailAccountConfirmsWithAFreshLink() {
        var confirmed = 0
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(
                    AccountSamples.signedIn().copy(delete = DeleteState(google = false, problem = DeleteProblem.Backup)),
                    object : AccountActions { override fun onDeleteConfirm() { confirmed++ } },
                )
            }
        }
        compose.onNodeWithText("We'll email you a link to confirm. Open it on this phone.").assertExists()
        compose.onNodeWithText("Couldn't delete your backup, so your account was kept. Try again.").assertExists()
        compose.onNode(hasText("Password") and hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Email me a link").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun theDeletionLinkOnItsWaySaysWhatItDoes() {
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(AccountSamples.signedIn().copy(delete = DeleteState(google = false, linkSent = true)), AccountActions.None)
            }
        }
        compose.onNodeWithText("Check your email. Tapping the link in it on this phone deletes your account.").assertExists()
        compose.onNodeWithText("Send again").assertExists()
    }

    @Test
    fun aPartlyFailedDeletionSaysWhatWasDeleted() {
        val google = AccountUser("uid-g", "parent@gmail.com", google = true)
        compose.setContent {
            NightLightTheme(night = false) {
                AccountScreen(
                    AccountSamples.signedIn().copy(
                        user = google,
                        delete = DeleteState(google = true, problem = DeleteProblem.Account(AccountError.NO_CONNECTION)),
                    ),
                    AccountActions.None,
                )
            }
        }
        compose.onNodeWithText("Your backup was deleted, but your account wasn't. Try again.").assertExists()
        compose.onNodeWithText("You'll sign in with Google again to confirm.").assertExists()
        compose.onNode(hasText("Password") and hasSetTextAction()).assertDoesNotExist()
    }
}
