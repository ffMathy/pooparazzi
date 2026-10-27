package com.ffmathy.xaternity.wear.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Opens screens of the phone app from the watch ("More on phone", DESIGN_SPEC §3.5.8, §10.14) through
 * [RemoteActivityHelper]. Everything degrades quietly: without Play Services or a paired phone, [available] is false
 * and the button is hidden.
 */
class PhoneLink(context: Context) {
    private val appContext = context.applicationContext
    private val helper: RemoteActivityHelper by lazy { RemoteActivityHelper(appContext) }

    /** Whether a phone can receive a remote activity now (unknown counts as available: older phones cannot say). */
    val available: Flow<Boolean> = flow {
        emitAll(
            helper.availabilityStatus.map {
                it == RemoteActivityHelper.STATUS_AVAILABLE || it == RemoteActivityHelper.STATUS_UNKNOWN
            },
        )
    }.catch { e ->
        Log.i(TAG, "Remote activities unavailable", e)
        emit(false)
    }

    /** Asks the phone to open History. Returns true when the request reached the phone. */
    suspend fun openHistory(): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, HISTORY_URI).addCategory(Intent.CATEGORY_BROWSABLE)
        helper.startRemoteActivity(intent).awaitDone()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.i(TAG, "Could not open History on the phone", e)
        false
    }

    companion object {
        private const val TAG = "PhoneLink"

        /** The phone app's History deep link (the phone declares a VIEW + BROWSABLE filter for it). */
        val HISTORY_URI: Uri = "xaternity://history".toUri()
    }
}

private suspend fun ListenableFuture<*>.awaitDone() = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                get()
                continuation.resume(Unit)
            } catch (e: ExecutionException) {
                continuation.resumeWithException(e.cause ?: e)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        },
        Runnable::run,
    )
    continuation.invokeOnCancellation { cancel(false) }
}
