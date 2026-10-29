package com.ffmathy.pooparazzi.phone.account

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.ffmathy.pooparazzi.core.cloud.CloudError
import com.ffmathy.pooparazzi.core.cloud.CloudException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * [Accounts] on Firebase Authentication: Google sign-in through Credential Manager (the web client id from the
 * configuration, [FirebaseConfig.webClientId]), and passwordless sign-in with a link sent by email (BABIES_AND_BACKUP
 * §3.3), which the app receives as an App Link on [hostingDomain]. Build it only when [FirebaseConfig.isPresent].
 *
 * There are no passwords: no password field, reset or sign-up. The first sign-in creates the account, and the backup
 * follows [user] (its family through [FamilyService]).
 *
 * @param links where the last email link went and what it is for, kept on this phone ([EmailLinkPrefs]).
 * @param deleteUserDocument deletes the account's `users/{uid}` document, before the account itself.
 */
class FirebaseAccounts(
    context: Context,
    private val auth: FirebaseAuth,
    private val webClientId: String?,
    override val hostingDomain: String?,
    private val links: EmailLinkPrefs,
    private val deleteUserDocument: suspend (uid: String) -> Unit,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : Accounts {
    private val app = context.applicationContext
    private val current = MutableStateFlow(auth.currentUser?.toAccountUser())

    override val available = true
    override val googleAvailable = webClientId != null
    override val user: StateFlow<AccountUser?> = current.asStateFlow()
    override val sentLink: SentLink? get() = links.sent

    init {
        // Also called when the account is deleted, or its session ends on the server (a disabled account).
        auth.addAuthStateListener { refresh() }
    }

    override suspend fun signInWithGoogle(activity: Activity): AccountResult = withGoogle(activity) { credential ->
        auth.signInWithCredential(credential).await()
        refresh()
    }

    override suspend fun sendSignInLink(email: String): AccountResult {
        AccountInput.emailError(email)?.let { return AccountResult.Failed(it) }
        return sendLink(email.trim(), LinkPurpose.SIGN_IN)
    }

    override fun isSignInLink(link: String): Boolean = try {
        auth.isSignInWithEmailLink(link)
    } catch (e: Exception) {
        Log.w(TAG, "Could not read a link", e)
        false
    }

    override suspend fun signInWithLink(email: String, link: String): AccountResult {
        AccountInput.emailError(email)?.let { return AccountResult.Failed(it) }
        if (!isSignInLink(link)) return AccountResult.Failed(AccountError.LINK_INVALID)
        val result = attempt {
            auth.signInWithEmailLink(email.trim(), link).await()
            refresh()
        }
        // Used up: the next link is asked for again.
        if (result == AccountResult.Success) links.sent = null
        return result
    }

    override suspend fun signOut() {
        auth.signOut()
        refresh()
        // Forgets the Google account chosen last, so the next "Continue with Google" offers the choice again.
        try {
            withTimeoutOrNull(timeoutMillis) { CredentialManager.create(app).clearCredentialState(ClearCredentialStateRequest()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Signed out all the same; only the picker's memory of the last account stays.
            Log.w(TAG, "Could not clear the credential state", e)
        }
    }

    override suspend fun reauthenticateWithGoogle(activity: Activity): AccountResult {
        val user = auth.currentUser ?: return AccountResult.Failed(AccountError.OTHER)
        return withGoogle(activity) { credential -> user.reauthenticate(credential).await() }
    }

    override suspend fun sendReauthenticationLink(): AccountResult {
        val email = auth.currentUser?.email?.takeIf { it.isNotBlank() } ?: return AccountResult.Failed(AccountError.OTHER)
        return sendLink(email, LinkPurpose.DELETE_ACCOUNT)
    }

    override suspend fun reauthenticateWithLink(link: String): AccountResult {
        val user = auth.currentUser ?: return AccountResult.Failed(AccountError.OTHER)
        val email = user.email ?: return AccountResult.Failed(AccountError.OTHER)
        if (!isSignInLink(link)) return AccountResult.Failed(AccountError.LINK_INVALID)
        val result = attempt { user.reauthenticate(EmailAuthProvider.getCredentialWithLink(email, link)).await() }
        if (result == AccountResult.Success) links.sent = null
        return result
    }

    override suspend fun deleteAccount(): AccountResult {
        val user = auth.currentUser ?: return AccountResult.Failed(AccountError.OTHER)
        return attempt {
            // The pointer to the family first: once the account is gone, nobody may delete it any more.
            deleteUserDocument(user.uid)
            user.delete().await()
            refresh()
        }
    }

    /**
     * Sends Firebase's sign-in email to [email] for [purpose], in the app's language. The link opens the app on
     * [hostingDomain] (`/__/auth/links`, continuing to `/signin`), or that site's `/signin` page without the app.
     */
    private suspend fun sendLink(email: String, purpose: LinkPurpose): AccountResult {
        val domain = hostingDomain ?: return AccountResult.Failed(AccountError.OTHER)
        val settings = ActionCodeSettings.newBuilder()
            .setUrl("https://$domain/signin")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(app.packageName, true, null)
            .setLinkDomain(domain)
            .build()
        return attemptWithin(timeoutMillis, ::refresh, errorOf = { it.toSendLinkError() }) {
            auth.setLanguageCode(Locale.getDefault().language)
            auth.sendSignInLinkToEmail(email, settings).await()
            links.sent = SentLink(email, purpose)
        }
    }

    /** Asks the system's Google account picker for an ID token, then runs [block] with its Firebase credential. */
    private suspend fun withGoogle(activity: Activity, block: suspend (AuthCredential) -> Unit): AccountResult {
        val clientId = webClientId ?: return AccountResult.Failed(AccountError.NO_GOOGLE_ACCOUNT)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val idToken = try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Log.w(TAG, "Unexpected credential type ${credential.type}")
                return AccountResult.Failed(AccountError.OTHER)
            }
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            return AccountResult.Cancelled
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed: ${e.type}", e)
            return AccountResult.Failed(e.toAccountError())
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Google sign-in returned an unreadable token", e)
            return AccountResult.Failed(AccountError.OTHER)
        } catch (e: Exception) {
            Log.w(TAG, "Google sign-in failed", e)
            return AccountResult.Failed(AccountError.OTHER)
        }
        return attempt { block(GoogleAuthProvider.getCredential(idToken, null)) }
    }

    private fun refresh() {
        current.value = auth.currentUser?.toAccountUser()
    }

    /** Runs a Firebase call within [timeoutMillis] ([attemptWithin]). */
    private suspend fun attempt(block: suspend () -> Unit): AccountResult = attemptWithin(timeoutMillis, ::refresh, block = block)

    private companion object {
        const val TAG = "Accounts"

        /** How long one Firebase Authentication call may take before it counts as no connection. */
        const val TIMEOUT_MILLIS = 30_000L

        fun FirebaseUser.toAccountUser() = AccountUser(
            uid = uid,
            email = email.orEmpty(),
            google = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID },
        )
    }
}

