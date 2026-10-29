package com.ffmathy.pooparazzi.phone

import android.app.Activity
import android.app.Application
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.account.AccountError
import com.ffmathy.pooparazzi.phone.account.AccountResult
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.account.LinkPurpose
import com.ffmathy.pooparazzi.phone.account.SentLink
import com.ffmathy.pooparazzi.phone.launch.AppLink
import com.ffmathy.pooparazzi.phone.launch.AppLinkInbox
import com.ffmathy.pooparazzi.phone.ui.account.AccountUiState
import com.ffmathy.pooparazzi.phone.ui.account.AccountViewModel
import com.ffmathy.pooparazzi.phone.ui.account.DeleteProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Account screen's actions (BABIES_AND_BACKUP §3.3) on a fake [com.ffmathy.pooparazzi.phone.account.Accounts] and
 * a fake backup: Google and email-link sign-in (sending, the daily email quota, a link opened with and without the
 * remembered address), errors in words, a closed Google picker says nothing, and deleting the account (with Google or a
 * fresh email link) reports exactly what was and was not deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()

    private class Rig(
        val vm: AccountViewModel,
        val accounts: FakeAccounts,
        val cloud: FakeCloudSync,
        val families: FakeFamilies,
        val inbox: AppLinkInbox,
    ) {
        val state: AccountUiState get() = vm.state.value

        /** An email link opens the app. */
        fun open(link: String) = inbox.post(AppLink.EmailSignIn(link))
    }

    private fun test(
        accounts: FakeAccounts = FakeAccounts(),
        cloud: FakeCloudSync = FakeCloudSync(),
        block: suspend TestScope.(Rig) -> Unit,
    ) = runTest(dispatcher) {
        val families = FakeFamilies()
        val inbox = AppLinkInbox()
        val vm = AccountViewModel(accounts, cloud, backgroundScope, zone = SampleData.ZONE, families = families, inbox = inbox)
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.receiveLinks() }
        runCurrent()
        block(Rig(vm, accounts, cloud, families, inbox))
    }

    private val googleUser = AccountUser("uid-g", "parent@gmail.com", google = true)
    private val emailUser = AccountUser("uid-1", "parent@example.com", google = false)

    @Test
    fun aSignInThatTimedOutSaysItMayStillGoThroughAndTheScreenFollowsWhenItDoes() = test { rig ->
        rig.accounts.results["google"] = AccountResult.Failed(AccountError.TIMED_OUT)
        rig.vm.onGoogle(activity)
        runCurrent()
        assertEquals(AccountError.TIMED_OUT, rig.state.error)
        assertNull(rig.state.user)

        // Firebase finished the sign-in after all.
        rig.accounts.signInAs(emailUser)
        runCurrent()
        assertEquals(emailUser, rig.state.user)
        assertNull("no stale error", rig.state.error)
    }

    @Test
    fun emailMeASignInLinkSendsItAndSaysWhereItWent() = test { rig ->
        rig.vm.onEmail(" parent@example.com ")
        rig.accounts.gate = CompletableDeferred()
        rig.vm.onSendLink()
        runCurrent()
        assertTrue("busy while it sends", rig.state.busy)
        rig.accounts.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("sendLink:parent@example.com"), rig.accounts.calls)
        assertEquals("parent@example.com", rig.state.linkSentTo)
        assertEquals("remembered on this phone", SentLink("parent@example.com", LinkPurpose.SIGN_IN), rig.accounts.sentLink)
        assertNull(rig.state.user)

        rig.vm.onSendLink()
        runCurrent()
        assertEquals("Send again goes to the same address", "sendLink:parent@example.com", rig.accounts.calls.last())

        rig.vm.onUseAnotherEmail()
        runCurrent()
        assertNull(rig.state.linkSentTo)
    }

    @Test
    fun aMistypedAddressIsWordedWithoutAskingTheBackend() = test { rig ->
        rig.vm.onEmail("not-an-address")
        rig.vm.onSendLink()
        runCurrent()
        assertEquals(AccountError.INVALID_EMAIL, rig.state.error)
        assertTrue(rig.accounts.calls.isEmpty())
        rig.vm.onEmail("parent@example.com")
        runCurrent()
        assertNull("typing clears the error", rig.state.error)
    }

    @Test
    fun theDailyEmailQuotaIsSaidAsSuch() = test { rig ->
        rig.accounts.results["sendLink"] = AccountResult.Failed(AccountError.EMAIL_QUOTA)
        rig.vm.onEmail("parent@example.com")
        rig.vm.onSendLink()
        runCurrent()
        assertEquals(AccountError.EMAIL_QUOTA, rig.state.error)
        assertNull("nothing was sent", rig.state.linkSentTo)
    }

    @Test
    fun theLinkSignsInWithTheRememberedAddress() = test { rig ->
        rig.vm.onEmail("parent@example.com")
        rig.vm.onSendLink()
        runCurrent()
        rig.open(FakeAccounts.LINK + "abc")
        runCurrent()
        assertEquals("signInWithLink:parent@example.com", rig.accounts.calls.last())
        assertEquals("parent@example.com", rig.state.user?.email)
        assertNull(rig.state.linkSentTo)
        assertNull("taken once", rig.inbox.emailLink.value)
    }

    @Test
    fun aLinkOpenedOnAnotherPhoneAsksForTheAddress() = test { rig ->
        rig.open(FakeAccounts.LINK + "abc")
        runCurrent()
        assertTrue(rig.state.linkNeedsEmail)
        assertTrue("nothing signed in yet", rig.accounts.calls.isEmpty())

        rig.vm.onFinishSignIn()
        runCurrent()
        assertEquals(AccountError.INVALID_EMAIL, rig.state.error)

        rig.vm.onEmail("other@example.com")
        rig.vm.onFinishSignIn()
        runCurrent()
        assertEquals(listOf("signInWithLink:other@example.com"), rig.accounts.calls)
        assertEquals("other@example.com", rig.state.user?.email)
        assertFalse(rig.state.linkNeedsEmail)
    }

    @Test
    fun anExpiredOrBrokenLinkIsSaidInWords() = test { rig ->
        rig.open("https://demo-pooparazzi.web.app/signin")
        runCurrent()
        assertEquals(AccountError.LINK_INVALID, rig.state.error)

        rig.accounts.sentLink = SentLink("parent@example.com", LinkPurpose.SIGN_IN)
        rig.accounts.results["signInWithLink"] = AccountResult.Failed(AccountError.LINK_INVALID)
        rig.open(FakeAccounts.LINK + "used")
        runCurrent()
        assertEquals(AccountError.LINK_INVALID, rig.state.error)
        assertNull(rig.state.user)
    }

    @Test
    fun anInviteWaitingForTheSignInIsSaid() = test { rig ->
        assertFalse(rig.state.joining)
        rig.inbox.post(AppLink.Invite("AbCdEfGhIjKlMnOpQrSt_-"))
        runCurrent()
        assertTrue(rig.state.joining)
        rig.vm.onGoogle(activity)
        runCurrent()
        assertFalse("signed in: the join confirmation takes over", rig.state.joining)
    }

    @Test
    fun thePrivacyPolicyIsOnTheProjectsWebsite() {
        test { rig -> assertEquals("https://demo-pooparazzi.web.app/privacy", rig.state.privacyUrl) }
        test(FakeAccounts(hostingDomain = null)) { rig ->
            assertEquals("https://github.com/ffMathy/pooparazzi/blob/main/docs/PRIVACY.md", rig.state.privacyUrl)
        }
    }

    @Test
    fun googleSignsIn() = test { rig ->
        rig.vm.onGoogle(activity)
        runCurrent()
        assertEquals(listOf("google"), rig.accounts.calls)
        assertEquals("parent@gmail.com", rig.state.user?.email)
    }

    @Test
    fun closingTheGooglePickerSaysNothing() = test { rig ->
        rig.vm.onEmail("parent@example.com")
        rig.accounts.results["google"] = AccountResult.Cancelled
        rig.vm.onGoogle(activity)
        runCurrent()
        assertNull(rig.state.user)
        assertNull(rig.state.error)
        assertFalse(rig.state.busy)
        assertEquals("the form is as it was", "parent@example.com", rig.state.email)
    }

    @Test
    fun googleFailuresAreWorded() = test { rig ->
        rig.accounts.results["google"] = AccountResult.Failed(AccountError.NO_GOOGLE_ACCOUNT)
        rig.vm.onGoogle(activity)
        runCurrent()
        assertEquals(AccountError.NO_GOOGLE_ACCOUNT, rig.state.error)
    }

    @Test
    fun oneActionAtATime() = test { rig ->
        rig.accounts.gate = CompletableDeferred()
        rig.vm.onGoogle(activity)
        runCurrent()
        rig.vm.onGoogle(activity)
        rig.vm.onEmail("parent@example.com")
        rig.vm.onSendLink()
        runCurrent()
        rig.accounts.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("google"), rig.accounts.calls)
    }

    @Test
    fun backUpNowSyncsOnceAtATime() = test(FakeAccounts(signedIn = emailUser), FakeCloudSync(CloudStatus.Synced(1L))) { rig ->
        rig.cloud.gate = CompletableDeferred()
        rig.vm.onBackUpNow()
        runCurrent()
        assertTrue(rig.state.backingUp)
        rig.vm.onBackUpNow()
        rig.cloud.gate!!.complete(Unit)
        runCurrent()
        assertEquals(1, rig.cloud.syncs)
        assertFalse(rig.state.backingUp)
        assertEquals(CloudStatus.Synced(1L), rig.state.cloud)
        assertTrue("the family stays as it is", rig.families.holds.isEmpty())
    }

    @Test
    fun backUpNowAfterADeletedBackupReleasesTheFamily() =
        test(FakeAccounts(signedIn = emailUser), FakeCloudSync(CloudStatus.Deleted)) { rig ->
            rig.vm.onBackUpNow()
            runCurrent()
            assertEquals(listOf(false), rig.families.holds)
            assertEquals(1, rig.cloud.syncs)
        }

    @Test
    fun signingOutKeepsNothingOfTheForm() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.vm.onSignOut()
        runCurrent()
        assertEquals(listOf("signOut"), rig.accounts.calls)
        assertNull(rig.state.user)
    }

    @Test
    fun deletingAnEmailAccountSendsALinkWhoseLinkCompletesTheDeletion() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.vm.onDeleteStart()
        runCurrent()
        assertEquals(false, rig.state.delete?.google)
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(listOf("sendReauthLink"), rig.accounts.calls)
        assertEquals(true, rig.state.delete?.linkSent)
        assertEquals("nothing deleted before the link", 0, rig.cloud.deletions)

        rig.open(FakeAccounts.LINK + "fresh")
        runCurrent()
        assertEquals(listOf("sendReauthLink", "reauthLink", "delete"), rig.accounts.calls)
        assertEquals(listOf(true), rig.families.holds)
        assertEquals(1, rig.cloud.deletions)
        assertNull(rig.state.user)
        assertNull(rig.state.delete)
    }

    @Test
    fun theDeletionLinkWorksAfterTheAppWasClosed() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        // The link was asked for before a restart: the screen has no confirmation open.
        rig.accounts.sentLink = SentLink(emailUser.email, LinkPurpose.DELETE_ACCOUNT)
        rig.open(FakeAccounts.LINK + "fresh")
        runCurrent()
        assertEquals(listOf("reauthLink", "delete"), rig.accounts.calls)
        assertNull(rig.state.user)
    }

    @Test
    fun aSignInLinkOpenedWhileSignedInDeletesNothing() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.accounts.sentLink = SentLink(emailUser.email, LinkPurpose.SIGN_IN)
        rig.open(FakeAccounts.LINK + "old")
        runCurrent()
        assertTrue(rig.accounts.calls.isEmpty())
        assertEquals(0, rig.cloud.deletions)
        assertNotNull(rig.state.user)
    }

    @Test
    fun aDeletionLinkThatFailsDeletesNothing() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.vm.onDeleteStart()
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        rig.accounts.results["reauthLink"] = AccountResult.Failed(AccountError.LINK_INVALID)
        rig.open(FakeAccounts.LINK + "expired")
        runCurrent()
        assertEquals(DeleteProblem.SignIn(AccountError.LINK_INVALID), rig.state.delete?.problem)
        assertEquals(0, rig.cloud.deletions)
        assertFalse("delete" in rig.accounts.calls)
        assertNotNull(rig.state.user)
    }

    @Test
    fun theDeletionLinkThatCannotBeSentSaysSo() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.accounts.results["sendReauthLink"] = AccountResult.Failed(AccountError.EMAIL_QUOTA)
        rig.vm.onDeleteStart()
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(DeleteProblem.SignIn(AccountError.EMAIL_QUOTA), rig.state.delete?.problem)
        assertEquals(false, rig.state.delete?.linkSent)
    }

    @Test
    fun aBackupThatCannotBeDeletedKeepsTheAccount() = test(FakeAccounts(signedIn = googleUser)) { rig ->
        rig.cloud.deleteSucceeds = false
        rig.vm.onDeleteStart()
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(DeleteProblem.Backup, rig.state.delete?.problem)
        assertFalse("the account is not deleted without its backup", "delete" in rig.accounts.calls)
        assertEquals("the family is released again", listOf(true, false), rig.families.holds)
        assertNotNull(rig.state.user)
        assertFalse(rig.state.delete!!.busy)
    }

    @Test
    fun anAccountThatCannotBeDeletedAfterItsBackupSaysSo() = test(FakeAccounts(signedIn = googleUser)) { rig ->
        rig.accounts.results["delete"] = AccountResult.Failed(AccountError.NO_CONNECTION)
        rig.vm.onDeleteStart()
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(1, rig.cloud.deletions)
        assertEquals(DeleteProblem.Account(AccountError.NO_CONNECTION), rig.state.delete?.problem)
        assertEquals("held: no new family while the account remains", listOf(true), rig.families.holds)
        assertNotNull(rig.state.user)

        // Trying again signs in again, deletes the (empty) backup and then the account.
        rig.accounts.results.remove("delete")
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertNull(rig.state.user)
        assertNull(rig.state.delete)
    }

    @Test
    fun aGoogleAccountConfirmsWithGoogle() = test(FakeAccounts(signedIn = googleUser)) { rig ->
        rig.vm.onDeleteStart()
        runCurrent()
        assertEquals(true, rig.state.delete?.google)
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(listOf("reauthGoogle", "delete"), rig.accounts.calls)
        assertEquals(1, rig.cloud.deletions)
        assertNull(rig.state.user)
    }

    @Test
    fun aWrongGoogleAccountDeletesNothing() = test(FakeAccounts(signedIn = googleUser)) { rig ->
        rig.accounts.results["reauthGoogle"] = AccountResult.Failed(AccountError.WRONG_ACCOUNT)
        rig.vm.onDeleteStart()
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(DeleteProblem.SignIn(AccountError.WRONG_ACCOUNT), rig.state.delete?.problem)
        assertEquals(0, rig.cloud.deletions)
        assertTrue(rig.families.holds.isEmpty())
    }

    @Test
    fun closingTheGooglePickerDuringDeletionChangesNothing() = test(FakeAccounts(signedIn = googleUser)) { rig ->
        rig.accounts.results["reauthGoogle"] = AccountResult.Cancelled
        rig.vm.onDeleteStart()
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        val dialog = rig.state.delete
        assertNotNull("the confirmation stays open", dialog)
        assertNull("without a message", dialog!!.problem)
        assertFalse(dialog.busy)
        assertEquals(0, rig.cloud.deletions)
        assertNotNull(rig.state.user)
    }

    @Test
    fun theConfirmationCannotBeDismissedWhileDeleting() = test(FakeAccounts(signedIn = googleUser)) { rig ->
        rig.vm.onDeleteStart()
        rig.accounts.gate = CompletableDeferred()
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        rig.vm.onDeleteDismiss()
        runCurrent()
        assertTrue(rig.state.delete!!.busy)
        rig.accounts.gate!!.complete(Unit)
        runCurrent()
        assertNull(rig.state.delete)
    }
}
