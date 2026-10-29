package com.ffmathy.pooparazzi.core.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** The capabilities other nodes advertise; a seam over Play Services' CapabilityClient for tests. Calls may throw. */
fun interface Capabilities {
    /** The ids of the other nodes (never this device) known to advertise [capability], reachable or not. */
    suspend fun nodesWith(capability: String): Set<String>
}

/**
 * Whether a paired device runs a Pooparazzi from before several babies: it advertises [SyncPaths.CAPABILITY] but not
 * [SyncPaths.BABIES_CAPABILITY]. Such a watch ignores baby ids and resolves conflicts across all babies (it would
 * soft-delete one twin's running sleep, or a diaper changed seconds after the other twin's, as a duplicate), so the
 * phone lets no second baby be added until the watch is updated. With one baby nothing else changes.
 */
class WatchVersionCheck(private val capabilities: Capabilities) {

    /** True while a paired device needs an update. Unknown (no Play Services, no answer) counts as false. */
    suspend fun needsUpdate(): Boolean = try {
        outdated(capabilities.nodesWith(SyncPaths.CAPABILITY), capabilities.nodesWith(SyncPaths.BABIES_CAPABILITY))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the watch's capabilities", e)
        false
    } catch (e: NoClassDefFoundError) {
        false
    }

    companion object {
        private const val TAG = "WatchVersionCheck"

        /** True when a node with the app ([trackers]) lacks the babies capability ([babies]). */
        fun outdated(trackers: Set<String>, babies: Set<String>): Boolean = (trackers - babies).isNotEmpty()

        /** On Play Services: nodes known to the Wearable API (paired, connected or not), within [timeoutMillis]. */
        fun create(context: Context, timeoutMillis: Long = 10_000L): WatchVersionCheck {
            val app = context.applicationContext
            return WatchVersionCheck { capability ->
                withTimeoutOrNull(timeoutMillis) {
                    val local = Wearable.getNodeClient(app).localNode.await().id
                    Wearable.getCapabilityClient(app).getCapability(capability, CapabilityClient.FILTER_ALL).await()
                        .nodes.map { it.id }.filter { it != local }.toSet()
                } ?: throw IllegalStateException("No answer for $capability")
            }
        }
    }
}
