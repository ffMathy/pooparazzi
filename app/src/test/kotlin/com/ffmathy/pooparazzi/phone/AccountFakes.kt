package com.ffmathy.pooparazzi.phone

import android.app.Activity
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.cloud.CloudSync
import com.ffmathy.pooparazzi.phone.account.AccountError
import com.ffmathy.pooparazzi.phone.account.AccountInput
import com.ffmathy.pooparazzi.phone.account.AccountResult
import com.ffmathy.pooparazzi.phone.account.AccountUser
import com.ffmathy.pooparazzi.phone.account.Accounts
import com.ffmathy.pooparazzi.phone.account.WelcomeStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [Accounts] without Firebase: each call is recorded and answers with the result queued for it (success by default),
 * applying the same input checks as the real one. A successful sign-in signs in [nextUser].
 */
class FakeAccounts(
    override val available: Boolean = true,
    override val googleAvailable: Boolean = true,
    signedIn: AccountUser? = null,
) : Accounts {
    private val current = MutableStateFlow(signedIn)
    override val user: StateFlow<AccountUser?> = current.asStateFlow()

    val calls = mutableListOf<String>()
    var nextUser = AccountUser("uid-1", "parent@example.com", google = false)
    var googleUser = AccountUser("uid-g", "parent@gmail.com", google = true)

    /** Results by call name ("google", "signIn", "create", "reset", "reauth", "reauthGoogle", "delete"). */
    val results = mutableMapOf<String, AccountResult>()

    /** When set, calls named here wait for it before answering. */
    var gate: CompletableDeferred<Unit>? = null

    private suspend fun answer(name: String): AccountResult {
        calls += name
        gate?.await()
        return results[name] ?: AccountResult.Success
    }

    fun signInAs(user: AccountUser?) {
        current.value = user
    }

    override suspend fun signInWithGoogle(activity: Activity): AccountResult =
        answer("google").also { if (it == AccountResult.Success) current.value = googleUser }

    override suspend fun signIn(email: String, password: String): AccountResult {
        AccountInput.signInError(email, password)?.let { return AccountResult.Failed(it) }
        return answer("signIn").also { if (it == AccountResult.Success) current.value = nextUser.copy(email = email.trim()) }
    }

    override suspend fun createAccount(email: String, password: String): AccountResult {
        AccountInput.createError(email, password)?.let { return AccountResult.Failed(it) }
        return answer("create").also { if (it == AccountResult.Success) current.value = nextUser.copy(email = email.trim()) }
    }

    override suspend fun sendPasswordReset(email: String): AccountResult {
        AccountInput.emailError(email)?.let { return AccountResult.Failed(it) }
        return answer("reset")
    }

    override suspend fun signOut() {
        calls += "signOut"
        current.value = null
    }

    override suspend fun reauthenticate(password: String): AccountResult {
        if (password.isEmpty()) return AccountResult.Failed(AccountError.MISSING_PASSWORD)
        return answer("reauth:$password").let { results["reauth"] ?: it }
    }

    override suspend fun reauthenticateWithGoogle(activity: Activity): AccountResult = answer("reauthGoogle")

    override suspend fun deleteAccount(): AccountResult =
        answer("delete").also { if (it == AccountResult.Success) current.value = null }
}

/** [CloudSync] whose status the test sets, recording what was asked of it. */
class FakeCloudSync(status: CloudStatus = CloudStatus.Off) : CloudSync {
    val state = MutableStateFlow(status)
    override val status: StateFlow<CloudStatus> = state.asStateFlow()
    var syncs = 0
    var requests = 0
    var deletions = 0
    var deleteSucceeds = true

    /** When set, [syncNow] waits for it. */
    var gate: CompletableDeferred<Unit>? = null

    override fun start() = Unit

    override suspend fun syncNow() {
        syncs++
        gate?.await()
    }

    override fun requestSync() {
        requests++
    }

    override suspend fun deleteBackup(): Boolean {
        deletions++
        return deleteSucceeds
    }
}

/** A [WelcomeStore] in memory. */
class MemoryWelcomeStore(override var seen: Boolean = false) : WelcomeStore
