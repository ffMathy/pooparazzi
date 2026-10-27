package com.ffmathy.pooparazzi.core.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Whether a paired device with the app exists, and whether it is reachable now. */
enum class PeerState { CONNECTED, DISCONNECTED, NONE }

/** The few Wearable Data Layer operations sync needs; a seam for tests. Calls may throw. */
internal interface DataLayer {
    suspend fun put(path: String, data: ByteArray, urgent: Boolean)

    /** Every item whose path starts with [pathPrefix], from all nodes. */
    suspend fun items(pathPrefix: String): List<RemoteItem>

    /** This device's node id (the host of the items it writes). */
    suspend fun localNodeId(): String

    suspend fun peerState(): PeerState
}

/** Thrown when a Play Services call does not answer in time. */
internal class DataLayerTimeoutException(operation: String) : Exception("Wearable $operation timed out")

/** [DataLayer] on Play Services. Clients are created lazily so a device without Play Services never fails at construction. */
internal class PlayServicesDataLayer(
    context: Context,
    private val timeoutMillis: Long = 15_000L,
) : DataLayer {
    private val app = context.applicationContext
    private val dataClient: DataClient by lazy { Wearable.getDataClient(app) }
    private val nodeClient by lazy { Wearable.getNodeClient(app) }
    private val capabilityClient: CapabilityClient by lazy { Wearable.getCapabilityClient(app) }

    override suspend fun put(path: String, data: ByteArray, urgent: Boolean) {
        call("put") {
            val request = PutDataRequest.create(path).setData(data)
            if (urgent) request.setUrgent()
            dataClient.putDataItem(request).await()
        }
    }

    override suspend fun items(pathPrefix: String): List<RemoteItem> = call("getDataItems") {
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(pathPrefix).build()
        val buffer = dataClient.getDataItems(uri, DataClient.FILTER_PREFIX).await()
        try {
            buffer.mapNotNull { item ->
                val path = item.uri.path ?: return@mapNotNull null
                val data = item.data ?: return@mapNotNull null
                RemoteItem(path, data.copyOf(), item.uri.host)
            }
        } finally {
            buffer.release()
        }
    }

    @Volatile
    private var localId: String? = null

    /** Resolved once: a node id never changes while the app runs. */
    override suspend fun localNodeId(): String =
        localId ?: call("getLocalNode") { nodeClient.localNode.await().id }.also { localId = it }

    override suspend fun peerState(): PeerState = call("getCapability") {
        val localId = localNodeId()
        val known = capabilityClient.getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_ALL).await()
            .nodes.filter { it.id != localId }
        if (known.isEmpty()) return@call PeerState.NONE
        val reachable = capabilityClient.getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            .nodes.filter { it.id != localId }
        if (reachable.isEmpty()) PeerState.DISCONNECTED else PeerState.CONNECTED
    }

    private suspend fun <T : Any> call(operation: String, block: suspend () -> T): T =
        withTimeoutOrNull(timeoutMillis) { block() } ?: throw DataLayerTimeoutException(operation)
}

/** True when [error] means "this device cannot use the Wearable API at all" (as opposed to a transient failure). */
internal fun isWearableUnavailable(error: Throwable): Boolean = when (error) {
    is ApiException -> error.statusCode in setOf(
        CommonStatusCodes.API_NOT_CONNECTED,
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
        ConnectionResult.SERVICE_DISABLED,
        ConnectionResult.SERVICE_MISSING,
        ConnectionResult.SERVICE_INVALID,
    )
    is SecurityException, is NoClassDefFoundError -> true
    else -> false
}
