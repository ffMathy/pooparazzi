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
import com.ffmathy.pooparazzi.phone.account.AccountResult
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.account.Accounts
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** The email form's two modes (BABIES_AND_BACKUP §3.3). */
enum class EmailMode { SIGN_IN, CREATE }

/** A line the signed-out form shows after an action that worked. */
enum class AccountNotice {
    /** "If {email} has an account, a link to choose a new password is on its way." */
    RESET_SENT,
}

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
    /** Google accounts sign in again with Google; the confirmation then asks for nothing more. */
    val google: Boolean,
    val password: String = "",
    val busy: Boolean = false,
    val problem: DeleteProblem? = null,
)

/**
 * The Account screen and the Welcome screen (BABIES_AND_BACKUP §3.1, §3.3) as data.
 *
 * @property user the signed-in account; null shows the signed-out form.
 * @property busy an account action is running: the buttons wait.
 * @property backingUp "Back up now" is running.
 */
@Immutable
data class AccountUiState(
    val available: Boolean,
    val googleAvailable: Boolean,
    val user: AccountUser?,
    val cloud: CloudStatus,
    val zone: ZoneId,
    val mode: EmailMode = EmailMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: AccountError? = null,
    val notice: AccountNotice? = null,
    val noticeEmail: String = "",
    val backingUp: Boolean = false,
    val delete: DeleteState? = null,
)

/** Everything the parent can do on the Account and Welcome screens. Defaults do nothing (previews). */
@Stable
interface AccountActions {
    fun onBack() {}

    /** "Continue with Google". */
    fun onGoogle() {}
    fun onMode(mode: EmailMode) {}
    fun onEmail(email: String) {}
    fun onPassword(password: String) {}

    /** The main button: "Sign in" or "Create account". */
    fun onSubmit() {}
    fun onForgotPassword() {}
    fun onBackUpNow() {}
    fun onSignOut() {}

    /** "Delete account…": opens the confirmation. */
    fun onDeleteStart() {}
    fun onDeletePassword(password: String) {}
    fun onDeleteConfirm() {}
    fun onDeleteDismiss() {}

    /** Welcome's "Continue without an account". */
    fun onContinueWithout() {}

    object None : AccountActions
}

/** The form's own state, beside the account and the backup status. */
private data class Form(
    val mode: EmailMode = EmailMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: AccountError? = null,
    val notice: AccountNotice? = null,
    val noticeEmail: String = "",
    val backingUp: Boolean = false,
    val delete: DeleteState? = null,
)

/**
 * The account's actions. Errors are shown in words; a closed Google picker ([AccountResult.Cancelled]) says nothing.
 *
 * Deleting the account signs in again (password or Google), deletes the backup, then the account; the log on this
 * phone stays. Should the backup not be deleted, the account is kept. Should the account not be
 * deleted after its backup was, the confirmation says exactly that.
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
) : ViewModel() {

    private val form = MutableStateFlow(Form())

    val state: StateFlow<AccountUiState> = combine(accounts.user, cloud.status, form) { user, status, f ->
        AccountUiState(
            available = accounts.available,
            googleAvailable = accounts.googleAvailable,
            user = user,
            cloud = status,
            zone = zone,
            mode = f.mode,
            email = f.email,
            password = f.password,
            busy = f.busy,
            error = f.error,
            notice = f.notice,
            noticeEmail = f.noticeEmail,
            backingUp = f.backingUp,
            delete = f.delete?.takeIf { user != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial())

    init {
        // A sign-in that timed out ("it may still go through") and then went through after all: the screen follows the
        // account, and the old error goes with the form it belonged to.
        viewModelScope.launch {
            accounts.user.map { it?.uid }.distinctUntilChanged().drop(1).collect { uid ->
                if (uid != null) form.update { it.copy(error = null, password = "") }
            }
        }
    }

    private fun initial() = AccountUiState(
        available = accounts.available,
        googleAvailable = accounts.googleAvailable,
        user = accounts.user.value,
        cloud = cloud.status.value,
        zone = zone,
    )

    fun onGoogle(activity: Activity) = run { accounts.signInWithGoogle(activity) }

    fun onMode(mode: EmailMode) = form.update { it.copy(mode = mode, error = null, notice = null) }

    fun onEmail(email: String) = form.update { it.copy(email = email, error = null) }

    fun onPassword(password: String) = form.update { it.copy(password = password, error = null) }

    fun onSubmit() {
        val f = form.value
        run {
            when (f.mode) {
                EmailMode.SIGN_IN -> accounts.signIn(f.email, f.password)
                EmailMode.CREATE -> accounts.createAccount(f.email, f.password)
            }
        }
    }

    fun onForgotPassword() {
        val email = form.value.email
        run(onSuccess = { it.copy(notice = AccountNotice.RESET_SENT, noticeEmail = email.trim()) }) {
            accounts.sendPasswordReset(email)
        }
    }

    fun onBackUpNow() {
        if (form.value.backingUp) return
        form.update { it.copy(backingUp = true) }
        workScope.launch {
            try {
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

    fun onDeletePassword(password: String) =
        form.update { f -> f.copy(delete = f.delete?.copy(password = password, problem = null)) }

    fun onDeleteDismiss() = form.update { f -> if (f.delete?.busy == true) f else f.copy(delete = null) }

    /** Confirms the deletion: signs in again ([activity] for Google), deletes the backup, then the account. */
    fun onDeleteConfirm(activity: Activity?) {
        val dialog = form.value.delete ?: return
        if (dialog.busy) return
        setDelete { it.copy(busy = true, problem = null) }
        workScope.launch {
            val outcome = try {
                delete(dialog, activity)
            } catch (e: CancellationException) {
                setDelete { it.copy(busy = false) }
                throw e
            }
            when (outcome) {
                DeleteOutcome.Done -> form.value = Form()
                DeleteOutcome.Cancelled -> setDelete { it.copy(busy = false) }
                is DeleteOutcome.Failed -> setDelete { it.copy(busy = false, problem = outcome.problem, password = "") }
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
    private suspend fun delete(dialog: DeleteState, activity: Activity?): DeleteOutcome {
        val signedIn = when {
            dialog.google && activity != null -> accounts.reauthenticateWithGoogle(activity)
            dialog.google -> AccountResult.Failed(AccountError.OTHER)
            else -> accounts.reauthenticate(dialog.password)
        }
        when (signedIn) {
            AccountResult.Success -> Unit
            AccountResult.Cancelled -> return DeleteOutcome.Cancelled
            is AccountResult.Failed -> return DeleteOutcome.Failed(DeleteProblem.SignIn(signedIn.error))
        }
        if (!cloud.deleteBackup()) return DeleteOutcome.Failed(DeleteProblem.Backup)
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
        onSuccess: (Form) -> Form = { it.copy(password = "") },
        action: suspend () -> AccountResult,
    ) {
        if (form.value.busy) return
        form.update { it.copy(busy = true, error = null, notice = null) }
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
            initializer { AccountViewModel(AppAccounts.accounts, Core.cloud, Core.graph.scope) }
        }
    }
}
