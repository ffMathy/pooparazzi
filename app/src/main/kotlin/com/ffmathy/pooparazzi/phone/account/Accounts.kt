package com.ffmathy.pooparazzi.phone.account

import android.app.Activity
import com.ffmathy.pooparazzi.core.cloud.CloudAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in account (BABIES_AND_BACKUP §3).
 *
 * @property google signed in with Google: deleting the account asks for Google again instead of a password.
 */
data class AccountUser(val uid: String, val email: String, val google: Boolean) {
    /**
     * What the backup needs of it. The family is not resolved here yet (FAMILIES: `users/{uid}.familyId`), so nothing
     * is synced until it is.
     */
    val cloud: CloudAccount get() = CloudAccount(uid, email, familyId = null)
}

/** Why an account action failed, as the Account screen words it. */
enum class AccountError {
    /** "Enter your email address" / not an address. */
    INVALID_EMAIL,

    /** "Enter your password". */
    MISSING_PASSWORD,

    /** "Wrong email or password". */
    WRONG_CREDENTIALS,

    /** "That email already has an account". */
    EMAIL_IN_USE,

    /** "Use at least 6 characters". */
    WEAK_PASSWORD,

    /** "No connection. Try again." */
    NO_CONNECTION,

    /**
     * No answer in time, but the call may still complete (Firebase keeps trying): "No answer yet. It may still go
     * through in a moment." A sign-in that completes later shows through [Accounts.user].
     */
    TIMED_OUT,

    /** Too many attempts from this phone: "Too many tries. Wait a moment and try again." */
    TOO_MANY_ATTEMPTS,

    /** The phone has no Google account to offer (or Google sign-in is not possible on it). */
    NO_GOOGLE_ACCOUNT,

    /** Deleting the account needs a fresh sign-in: "Sign in again to delete your account." */
    RECENT_LOGIN_REQUIRED,

    /** Signing in again for account deletion chose another account than the signed-in one. */
    WRONG_ACCOUNT,

    /** Anything else: "Something went wrong. Try again." */
    OTHER,
}

/** The outcome of an account action. */
sealed interface AccountResult {
    data object Success : AccountResult

    /** The person closed the Google account picker: not an error, and the screen says nothing. */
    data object Cancelled : AccountResult

    data class Failed(val error: AccountError) : AccountResult
}

/**
 * The optional account of the phone app (BABIES_AND_BACKUP §3): Firebase Authentication in builds with a Firebase
 * configuration ([FirebaseAccounts]), [NoAccounts] otherwise. Nothing here throws: failures are [AccountResult.Failed].
 */
interface Accounts {
    /** False in builds without a Firebase configuration: no Welcome, and Settings says the backup isn't set up. */
    val available: Boolean

    /** Google sign-in is possible: the configuration carries the web client id (the Google provider is enabled). */
    val googleAvailable: Boolean

    /** The signed-in account, or null. */
    val user: StateFlow<AccountUser?>

    /** Signs in with a Google account chosen in the system's account picker. Creating an account is the same thing. */
    suspend fun signInWithGoogle(activity: Activity): AccountResult

    suspend fun signIn(email: String, password: String): AccountResult

    /** Creates an email account and signs in to it; the backup starts at once. */
    suspend fun createAccount(email: String, password: String): AccountResult

    /** Sends a password reset link to [email]. */
    suspend fun sendPasswordReset(email: String): AccountResult

    /** Signs out. The log stays on the phone. */
    suspend fun signOut()

    /** Signs in again with the password, as deleting the account requires. */
    suspend fun reauthenticate(password: String): AccountResult

    /** Signs in again with a fresh Google credential of the same account, as deleting the account requires. */
    suspend fun reauthenticateWithGoogle(activity: Activity): AccountResult

    /** Deletes the account (after [reauthenticate]); its backup must be deleted first. Signs out on success. */
    suspend fun deleteAccount(): AccountResult
}

/** Builds without a Firebase configuration: no accounts, nothing signed in. */
object NoAccounts : Accounts {
    private val nobody = MutableStateFlow<AccountUser?>(null).asStateFlow()
    private val unavailable = AccountResult.Failed(AccountError.OTHER)
    override val available = false
    override val googleAvailable = false
    override val user: StateFlow<AccountUser?> = nobody
    override suspend fun signInWithGoogle(activity: Activity): AccountResult = unavailable
    override suspend fun signIn(email: String, password: String): AccountResult = unavailable
    override suspend fun createAccount(email: String, password: String): AccountResult = unavailable
    override suspend fun sendPasswordReset(email: String): AccountResult = unavailable
    override suspend fun signOut() = Unit
    override suspend fun reauthenticate(password: String): AccountResult = unavailable
    override suspend fun reauthenticateWithGoogle(activity: Activity): AccountResult = unavailable
    override suspend fun deleteAccount(): AccountResult = unavailable
}

/** Checks done before anything is sent, so the common mistakes are worded at once and cost nothing. */
object AccountInput {
    /** Firebase Authentication's minimum password length. */
    const val MIN_PASSWORD_LENGTH = 6

    fun emailError(email: String): AccountError? {
        val trimmed = email.trim()
        val at = trimmed.indexOf('@')
        return if (at <= 0 || at == trimmed.lastIndex || trimmed.any { it.isWhitespace() }) AccountError.INVALID_EMAIL else null
    }

    fun signInError(email: String, password: String): AccountError? =
        emailError(email) ?: if (password.isEmpty()) AccountError.MISSING_PASSWORD else null

    fun createError(email: String, password: String): AccountError? =
        emailError(email) ?: if (password.length < MIN_PASSWORD_LENGTH) AccountError.WEAK_PASSWORD else null
}
