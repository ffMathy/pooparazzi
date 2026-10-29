package com.ffmathy.pooparazzi.phone.ui.account

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.cloud.CloudSync
import com.ffmathy.pooparazzi.phone.account.AccountError
import com.ffmathy.pooparazzi.phone.account.AccountInput
import com.ffmathy.pooparazzi.phone.account.AccountResult
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.account.Accounts
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.Families
import com.ffmathy.pooparazzi.phone.account.LinkPurpose
import com.ffmathy.pooparazzi.phone.account.NoFamilies
import com.ffmathy.pooparazzi.phone.account.PrivacyPolicy
import com.ffmathy.pooparazzi.phone.launch.AppLinkInbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** What went wrong while deleting the account, in the order the steps run. */
sealed interface DeleteProblem {
    /** Signing in again failed: nothing was deleted. */
    data class SignIn(val error: AccountError) : DeleteProblem

    /** The backup could not be (fully) deleted: the account is kept, and the next sync completes the backup again. */
    data object Backup : DeleteProblem

    /** The backup is deleted, but the account is not. Trying again deletes the account. */
    data class Account(val error: AccountError) : DeleteProblem
}

/** The "Delete account?" confirmation that "Delete account…" opens. */
@Immutable
data class DeleteState(
    /** Google accounts sign in again with Google; email accounts with a fresh email link. */
    val google: Boolean,
    /** The email link is on its way: its link completes the deletion. */
    val linkSent: Boolean = false,
    /**
     * A deletion link opened the app while the confirmation was not open: it waits here, and "Delete" uses it. A link
     * never deletes the account without the confirmation on screen.
     */
    val link: String? = null,
    val busy: Boolean = false,
    val problem: DeleteProblem? = null,
)

/**
 * The Account screen and the Welcome screen (BABIES_AND_BACKUP §3.1, §3.3) as data.
 *
 * @property user the signed-in account; null shows the sign-in choices.
 * @property busy an account action is running: the buttons wait.
 * @property linkSentTo a sign-in link was emailed to this address: "Check your email".
 * @property linkNeedsEmail a sign-in link opened the app on a phone that did not ask for it: the address is asked for.
 * @property joining an invite link waits for the sign-in: "Sign in to join the family."
 * @property privacyUrl where "privacy policy" links to.
 * @property backingUp "Back up now" is running.
 */
@Immutable
data class AccountUiState(
    val available: Boolean,
    val googleAvailable: Boolean,
    val user: AccountUser?,
    val cloud: CloudStatus,
    val zone: ZoneId,
    val email: String = "",
    val busy: Boolean = false,
    val error: AccountError? = null,
    val linkSentTo: String? = null,
    val linkNeedsEmail: Boolean = false,
    val joining: Boolean = false,
    val privacyUrl: String = PrivacyPolicy.REPOSITORY_URL,
    val backingUp: Boolean = false,
    val delete: DeleteState? = null,
)

/** Everything the parent can do on the Account and Welcome screens. Defaults do nothing (previews). */
@Stable
interface AccountActions {
    fun onBack() {}

    /** "Continue with Google". */
    fun onGoogle() {}
    fun onEmail(email: String) {}

    /** "Email me a sign-in link" (and "Send again"). */
    fun onSendLink() {}

    /** "Use another email": back to the address field. */
    fun onUseAnotherEmail() {}

    /** "Sign in" with the typed address, for a link opened on a phone that did not ask for it. */
    fun onFinishSignIn() {}

    /** The "privacy policy" link under the sign-in buttons. */
    fun onOpenPrivacy() {}
    fun onBackUpNow() {}
    fun onSignOut() {}

    /** "Delete account…": opens the confirmation. */
    fun onDeleteStart() {}

    /** "Delete" (Google: signs in again first) or "Email me a link" (email accounts). */
    fun onDeleteConfirm() {}
    fun onDeleteDismiss() {}

    /** Welcome's "Continue without an account". */
    fun onContinueWithout() {}

    object None : AccountActions
}

/** The form's own state, beside the account and the backup status. */
private data class Form(
    val email: String = "",
    val busy: Boolean = false,
    val error: AccountError? = null,
    val linkSentTo: String? = null,
    /** A sign-in link that waits for the address it was sent to. */
    val pendingLink: String? = null,
    val backingUp: Boolean = false,
    val delete: DeleteState? = null,
)

/**
 * The account's actions. Errors are shown in words; a closed Google picker ([AccountResult.Cancelled]) says nothing.
 *
 * Signing in is Google or an email link: "Email me a sign-in link" sends it and remembers the address; the link opens
 * the app ([AppLinkInbox.emailLink], taken here) and completes the sign-in, asking for the address only when this phone
 * did not ask for the link.
 *
 * Deleting the account signs in again (Google, or a fresh email link whose link then completes the deletion), holds the
 * account's family ([Families.holdForDeletion]), deletes the backup, then the users document and the account; the log
 * on this phone stays. Should the backup not be deleted, the account is kept. Should the account not be deleted after
 * its backup was, the confirmation says exactly that.
 *
 * The Google actions take the activity the system's account picker opens over; the screen passes it in.
 *
 * @param workScope runs "Back up now" and the account deletion, which must finish even if the screen closes.
 */
