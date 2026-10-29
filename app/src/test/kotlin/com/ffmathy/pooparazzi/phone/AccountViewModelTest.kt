package com.ffmathy.pooparazzi.phone

import android.app.Activity
import android.app.Application
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.account.AccountError
import com.ffmathy.pooparazzi.phone.account.AccountResult
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.ui.account.AccountNotice
import com.ffmathy.pooparazzi.phone.ui.account.AccountUiState
import com.ffmathy.pooparazzi.phone.ui.account.AccountViewModel
import com.ffmathy.pooparazzi.phone.ui.account.DeleteProblem
import com.ffmathy.pooparazzi.phone.ui.account.EmailMode
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
 * a fake backup: errors in words, a closed Google picker says nothing, and deleting the account reports exactly what
 * was and was not deleted.
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

    private class Rig(val vm: AccountViewModel, val accounts: FakeAccounts, val cloud: FakeCloudSync) {
        val state: AccountUiState get() = vm.state.value
    }

    private fun test(
        accounts: FakeAccounts = FakeAccounts(),
        cloud: FakeCloudSync = FakeCloudSync(),
        block: suspend TestScope.(Rig) -> Unit,
    ) = runTest(dispatcher) {
        val vm = AccountViewModel(accounts, cloud, backgroundScope, zone = SampleData.ZONE)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        block(Rig(vm, accounts, cloud))
    }

    private val googleUser = AccountUser("uid-g", "parent@gmail.com", google = true)
    private val emailUser = AccountUser("uid-1", "parent@example.com", google = false)

    @Test
    fun aSignInThatTimedOutSaysItMayStillGoThroughAndTheScreenFollowsWhenItDoes() = test { rig ->
        rig.accounts.results["signIn"] = AccountResult.Failed(AccountError.TIMED_OUT)
        rig.vm.onEmail("parent@example.com")
        rig.vm.onPassword("secret1")
        rig.vm.onSubmit()
        runCurrent()
        assertEquals(AccountError.TIMED_OUT, rig.state.error)
        assertNull(rig.state.user)

        // Firebase finished the sign-in after all.
        rig.accounts.signInAs(emailUser)
        runCurrent()
        assertEquals(emailUser, rig.state.user)
        assertNull("no stale error", rig.state.error)
        assertEquals("", rig.state.password)
    }

    @Test
    fun signingInWithEmailSignsIn() = test { rig ->
        rig.vm.onEmail(" parent@example.com ")
        rig.vm.onPassword("secret1")
        rig.accounts.gate = CompletableDeferred()
        rig.vm.onSubmit()
        runCurrent()
        assertTrue("busy while it runs", rig.state.busy)
        rig.accounts.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("signIn"), rig.accounts.calls)
        assertEquals("parent@example.com", rig.state.user?.email)
        assertFalse(rig.state.busy)
        assertNull(rig.state.error)
    }

    @Test
    fun creatingAnAccountSignsInAtOnce() = test { rig ->
        rig.vm.onMode(EmailMode.CREATE)
        rig.vm.onEmail("new@example.com")
        rig.vm.onPassword("secret1")
        rig.vm.onSubmit()
        runCurrent()
        assertEquals(listOf("create"), rig.accounts.calls)
        assertEquals("new@example.com", rig.state.user?.email)
    }

    @Test
    fun mistakesAreWordedWithoutAskingTheBackend() = test { rig ->
        rig.vm.onEmail("not-an-address")
        rig.vm.onPassword("secret1")
        rig.vm.onSubmit()
        runCurrent()
        assertEquals(AccountError.INVALID_EMAIL, rig.state.error)

        rig.vm.onEmail("parent@example.com")
        runCurrent()
        assertNull("typing clears the error", rig.state.error)
        rig.vm.onPassword("")
        rig.vm.onSubmit()
        runCurrent()
        assertEquals(AccountError.MISSING_PASSWORD, rig.state.error)

        rig.vm.onMode(EmailMode.CREATE)
        rig.vm.onPassword("12345")
        rig.vm.onSubmit()
        runCurrent()
        assertEquals(AccountError.WEAK_PASSWORD, rig.state.error)
        assertTrue(rig.accounts.calls.isEmpty())
    }

    @Test
    fun backendErrorsAreShown() = test { rig ->
        rig.accounts.results["signIn"] = AccountResult.Failed(AccountError.WRONG_CREDENTIALS)
        rig.vm.onEmail("parent@example.com")
        rig.vm.onPassword("wrong")
        rig.vm.onSubmit()
        runCurrent()
        assertEquals(AccountError.WRONG_CREDENTIALS, rig.state.error)
        assertNull(rig.state.user)

        rig.accounts.results["create"] = AccountResult.Failed(AccountError.EMAIL_IN_USE)
        rig.vm.onMode(EmailMode.CREATE)
        rig.vm.onPassword("secret1")
        rig.vm.onSubmit()
        runCurrent()
        assertEquals(AccountError.EMAIL_IN_USE, rig.state.error)
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
        rig.vm.onPassword("secret1")
        rig.vm.onSubmit()
        runCurrent()
        rig.accounts.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("google"), rig.accounts.calls)
    }

    @Test
    fun forgotPasswordSendsALinkToTheTypedAddress() = test { rig ->
        rig.vm.onForgotPassword()
        runCurrent()
        assertEquals("no address yet", AccountError.INVALID_EMAIL, rig.state.error)

        rig.vm.onEmail("parent@example.com ")
        rig.vm.onForgotPassword()
        runCurrent()
        assertEquals(listOf("reset"), rig.accounts.calls)
        assertEquals(AccountNotice.RESET_SENT, rig.state.notice)
        assertEquals("parent@example.com", rig.state.noticeEmail)
        assertNull(rig.state.error)

        rig.accounts.results["reset"] = AccountResult.Failed(AccountError.NO_CONNECTION)
        rig.vm.onForgotPassword()
        runCurrent()
        assertEquals(AccountError.NO_CONNECTION, rig.state.error)
        assertNull(rig.state.notice)
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
    }

    @Test
    fun signingOutKeepsNothingOfTheForm() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.vm.onSignOut()
        runCurrent()
        assertEquals(listOf("signOut"), rig.accounts.calls)
        assertNull(rig.state.user)
    }

    @Test
    fun deletingAnEmailAccountAsksForThePasswordThenDeletesTheBackupAndTheAccount() =
        test(FakeAccounts(signedIn = emailUser)) { rig ->
            rig.vm.onDeleteStart()
            runCurrent()
            assertEquals(false, rig.state.delete?.google)
            rig.vm.onDeleteConfirm(activity)
            runCurrent()
            assertEquals(DeleteProblem.SignIn(AccountError.MISSING_PASSWORD), rig.state.delete?.problem)
            assertEquals(0, rig.cloud.deletions)

            rig.vm.onDeletePassword("secret1")
            runCurrent()
            assertNull("typing clears the problem", rig.state.delete?.problem)
            rig.vm.onDeleteConfirm(activity)
            runCurrent()
            assertEquals(listOf("reauth:secret1", "delete"), rig.accounts.calls)
            assertEquals(1, rig.cloud.deletions)
            assertNull(rig.state.user)
            assertNull(rig.state.delete)
        }

    @Test
    fun aWrongPasswordDeletesNothing() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.accounts.results["reauth"] = AccountResult.Failed(AccountError.WRONG_CREDENTIALS)
        rig.vm.onDeleteStart()
        rig.vm.onDeletePassword("wrong")
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(DeleteProblem.SignIn(AccountError.WRONG_CREDENTIALS), rig.state.delete?.problem)
        assertEquals(0, rig.cloud.deletions)
        assertFalse("delete" in rig.accounts.calls)
        assertNotNull(rig.state.user)
    }

    @Test
    fun aBackupThatCannotBeDeletedKeepsTheAccount() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.cloud.deleteSucceeds = false
        rig.vm.onDeleteStart()
        rig.vm.onDeletePassword("secret1")
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(DeleteProblem.Backup, rig.state.delete?.problem)
        assertFalse("the account is not deleted without its backup", "delete" in rig.accounts.calls)
        assertNotNull(rig.state.user)
        assertFalse(rig.state.delete!!.busy)
    }

    @Test
    fun anAccountThatCannotBeDeletedAfterItsBackupSaysSo() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.accounts.results["delete"] = AccountResult.Failed(AccountError.NO_CONNECTION)
        rig.vm.onDeleteStart()
        rig.vm.onDeletePassword("secret1")
        rig.vm.onDeleteConfirm(activity)
        runCurrent()
        assertEquals(1, rig.cloud.deletions)
        assertEquals(DeleteProblem.Account(AccountError.NO_CONNECTION), rig.state.delete?.problem)
        assertNotNull(rig.state.user)

        // Trying again signs in again, deletes the (empty) backup and then the account.
        rig.accounts.results.remove("delete")
        rig.vm.onDeletePassword("secret1")
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
    fun theConfirmationCannotBeDismissedWhileDeleting() = test(FakeAccounts(signedIn = emailUser)) { rig ->
        rig.vm.onDeleteStart()
        rig.vm.onDeletePassword("secret1")
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
