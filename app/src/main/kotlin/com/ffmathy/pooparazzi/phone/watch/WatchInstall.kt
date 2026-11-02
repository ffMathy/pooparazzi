package com.ffmathy.pooparazzi.phone.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.common.util.concurrent.ListenableFuture
import com.ffmathy.pooparazzi.core.sync.SyncPaths
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** What the Wearable API knows about the paired watches; a seam over Play Services for tests. Calls may throw. */
interface WatchNodes {
    /** The ids of the other devices connected now (never this phone). */
    suspend fun connected(): Set<String>

    /** The ids of the other devices known to run Pooparazzi ([SyncPaths.CAPABILITY]), reachable or not. */
    suspend fun withApp(): Set<String>

    /** Opens Pooparazzi's Google Play page on watch [nodeId]. */
    suspend fun openStore(nodeId: String)
}

/**
 * Settings › Watch's "Install on your watch" (DESIGN_SPEC §3.3.5): finds the connected watches that do not have
 * Pooparazzi yet, and opens its Play Store page on them, where one tap installs it.
 */
class WatchInstall(private val nodes: WatchNodes) {

    /** The connected watches without Pooparazzi. Unknown (no Play Services, no answer) counts as none. */
    suspend fun watchesWithoutApp(): Set<String> = guarded(emptySet()) { missing(nodes.connected(), nodes.withApp()) }

    /**
     * Opens Pooparazzi's Play Store page on every connected watch without it. True when it opened on at least one; false
     * when there was none, or no watch answered.
     */
    suspend fun openStoreOnWatches(): Boolean = guarded(false) {
        var opened = false
        for (node in watchesWithoutApp()) {
            try {
                nodes.openStore(node)
                opened = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not open the Play Store on $node", e)
            }
        }
        opened
    }

    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not ask the Wearable API about the watch", e)
        fallback
    } catch (e: NoClassDefFoundError) {
        fallback
    }

    companion object {
        private const val TAG = "WatchInstall"

        /** The connected devices ([connected]) that do not advertise Pooparazzi ([withApp]). */
        fun missing(connected: Set<String>, withApp: Set<String>): Set<String> = connected - withApp

        /** Pooparazzi's page in the watch's Play Store (phone and watch share one application id). */
        fun storeUri(packageName: String): Uri = Uri.parse("market://details?id=$packageName")

        /** On Play Services and the Wear remote-interactions library, each call within [timeoutMillis]. */
        fun create(context: Context, timeoutMillis: Long = 10_000L): WatchInstall {
            val app = context.applicationContext
            return WatchInstall(object : WatchNodes {
                private suspend fun <T : Any> timed(what: String, block: suspend () -> T): T =
                    withTimeoutOrNull(timeoutMillis) { block() } ?: throw IllegalStateException("No answer for $what")

                private suspend fun localId(): String = timed("localNode") { Wearable.getNodeClient(app).localNode.await().id }

                override suspend fun connected(): Set<String> {
                    val local = localId()
                    return timed("connectedNodes") { Wearable.getNodeClient(app).connectedNodes.await() }
                        .map { it.id }.filter { it != local }.toSet()
                }

                override suspend fun withApp(): Set<String> {
                    val local = localId()
                    return timed("getCapability") {
                        Wearable.getCapabilityClient(app).getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_ALL).await()
                    }.nodes.map { it.id }.filter { it != local }.toSet()
                }

                override suspend fun openStore(nodeId: String) {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .setData(storeUri(app.packageName))
                    timed("startRemoteActivity") {
                        RemoteActivityHelper(app).startRemoteActivity(intent, nodeId).awaitDone()
                    }
                }
            })
        }
    }
}

/** Waits for the future; throws its failure (the cause of the [java.util.concurrent.ExecutionException]). */
private suspend fun ListenableFuture<*>.awaitDone() = suspendCancellableCoroutine { continuation ->
    addListener({
        val result = runCatching { get() }.map { }
            .recoverCatching { throw (it as? java.util.concurrent.ExecutionException)?.cause ?: it }
        continuation.resumeWith(result)
    }, Runnable::run)
    continuation.invokeOnCancellation { cancel(false) }
}
