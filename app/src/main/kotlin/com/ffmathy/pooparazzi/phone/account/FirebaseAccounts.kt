package com.ffmathy.pooparazzi.phone.account

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
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
import com.ffmathy.pooparazzi.core.cloud.CloudAccount
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [Accounts] on Firebase Authentication: Google sign-in through Credential Manager (the web client id from the
 * configuration, [FirebaseConfig.webClientId]), and email and password. Build it only when [FirebaseConfig.isPresent].
 *
 * There is no invitation and no email confirmation: a new account is signed in at once, and the backup follows
 * [user].
 */
class FirebaseAccounts(
    context: Context,
    private val auth: FirebaseAuth,
    private val webClientId: String?,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : Accounts {
    private val app = context.applicationContext
    private val current = MutableStateFlow(auth.currentUser?.toAccountUser())
    private val cloudAccount = MutableStateFlow(current.value?.cloud)

    override val available = true
    override val googleAvailable = webClientId != null
    override val user: StateFlow<AccountUser?> = current.asStateFlow()

    /** The signed-in account as the backup follows it ([com.ffmathy.pooparazzi.core.cloud.CloudBackend.account]). */
    val cloud: StateFlow<CloudAccount?> = cloudAccount.asStateFlow()

    init {
        // Also called when the account is deleted, or its session ends on the server (a disabled account).
        auth.addAuthStateListener { refresh() }
    }

    override suspend fun signInWithGoogle(activity: Activity): AccountResult = withGoogle(activity) { credential ->
        auth.signInWithCredential(credential).await()
        refresh()
    }

    override suspend fun signIn(email: String, password: String): AccountResult {
        AccountInput.signInError(email, password)?.let { return AccountResult.Failed(it) }
        return attempt {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            refresh()
        }
    }

    override suspend fun createAccount(email: String, password: String): AccountResult {
        AccountInput.createError(email, password)?.let { return AccountResult.Failed(it) }
        return attempt {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
            refresh()
        }
    }

    override suspend fun sendPasswordReset(email: String): AccountResult {
        AccountInput.emailError(email)?.let { return AccountResult.Failed(it) }
        // Firebase answers the same for an address without an account (email enumeration protection): fine, the
        // screen says a link was sent if the address has an account.
        return attempt { auth.sendPasswordResetEmail(email.trim()).await() }
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

    override suspend fun reauthenticate(password: String): AccountResult {
        if (password.isEmpty()) return AccountResult.Failed(AccountError.MISSING_PASSWORD)
        val user = auth.currentUser ?: return AccountResult.Failed(AccountError.OTHER)
        val email = user.email ?: return AccountResult.Failed(AccountError.OTHER)
        return attempt { user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await() }
    }

    override suspend fun reauthenticateWithGoogle(activity: Activity): AccountResult {
        val user = auth.currentUser ?: return AccountResult.Failed(AccountError.OTHER)
        return withGoogle(activity) { credential -> user.reauthenticate(credential).await() }
    }

    override suspend fun deleteAccount(): AccountResult {
        val user = auth.currentUser ?: return AccountResult.Failed(AccountError.OTHER)
        return attempt {
            user.delete().await()
            refresh()
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
        val signedIn = auth.currentUser?.toAccountUser()
        current.value = signedIn
        cloudAccount.value = signedIn?.cloud
    }

    /**
     * Runs a Firebase call, turning its failure into words. Firebase Authentication can wait a long time for a
     * connection: after [timeoutMillis] the call counts as [AccountError.NO_CONNECTION].
     */
    private suspend fun attempt(block: suspend () -> Unit): AccountResult = try {
        // withTimeoutOrNull: its own cancellation never leaks out as if the caller had been cancelled.
        val done = withTimeoutOrNull(timeoutMillis) { block() }
        if (done == null) {
            refresh()
            AccountResult.Failed(AccountError.NO_CONNECTION)
        } else {
            AccountResult.Success
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Account action failed", e)
        AccountResult.Failed(e.toAccountError())
    }

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

/** The words of a failed Firebase Authentication or Credential Manager call. */
@VisibleForTesting
internal fun Exception.toAccountError(): AccountError = when (this) {
    is FirebaseNetworkException -> AccountError.NO_CONNECTION
    is FirebaseTooManyRequestsException -> AccountError.TOO_MANY_ATTEMPTS
    // Before the generic credentials case: a subclass of it.
    is FirebaseAuthWeakPasswordException -> AccountError.WEAK_PASSWORD
    is FirebaseAuthUserCollisionException -> AccountError.EMAIL_IN_USE
    // Deleting needs a recent sign-in; signing in again just before makes this rare.
    is FirebaseAuthRecentLoginRequiredException -> AccountError.RECENT_LOGIN_REQUIRED
    is FirebaseAuthInvalidUserException ->
        if (errorCode == "ERROR_USER_MISMATCH") AccountError.WRONG_ACCOUNT else AccountError.WRONG_CREDENTIALS
    is FirebaseAuthInvalidCredentialsException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> AccountError.INVALID_EMAIL
        "ERROR_USER_MISMATCH" -> AccountError.WRONG_ACCOUNT
        else -> AccountError.WRONG_CREDENTIALS
    }
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_USER_MISMATCH" -> AccountError.WRONG_ACCOUNT
        "ERROR_REQUIRES_RECENT_LOGIN" -> AccountError.RECENT_LOGIN_REQUIRED
        // With email enumeration protection, a wrong password and an unknown address are the same generic error.
        "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
            AccountError.WRONG_CREDENTIALS
        "ERROR_INVALID_EMAIL" -> AccountError.INVALID_EMAIL
        "ERROR_EMAIL_ALREADY_IN_USE" -> AccountError.EMAIL_IN_USE
        "ERROR_WEAK_PASSWORD" -> AccountError.WEAK_PASSWORD
        "ERROR_NETWORK_REQUEST_FAILED" -> AccountError.NO_CONNECTION
        "ERROR_TOO_MANY_REQUESTS" -> AccountError.TOO_MANY_ATTEMPTS
        else -> AccountError.OTHER
    }
    // The picker had nothing to offer: no Google account on the phone, or no Google Play services.
    is NoCredentialException, is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException ->
        AccountError.NO_GOOGLE_ACCOUNT
    is GetCredentialInterruptedException -> AccountError.OTHER
    else -> AccountError.OTHER
}
