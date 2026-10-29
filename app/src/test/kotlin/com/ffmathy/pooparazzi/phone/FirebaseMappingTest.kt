package com.ffmathy.pooparazzi.phone

import android.app.Application
import com.ffmathy.pooparazzi.core.cloud.CloudCursor
import com.ffmathy.pooparazzi.core.cloud.CloudError
import com.ffmathy.pooparazzi.core.cloud.CloudException
import com.ffmathy.pooparazzi.core.model.EntryJson
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.account.AccountError
import com.ffmathy.pooparazzi.phone.account.AccountResult
import com.ffmathy.pooparazzi.phone.account.FirestoreCloudStore
import com.ffmathy.pooparazzi.phone.account.FirestoreCloudStore.Stamp
import com.ffmathy.pooparazzi.phone.account.FirestoreCloudStore.StoredDocument
import com.ffmathy.pooparazzi.phone.account.attemptWithin
import com.ffmathy.pooparazzi.phone.account.shouldDropPassword
import com.ffmathy.pooparazzi.phone.account.toAccountError
import com.ffmathy.pooparazzi.phone.account.toSendLinkError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * What the Firebase classes' answers mean to the app, without starting Firebase: a page of backup documents
 * (BABIES_AND_BACKUP §4.1), the backup's errors and the account errors in words (§3.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FirebaseMappingTest {
    private val entries = SampleData.scenario().take(3)

    private fun doc(index: Int, seconds: Long?, json: String? = EntryJson.encode(entries[index])) =
        StoredDocument(entries[index].id, json, seconds?.let { Stamp(it, 7) })

    @Test
    fun aPageDecodesItsEntriesAndEndsAtItsLastDocument() {
        val page = FirestoreCloudStore.pageOf(listOf(doc(0, 10), doc(1, 11), doc(2, 12)))
        assertEquals(entries, page.items)
        assertEquals(CloudCursor(12, 7, entries[2].id), page.last)
        assertEquals(3, page.documents)
    }

    @Test
    fun badDocumentsAreSkippedButMoveTheCursorOn() {
        val otherId = StoredDocument("someone-else", EntryJson.encode(entries[1]), Stamp(11, 7))
        val page = FirestoreCloudStore.pageOf(listOf(doc(0, 10, json = "{not json"), otherId, doc(2, 12, json = null)))
        assertEquals(emptyList<Any>(), page.items)
        assertEquals(CloudCursor(12, 7, entries[2].id), page.last)
        assertEquals(3, page.documents)
    }

    @Test
    fun aPendingWriteIsSkippedAndTheCursorStopsBeforeIt() {
        val page = FirestoreCloudStore.pageOf(listOf(doc(0, 10), doc(1, null), doc(2, 12)))
        assertEquals(listOf(entries[0], entries[2]), page.items)
        assertEquals(CloudCursor(10, 7, entries[0].id), page.last)
        assertEquals(3, page.documents)

        assertNull(FirestoreCloudStore.pageOf(listOf(doc(0, null))).last)
    }

    @Test
    fun backupErrors() {
        fun firestore(code: FirebaseFirestoreException.Code, signedIn: Boolean = true) =
            FirestoreCloudStore.errorOf(FirebaseFirestoreException("x", code), signedIn = { signedIn })
        assertEquals(CloudError.SIGNED_OUT, firestore(FirebaseFirestoreException.Code.UNAUTHENTICATED))
        assertEquals(CloudError.OFFLINE, firestore(FirebaseFirestoreException.Code.UNAVAILABLE))
        assertEquals(CloudError.OFFLINE, firestore(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))
        assertEquals(CloudError.OTHER, firestore(FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED))
        assertEquals(CloudError.OTHER, firestore(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertEquals(CloudError.SIGNED_OUT, firestore(FirebaseFirestoreException.Code.PERMISSION_DENIED, signedIn = false))
        assertEquals(CloudError.OFFLINE, FirestoreCloudStore.errorOf(FirebaseNetworkException("x")) { true })
        assertEquals(CloudError.OFFLINE, FirestoreCloudStore.errorOf(IOException("x")) { true })
        assertEquals(CloudError.OTHER, FirestoreCloudStore.errorOf(IllegalStateException("x")) { true })
    }

    @Test
    fun accountErrorsInWords() {
        assertEquals(AccountError.NO_CONNECTION, FirebaseNetworkException("x").toAccountError())
        assertEquals(AccountError.TOO_MANY_ATTEMPTS, FirebaseTooManyRequestsException("x").toAccountError())
        assertEquals(AccountError.INVALID_EMAIL, FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "x").toAccountError())
        assertEquals(AccountError.LINK_INVALID, FirebaseAuthActionCodeException("ERROR_EXPIRED_ACTION_CODE", "x").toAccountError())
        assertEquals(AccountError.LINK_INVALID, FirebaseAuthInvalidCredentialsException("ERROR_INVALID_ACTION_CODE", "x").toAccountError())
        assertEquals(
            AccountError.RECENT_LOGIN_REQUIRED,
            FirebaseAuthRecentLoginRequiredException("ERROR_REQUIRES_RECENT_LOGIN", "x").toAccountError(),
        )
        assertEquals(AccountError.WRONG_ACCOUNT, FirebaseAuthInvalidUserException("ERROR_USER_MISMATCH", "x").toAccountError())
        assertEquals(AccountError.OTHER, IllegalStateException("x").toAccountError())
        assertEquals(AccountError.NO_CONNECTION, CloudException(CloudError.OFFLINE).toAccountError())
        assertEquals(AccountError.OTHER, CloudException(CloudError.OTHER).toAccountError())
    }

    @Test
    fun theDailySignInEmailQuotaIsSaidAsSuch() {
        // The free plan's few sign-in emails a day, used up: QUOTA_EXCEEDED, or "too many requests".
        assertEquals(AccountError.EMAIL_QUOTA, FirebaseAuthException("ERROR_QUOTA_EXCEEDED", "x").toSendLinkError())
        assertEquals(AccountError.EMAIL_QUOTA, FirebaseTooManyRequestsException("x").toSendLinkError())
        assertEquals(AccountError.EMAIL_QUOTA, FirebaseAuthException("ERROR_INTERNAL_ERROR", "QUOTA_EXCEEDED : Exceeded daily quota").toSendLinkError())
        assertEquals(AccountError.EMAIL_QUOTA, FirebaseAuthException("ERROR_TOO_MANY_REQUESTS", "x").toSendLinkError())
        // Everything else as for any call.
        assertEquals(AccountError.NO_CONNECTION, FirebaseNetworkException("x").toSendLinkError())
        assertEquals(AccountError.INVALID_EMAIL, FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "x").toSendLinkError())
        // Elsewhere, too many requests is a wait-a-moment.
        assertEquals(AccountError.TOO_MANY_ATTEMPTS, FirebaseTooManyRequestsException("x").toAccountError())
    }

    @Test
    fun aFamilyPageOfBabiesDecodes() {
        val baby = com.ffmathy.pooparazzi.core.model.Baby("default", "Noa", 5)
        val page = FirestoreCloudStore.babyPageOf(
            listOf(StoredDocument("default", com.ffmathy.pooparazzi.core.model.BabyJson.encode(baby), Stamp(3, 1))),
        )
        assertEquals(listOf(baby), page.items)
        assertEquals(CloudCursor(3, 1, "default"), page.last)
    }

    @Test
    fun anAccountCallWithoutAnAnswerInTimeMayStillGoThrough() = runTest {
        var refreshed = 0
        val timedOut = attemptWithin(30_000, onTimeout = { refreshed++ }) { CompletableDeferred<Unit>().await() }
        assertEquals("not \"No connection\": Firebase keeps trying", AccountResult.Failed(AccountError.TIMED_OUT), timedOut)
        assertEquals("the account is read again", 1, refreshed)
        assertEquals(AccountResult.Success, attemptWithin(30_000, onTimeout = { refreshed++ }) {})
        assertEquals(
            AccountResult.Failed(AccountError.NO_CONNECTION),
            attemptWithin(30_000, onTimeout = {}) { throw FirebaseNetworkException("offline") },
        )
        assertEquals(1, refreshed)
    }

    @Test
    fun anEmailLinkTakesAPasswordOffAnAccountThatExistedBefore() {
        // An account someone created for the address with a password, before its owner signed in with a link.
        assertEquals(true, shouldDropPassword(isNewUser = false, providers = listOf("password")))
        assertEquals(true, shouldDropPassword(isNewUser = false, providers = listOf("google.com", "password")))
        assertEquals("unknown counts as existing", true, shouldDropPassword(isNewUser = null, providers = listOf("password")))
        // Created by this very link: no password anyone could know.
        assertEquals(false, shouldDropPassword(isNewUser = true, providers = listOf("password")))
        assertEquals(false, shouldDropPassword(isNewUser = false, providers = listOf("google.com")))
    }
}
