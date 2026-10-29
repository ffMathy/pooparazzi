package com.ffmathy.pooparazzi.phone.account

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in account (BABIES_AND_BACKUP §3). Its family is resolved by [FamilyService], which hands the backup a
 * [com.ffmathy.pooparazzi.core.cloud.CloudAccount] with the family once it is known.
 *
 * @property google signed in with Google: deleting the account asks for Google again instead of an email link.
 */
data class AccountUser(val uid: String, val email: String, val google: Boolean)

/** Why an account action failed, as the Account screen words it. */
enum class AccountError {
    /** "Enter a valid email address" (also: the address does not match the one the link was sent to). */
    INVALID_EMAIL,

    /** "No connection. Try again." */
    NO_CONNECTION,

    /**
     * No answer in time, but the call may still complete (Firebase keeps trying): "No answer yet. It may still go
     * through in a moment." A sign-in that completes later shows through [Accounts.user].
     */
    TIMED_OUT,

    /** Too many attempts from this phone: "Too many tries. Wait a moment and try again." */
    TOO_MANY_ATTEMPTS,

    /**
     * The project's daily allowance of sign-in emails is used up (the free Spark plan sends few, docs/BACKEND.md ›
     * Cost): "Too many sign-in emails today. Try again tomorrow, or use Google."
     */
    EMAIL_QUOTA,

    /** An email link that expired, was used already or is not one: "That link has expired or was already used. Send a new one." */
    LINK_INVALID,

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

/** What the last email link this phone asked for is for. */
enum class LinkPurpose {
    /** "Email me a sign-in link": signs in (creating the account the first time). */
    SIGN_IN,

    /** "Email me a link" in "Delete account?": signs the signed-in account in again, and the deletion goes on. */
    DELETE_ACCOUNT,
}

/** The email link this phone asked for last: where it went and what it is for. Kept on this phone only. */
data class SentLink(val email: String, val purpose: LinkPurpose)

/**
 * The optional account of the phone app (BABIES_AND_BACKUP §3): Firebase Authentication in builds with a Firebase
 * configuration ([FirebaseAccounts]), [NoAccounts] otherwise. There are no passwords: sign-in is Google or a link sent
 * by email. Nothing here throws: failures are [AccountResult.Failed].
 */
interface Accounts {
    /** False in builds without a Firebase configuration: no Welcome, and Settings says the backup isn't set up. */
    val available: Boolean

    /** Google sign-in is possible: the configuration carries the web client id (the Google provider is enabled). */
    val googleAvailable: Boolean

    /** The project's website, `<project-id>.web.app` (invite links, sign-in links, the privacy policy), or null. */
    val hostingDomain: String?

    /** The signed-in account, or null. */
    val user: StateFlow<AccountUser?>

    /** The email link asked for last on this phone, until it is used (or another is asked for). */
    val sentLink: SentLink?

    /** Signs in with a Google account chosen in the system's account picker. Creating an account is the same thing. */
    suspend fun signInWithGoogle(activity: Activity): AccountResult

    /** Emails a sign-in link to [email] and remembers the address on this phone ([sentLink]). */
    suspend fun sendSignInLink(email: String): AccountResult

    /** Whether [link] (an App Link that opened the app) is an email sign-in link. */
    fun isSignInLink(link: String): Boolean

    /** Signs in with an email [link] sent to [email]. Creating an account is the same thing. */
    suspend fun signInWithLink(email: String, link: String): AccountResult

    /** Signs out. The log stays on the phone. */
    suspend fun signOut()

    /** Signs in again with a fresh Google credential of the same account, as deleting the account requires. */
    suspend fun reauthenticateWithGoogle(activity: Activity): AccountResult

    /** Emails a link to the signed-in address that signs it in again for deleting the account ([LinkPurpose.DELETE_ACCOUNT]). */
    suspend fun sendReauthenticationLink(): AccountResult

    /** Signs the signed-in account in again with an email [link] ([sendReauthenticationLink]). */
    suspend fun reauthenticateWithLink(link: String): AccountResult

    /**
     * Deletes the account (after signing in again); its backup must be deleted first. Deletes its `users/{uid}`
     * document, then the account itself. Signs out on success.
     */
    suspend fun deleteAccount(): AccountResult
}

/** Builds without a Firebase configuration: no accounts, nothing signed in. */
object NoAccounts : Accounts {
    private val nobody = MutableStateFlow<AccountUser?>(null).asStateFlow()
    private val unavailable = AccountResult.Failed(AccountError.OTHER)
    override val available = false
    override val googleAvailable = false
    override val hostingDomain: String? = null
    override val user: StateFlow<AccountUser?> = nobody
    override val sentLink: SentLink? = null
    override suspend fun signInWithGoogle(activity: Activity): AccountResult = unavailable
    override suspend fun sendSignInLink(email: String): AccountResult = unavailable
    override fun isSignInLink(link: String): Boolean = false
    override suspend fun signInWithLink(email: String, link: String): AccountResult = unavailable
    override suspend fun signOut() = Unit
    override suspend fun reauthenticateWithGoogle(activity: Activity): AccountResult = unavailable
    override suspend fun sendReauthenticationLink(): AccountResult = unavailable
    override suspend fun reauthenticateWithLink(link: String): AccountResult = unavailable
    override suspend fun deleteAccount(): AccountResult = unavailable
}

/** Checks done before anything is sent, so the common mistakes are worded at once and cost nothing. */
object AccountInput {
    fun emailError(email: String): AccountError? {
        val trimmed = email.trim()
        val at = trimmed.indexOf('@')
        return if (at <= 0 || at == trimmed.lastIndex || trimmed.any { it.isWhitespace() }) AccountError.INVALID_EMAIL else null
    }
}
