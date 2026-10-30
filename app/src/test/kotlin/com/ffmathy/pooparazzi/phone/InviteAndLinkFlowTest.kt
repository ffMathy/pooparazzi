@file:OptIn(ExperimentalTestApi::class)

package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.cloud.CloudAccount
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.InviteCheck
import com.ffmathy.pooparazzi.phone.account.LinkPurpose
import com.ffmathy.pooparazzi.phone.account.MemoryConsent
import com.ffmathy.pooparazzi.phone.account.SentLink
import com.ffmathy.pooparazzi.phone.account.WelcomePrefs
import com.ffmathy.pooparazzi.phone.launch.AppLink
import com.ffmathy.pooparazzi.phone.launch.AppLinkInbox
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The website's App Links in the real app, with fake accounts and a fake family (BABIES_AND_BACKUP §3.3, §3.5): an
 * invite opened while signed out asks to sign in first and then to join; expired and already-joined invites say so;
 * an email sign-in link signs in on the Account screen.
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
abstract class AppLinkFlowTestBase {
    protected abstract val accounts: FakeAccounts
    protected val families = FakeFamilies()
    protected open val welcomeSeen = false
    protected open val link: AppLink? = AppLink.Invite(CODE)

    /** Who agreed to the backup on this phone already: the account signed in from the start. */
    protected val consent by lazy { MemoryConsent(setOfNotNull(accounts.user.value?.uid)) }
    protected open fun prepare() = Unit

    /** Before the activity starts: the fakes, and the link that "opened" the app. */
    @get:Rule(order = 0)
    val fakes = object : ExternalResource() {
        override fun before() {
            prepare()
            AppAccounts.install(accounts, families, consent)
            WelcomePrefs(ApplicationProvider.getApplicationContext<Application>()).seen = welcomeSeen
            link?.let(AppLinkInbox.shared::post)
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        AppLinkInbox.shared.inviteHandled()
        AppLinkInbox.shared.takeEmailLink()
        Core.reset()
        AppAccounts.reset()
    }

    companion object {
        const val CODE = "AbCdEfGhIjKlMnOpQrSt_-"
        val SIGNED_IN = AccountUser("uid-g", "parent@gmail.com", google = true)
    }
}

/** Signed out on a new phone: the Welcome asks to sign in to join, then the join confirmation, then joined. */
class InviteSignedOutFlowTest : AppLinkFlowTestBase() {
    override val accounts = FakeAccounts()

    @Test
    fun signInThenConfirmThenJoined() {
        compose.waitUntilAtLeastOneExists(hasText("Sign in to join the family."), timeoutMillis = 10_000)
        compose.onNodeWithText("No account needed").assertExists()
        compose.onNodeWithText("Continue with Google").performScrollTo().performClick()

        // The consent comes first: nothing is checked or joined before it.
        compose.waitUntilAtLeastOneExists(hasText("Back up to Google Firebase?"), timeoutMillis = 5_000)
        assertTrue(families.calls.none { it.startsWith("check:") || it.startsWith("join:") })
        compose.onNodeWithText("I agree").performScrollTo().performClick()

        compose.waitUntilAtLeastOneExists(hasText("Join partner@example.com's family?"), timeoutMillis = 5_000)
        compose.onNodeWithText(
            "Your babies and your log on this phone are added to the family's and shared with its members. You'll see theirs too.",
        ).assertExists()
        assertEquals(listOf("check:$CODE"), families.calls.filter { it != "refresh" })
        compose.onNodeWithText("Join").performClick()

        compose.waitUntilAtLeastOneExists(hasText("You're in the family. Your log and babies are being shared."), timeoutMillis = 5_000)
        assertTrue("join:$CODE" in families.calls)
        compose.onNodeWithText("Done").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        assertNull("handled", AppLinkInbox.shared.invite.value)
    }
}

/** Signed out after the Welcome was answered: the Account screen asks to sign in first. */
class InviteAccountScreenFlowTest : AppLinkFlowTestBase() {
    override val accounts = FakeAccounts()
    override val welcomeSeen = true

    @Test
    fun theAccountScreenSignsInFirst() {
        compose.waitUntilAtLeastOneExists(hasText("Sign in to join the family."), timeoutMillis = 10_000)
        compose.onNodeWithText("Continue with Google").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("I agree"), timeoutMillis = 5_000)
        compose.onNodeWithText("I agree").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Join partner@example.com's family?"), timeoutMillis = 5_000)
        compose.onNodeWithText("Not now").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        assertTrue("nothing joined", families.calls.none { it.startsWith("join:") })
        assertNull(AppLinkInbox.shared.invite.value)
    }
}

/** An expired (or used) invite says so. */
class InviteExpiredFlowTest : AppLinkFlowTestBase() {
    override val accounts = FakeAccounts(signedIn = SIGNED_IN)
    override fun prepare() {
        families.check = InviteCheck.Expired
    }

    @Test
    fun saysItExpired() {
        compose.waitUntilAtLeastOneExists(
            hasText("This invite has expired or was already used. Ask for a new one."),
            timeoutMillis = 10_000,
        )
        compose.onNodeWithText("Join").assertDoesNotExist()
    }
}

/** An invite to the family the account is in already. */
class InviteAlreadyMemberFlowTest : AppLinkFlowTestBase() {
    override val accounts = FakeAccounts(signedIn = SIGNED_IN)
    override fun prepare() {
        families.current.value = CloudAccount(SIGNED_IN.uid, SIGNED_IN.email, "fam-1")
        families.check = InviteCheck.AlreadyMember
    }

    @Test
    fun saysSo() {
        compose.waitUntilAtLeastOneExists(hasText("You're already in this family."), timeoutMillis = 10_000)
    }
}

/** An email sign-in link opens the Account screen, which signs in with the remembered address. */
class EmailLinkFlowTest : AppLinkFlowTestBase() {
    override val accounts = FakeAccounts().apply { sentLink = SentLink("parent@example.com", LinkPurpose.SIGN_IN) }
    override val welcomeSeen = true
    override val link: AppLink = AppLink.EmailSignIn(FakeAccounts.LINK + "abc")

    @Test
    fun signsInThenAsksForTheConsent() {
        compose.waitUntilAtLeastOneExists(hasText("Back up to Google Firebase?"), timeoutMillis = 10_000)
        compose.onNodeWithText("You're signed in as parent@example.com. Nothing has been uploaded yet. Before anything is, please agree to this:")
            .assertExists()
        assertEquals(listOf("signInWithLink:parent@example.com"), accounts.calls)
        compose.onNodeWithText("I agree").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Start sleep"), timeoutMillis = 5_000)
        assertEquals(setOf("uid-1"), consent.agreed.value)
    }
}

/** An invite opened by a signed-in account that has not agreed yet: the consent first, and "Not now" signs out. */
class InviteWithoutConsentFlowTest : AppLinkFlowTestBase() {
    override val accounts = FakeAccounts(signedIn = SIGNED_IN)
    override val welcomeSeen = true
    override fun prepare() {
        consent.withdraw(SIGNED_IN.uid)
    }

    @Test
    fun notNowSignsOutAndJoinsNothing() {
        compose.waitUntilAtLeastOneExists(hasText("Back up to Google Firebase?"), timeoutMillis = 10_000)
        compose.onNodeWithText("Not now, sign out").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Sign in to join the family."), timeoutMillis = 5_000)
        assertEquals(listOf("signOut"), accounts.calls)
        assertTrue(families.calls.none { it.startsWith("check:") || it.startsWith("join:") })
    }
}