/**
 * Where this phone's last email link went and what it is for ([Accounts.sentLink]): a link opened later completes
 * that sign-in (or the account deletion) without asking for the address again. Device-local: excluded from backup and
 * device transfer (res/xml/data_extraction_rules.xml), so a link opened on another phone asks for the address.
 */
class EmailLinkPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var sent: SentLink?
        get() {
            val email = prefs.getString(KEY_EMAIL, null) ?: return null
            val purpose = prefs.getString(KEY_PURPOSE, null)?.let { name -> LinkPurpose.entries.firstOrNull { it.name == name } }
            return SentLink(email, purpose ?: LinkPurpose.SIGN_IN)
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_EMAIL)
                remove(KEY_PURPOSE)
            } else {
                putString(KEY_EMAIL, value.email)
                putString(KEY_PURPOSE, value.purpose.name)
            }
        }

    internal companion object {
        const val FILE = "account_email_link"
        private const val KEY_EMAIL = "email"
        private const val KEY_PURPOSE = "purpose"
    }
}

/**
 * Runs a Firebase call, turning its failure into words. Firebase Authentication can wait a long time for a connection:
 * after [timeoutMillis] the app stops waiting, but the call is not cancelled in Firebase and may still complete (a
 * sign-in then arrives through the auth state listener, and the screens follow [Accounts.user]). So it counts as
 * [AccountError.TIMED_OUT] ("it may still go through"), not as no connection; [onTimeout] reads the account again.
 */