class AccountViewModel(
    private val accounts: Accounts,
    private val cloud: CloudSync,
    private val workScope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val families: Families = NoFamilies,
    private val inbox: AppLinkInbox = AppLinkInbox(),
) : ViewModel() {

    private val form = MutableStateFlow(Form())
    private val privacyUrl = PrivacyPolicy.url(accounts.hostingDomain)

    val state: StateFlow<AccountUiState> = combine(accounts.user, cloud.status, form, inbox.invite) { user, status, f, invite ->
        AccountUiState(
            available = accounts.available,
            googleAvailable = accounts.googleAvailable,
            user = user,
            cloud = status,
            zone = zone,
            email = f.email,
            busy = f.busy,
            error = f.error,
            linkSentTo = f.linkSentTo.takeIf { user == null },
            linkNeedsEmail = f.pendingLink != null && user == null,
            joining = invite != null && user == null,
            privacyUrl = privacyUrl,
            backingUp = f.backingUp,
            delete = f.delete?.takeIf { user != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial())

    init {
        // A sign-in that timed out ("it may still go through") and then went through after all: the screen follows the
        // account, and the old error goes with the form it belonged to.
        viewModelScope.launch {
            accounts.user.map { it?.uid }.distinctUntilChanged().drop(1).collect { uid ->
                if (uid != null) form.update { it.copy(error = null, linkSentTo = null, pendingLink = null) }
            }
        }
    }

    /**
     * Takes the email links that open the app, while the screen shows (the Welcome's view model outlives its screen,
     * and must not take the links meant for the Account screen after it).
     */
    suspend fun receiveLinks() {
        inbox.emailLink.filterNotNull().collect { inbox.takeEmailLink()?.let(::onLink) }
    }

    private fun initial() = AccountUiState(
        available = accounts.available,
        googleAvailable = accounts.googleAvailable,
        user = accounts.user.value,
        cloud = cloud.status.value,
        zone = zone,
        privacyUrl = privacyUrl,
    )

    fun onGoogle(activity: Activity) = run { accounts.signInWithGoogle(activity) }

    fun onEmail(email: String) = form.update { it.copy(email = email, error = null) }

    /** "Email me a sign-in link" to the typed address, or "Send again" to the address it went to. */
    fun onSendLink() {
        val f = form.value
        val email = (f.linkSentTo ?: f.email).trim()
        run(onSuccess = { it.copy(linkSentTo = email, pendingLink = null) }) { accounts.sendSignInLink(email) }
    }

    fun onUseAnotherEmail() = form.update { it.copy(linkSentTo = null, error = null) }

    /** Completes the sign-in of a link opened without the remembered address, with the typed one. */
    fun onFinishSignIn() {
        val f = form.value
        val link = f.pendingLink ?: return
        AccountInput.emailError(f.email)?.let { error -> return form.update { it.copy(error = error) } }
        run(onSuccess = { it.copy(pendingLink = null) }) { accounts.signInWithLink(f.email.trim(), link) }
    }

    /**
     * An email link opened the app. Signed out, it signs in (with the address this phone sent it to, or asks for it).
     * Signed in, it is the link "Delete account?" asked for, and completes the deletion; any other link is ignored.
     */
    private fun onLink(link: String) {
        val user = accounts.user.value
        val sent = accounts.sentLink
        if (user != null) {
            if (sent?.purpose == LinkPurpose.DELETE_ACCOUNT && accounts.isSignInLink(link)) {
                val open = form.value.delete
                if (open != null && !open.google) {
                    // The confirmation is open and asked for this link: it completes the deletion.
                    form.update { it.copy(delete = open.copy(linkSent = true)) }
                    startDeletion { accounts.reauthenticateWithLink(link) }
                } else {
                    // Asked for earlier (the app was closed meanwhile, say): ask again before deleting anything.
                    form.update { it.copy(delete = DeleteState(google = false, link = link)) }
                }
            }
            return
        }
        if (!accounts.isSignInLink(link)) {
            form.update { it.copy(error = AccountError.LINK_INVALID) }
            return
        }
        if (sent == null) {
            form.update { it.copy(pendingLink = link, linkSentTo = null, error = null) }
            return
        }
        form.update { it.copy(email = sent.email) }
        run(onSuccess = { it.copy(linkSentTo = null) }) { accounts.signInWithLink(sent.email, link) }
    }

    fun onBackUpNow() {
        if (form.value.backingUp) return
        form.update { it.copy(backingUp = true) }
        workScope.launch {
            try {
                // A backup deleted with a failed account deletion: the parent asks to back up again, in a family.
                if (cloud.status.value == CloudStatus.Deleted) families.holdForDeletion(false)
                cloud.syncNow()
            } finally {
                form.update { it.copy(backingUp = false) }
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            accounts.signOut()
            form.value = Form()
        }
    }

    fun onDeleteStart() {
        val user = accounts.user.value ?: return
        form.update { it.copy(delete = DeleteState(google = user.google)) }
    }

    /** Cancels the deletion: also its email link, which must not delete the account when it opens the app later. */
    fun onDeleteDismiss() {
        if (form.value.delete?.busy == true) return
        form.update { it.copy(delete = null) }
        if (accounts.sentLink?.purpose == LinkPurpose.DELETE_ACCOUNT) accounts.forgetSentLink()
    }

    /**
     * Confirms the deletion. A Google account signs in again ([activity]) and the deletion goes on; an email account
     * gets a link, which completes it when it opens the app.
     */
    fun onDeleteConfirm(activity: Activity?) {
        val dialog = form.value.delete ?: return
        if (dialog.busy) return
        val link = dialog.link
        if (link != null) {
            startDeletion { accounts.reauthenticateWithLink(link) }
            return
        }
        if (dialog.google) {
            startDeletion {
                if (activity != null) accounts.reauthenticateWithGoogle(activity) else AccountResult.Failed(AccountError.OTHER)
            }
            return
        }
        setDelete { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            val sent = accounts.sendReauthenticationLink()
            setDelete {
                when (sent) {
                    AccountResult.Success -> it.copy(busy = false, linkSent = true)
                    AccountResult.Cancelled -> it.copy(busy = false)
                    is AccountResult.Failed -> it.copy(busy = false, problem = DeleteProblem.SignIn(sent.error))
                }
            }
        }
    }

    /** Signs in again with [reauthenticate], then deletes the backup and the account. */
    private fun startDeletion(reauthenticate: suspend () -> AccountResult) {
        if (form.value.delete?.busy == true) return
        setDelete { it.copy(busy = true, problem = null) }
        workScope.launch {
            val outcome = try {
                delete(reauthenticate)
            } catch (e: CancellationException) {
                setDelete { it.copy(busy = false) }
                throw e
            }
            when (outcome) {
                DeleteOutcome.Done -> form.value = Form()
                DeleteOutcome.Cancelled -> setDelete { it.copy(busy = false) }
                is DeleteOutcome.Failed -> setDelete { it.copy(busy = false, problem = outcome.problem, linkSent = false, link = null) }
            }
        }
    }

    private sealed interface DeleteOutcome {
        data object Done : DeleteOutcome

        /** The Google picker was closed: the confirmation stays as it was, without a message. */
        data object Cancelled : DeleteOutcome
        data class Failed(val problem: DeleteProblem) : DeleteOutcome
    }

    /** The deletion's steps, each only after the one before worked. */
    private suspend fun delete(reauthenticate: suspend () -> AccountResult): DeleteOutcome {
        when (val signedIn = reauthenticate()) {
            AccountResult.Success -> Unit
            AccountResult.Cancelled -> return DeleteOutcome.Cancelled
            is AccountResult.Failed -> return DeleteOutcome.Failed(DeleteProblem.SignIn(signedIn.error))
        }
        // No new family may be made for the account while (and after) its backup goes.
        families.holdForDeletion(true)
        if (!cloud.deleteBackup()) {
            families.holdForDeletion(false)
            return DeleteOutcome.Failed(DeleteProblem.Backup)
        }
        return when (val deleted = accounts.deleteAccount()) {
            AccountResult.Success -> DeleteOutcome.Done
            AccountResult.Cancelled -> DeleteOutcome.Failed(DeleteProblem.Account(AccountError.OTHER))
            is AccountResult.Failed -> DeleteOutcome.Failed(DeleteProblem.Account(deleted.error))
        }
    }

    private fun setDelete(transform: (DeleteState) -> DeleteState) =
        form.update { f -> f.copy(delete = f.delete?.let(transform)) }

    /** Runs a sign-in style action: busy while it runs, its error in words after, nothing at all when cancelled. */
    private fun run(
        onSuccess: (Form) -> Form = { it },
        action: suspend () -> AccountResult,
    ) {
        if (form.value.busy) return
        form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = try {
                action()
            } finally {
                form.update { it.copy(busy = false) }
            }
            form.update { f ->
                when (result) {
                    AccountResult.Success -> onSuccess(f)
                    AccountResult.Cancelled -> f
                    is AccountResult.Failed -> f.copy(error = result.error)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AccountViewModel(
                    AppAccounts.accounts, Core.cloud, Core.graph.scope,
                    families = AppAccounts.families, inbox = AppLinkInbox.shared,
                )
            }
        }
    }
}