@VisibleForTesting
internal suspend fun attemptWithin(
    timeoutMillis: Long,
    onTimeout: () -> Unit,
    errorOf: (Exception) -> AccountError = { it.toAccountError() },
    block: suspend () -> Unit,
): AccountResult = try {
    // withTimeoutOrNull: its own cancellation never leaks out as if the caller had been cancelled.
    val done = withTimeoutOrNull(timeoutMillis) { block() }
    if (done == null) {
        onTimeout()
        AccountResult.Failed(AccountError.TIMED_OUT)
    } else {
        AccountResult.Success
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("Accounts", "Account action failed", e)
    AccountResult.Failed(errorOf(e))
}

/** The words of a failed Firebase Authentication or Credential Manager call. */
@VisibleForTesting
internal fun Exception.toAccountError(): AccountError = when (this) {
    is FirebaseNetworkException -> AccountError.NO_CONNECTION
    // Deleting the users document before the account (Firestore, through the backup's store).
    is CloudException -> if (error == CloudError.OFFLINE) AccountError.NO_CONNECTION else AccountError.OTHER
    is FirebaseTooManyRequestsException -> AccountError.TOO_MANY_ATTEMPTS
    // An email link that expired, was used, or was mangled on its way.
    is FirebaseAuthActionCodeException -> AccountError.LINK_INVALID
    // Deleting needs a recent sign-in; signing in again just before makes this rare.
    is FirebaseAuthRecentLoginRequiredException -> AccountError.RECENT_LOGIN_REQUIRED
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_USER_MISMATCH" -> AccountError.WRONG_ACCOUNT
        "ERROR_REQUIRES_RECENT_LOGIN" -> AccountError.RECENT_LOGIN_REQUIRED
        "ERROR_INVALID_ACTION_CODE", "ERROR_EXPIRED_ACTION_CODE" -> AccountError.LINK_INVALID
        // Also an email link opened with another address than the one it was sent to.
        "ERROR_INVALID_EMAIL" -> AccountError.INVALID_EMAIL
        "ERROR_NETWORK_REQUEST_FAILED" -> AccountError.NO_CONNECTION
        "ERROR_TOO_MANY_REQUESTS" -> AccountError.TOO_MANY_ATTEMPTS
        "ERROR_QUOTA_EXCEEDED" -> AccountError.EMAIL_QUOTA
        else -> AccountError.OTHER
    }
    // The picker had nothing to offer: no Google account on the phone, or no Google Play services.
    is NoCredentialException, is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException ->
        AccountError.NO_GOOGLE_ACCOUNT
    is GetCredentialInterruptedException -> AccountError.OTHER
    else -> AccountError.OTHER
}

/**
 * The words of a failed `sendSignInLinkToEmail`. The free Spark plan sends only a few sign-in emails a day for the
 * whole project (docs/BACKEND.md › Cost): once they are used up Firebase answers QUOTA_EXCEEDED, or "too many
 * requests", and that is said as such ("Try again tomorrow, or use Google"), not as a wait-a-moment.
 */
@VisibleForTesting
internal fun Exception.toSendLinkError(): AccountError {
    val error = toAccountError()
    val quota = error == AccountError.TOO_MANY_ATTEMPTS || error == AccountError.EMAIL_QUOTA ||
        message.orEmpty().contains("QUOTA_EXCEEDED")
    return if (quota) AccountError.EMAIL_QUOTA else error
}
