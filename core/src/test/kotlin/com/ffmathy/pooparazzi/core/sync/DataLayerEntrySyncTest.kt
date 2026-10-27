package com.ffmathy.pooparazzi.core.sync

import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryJson
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsJson
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeDataLayer : DataLayer {
    val items = linkedMapOf<String, ByteArray>()
    val remoteItems = mutableListOf<RemoteItem>()
    val urgent = mutableListOf<Boolean>()
    var peers = PeerState.CONNECTED
    var failure: Exception? = null

    override suspend fun put(path: String, data: ByteArray, urgent: Boolean) {
        failure?.let { throw it }
        items[path] = data
        this.urgent += urgent
    }

    override suspend fun items(pathPrefix: String): List<RemoteItem> {
        failure?.let { throw it }
        return remoteItems.filter { it.path.startsWith(pathPrefix) }
    }

    override suspend fun localNodeId(): String {
        failure?.let { throw it }
        return LOCAL_NODE
    }

    override suspend fun peerState(): PeerState {
        failure?.let { throw it }
        return peers
    }
}

private const val LOCAL_NODE = "local-node"

private class FakeSink : SyncSink {
    val entries = mutableListOf<Entry>()
    val batches = mutableListOf<List<Entry>>()
    val settings = mutableListOf<Settings>()
    var pendingPushed = 0
    var pendingResult = true
    var local: Settings? = null

    override suspend fun applyRemoteEntry(entry: Entry): Boolean = entries.add(entry)
    override suspend fun applyRemoteEntries(entries: List<Entry>): Int {
        batches += entries
        return entries.count { applyRemoteEntry(it) }
    }
    override suspend fun applyRemoteSettings(settings: Settings): Boolean = this.settings.add(settings)
    override suspend fun pushPendingEntries(): Boolean {
        pendingPushed++
        return pendingResult
    }
    override suspend fun localSettings(): Settings? = local
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataLayerEntrySyncTest {
    private val clock = FixedClock(1_000_000L)
    private val layer = FakeDataLayer()
    private val sink = FakeSink()
    private val sync = DataLayerEntrySync(layer, clock).also { it.attach(sink) }
    private val feed = Entry.feed(FeedType.FORMULA, 900_000L, id = "f1")

    @Test
    fun pushWritesTheJsonEntryUrgentlyAtItsPath() = runBlocking {
        assertTrue(sync.push(feed))
        assertEquals(feed, EntryJson.decode(layer.items.getValue("/entries/f1").decodeToString()))
        assertEquals(listOf(true), layer.urgent)
        assertEquals(SyncStatus.Synced(clock.now()), sync.status.value)
    }

    @Test
    fun pushWhileThePeerIsAwayIsQueued() = runBlocking {
        layer.peers = PeerState.DISCONNECTED
        assertTrue(sync.push(feed))
        assertEquals(SyncStatus.Offline, sync.status.value)
        layer.peers = PeerState.NONE
        assertTrue(sync.pushSettings(Settings("Noa", updatedAtMillis = 1)))
        assertEquals(SyncStatus.Unavailable, sync.status.value)
        assertEquals(Settings("Noa", updatedAtMillis = 1), SettingsJson.decodeOrNull(layer.items.getValue("/settings").decodeToString()))
    }

    @Test
    fun missingPlayServicesNeverThrows() = runBlocking {
        layer.failure = ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))
        assertFalse(sync.push(feed))
        assertEquals(SyncStatus.Unavailable, sync.status.value)
        assertEquals(0, sync.pullAll())
        sync.retry()
        assertEquals(SyncStatus.Unavailable, sync.status.value)
    }

    @Test
    fun otherFailuresAreErrors() = runBlocking {
        layer.failure = IllegalStateException("boom")
        assertFalse(sync.push(feed))
        assertEquals(SyncStatus.Error, sync.status.value)
        layer.failure = DataLayerTimeoutException("put")
        assertFalse(sync.pushSettings(Settings()))
        assertEquals(SyncStatus.Error, sync.status.value)
    }

    @Test
    fun pullAllMergesEntriesAndSettingsAndSkipsJunk() = runBlocking {
        val remoteSettings = Settings("Noa", updatedAtMillis = 5)
        layer.remoteItems += RemoteItem("/entries/f1", EntryJson.encode(feed).encodeToByteArray(), "watch")
        layer.remoteItems += RemoteItem("/entries/other-id", EntryJson.encode(feed).encodeToByteArray(), "watch")
        layer.remoteItems += RemoteItem("/entries/x", "garbage".encodeToByteArray(), "watch")
        layer.remoteItems += RemoteItem("/settings", SettingsJson.encode(remoteSettings).encodeToByteArray(), "watch")
        assertEquals(2, sync.pullAll())
        assertEquals(listOf(feed), sink.entries)
        assertEquals(listOf(remoteSettings), sink.settings)
        assertEquals(SyncStatus.Synced(clock.now()), sync.status.value)
    }

    @Test
    fun receiveMarksTheDeviceAsSynced() = runBlocking {
        layer.peers = PeerState.DISCONNECTED
        sync.push(feed)
        clock.advanceBy(60_000)
        assertEquals(1, sync.receive(listOf(RemoteItem("/entries/f1", EntryJson.encode(feed).encodeToByteArray(), "watch"))))
        assertEquals(SyncStatus.Synced(clock.now()), sync.status.value)
    }

    @Test
    fun receiveIgnoresThisDevicesOwnItems() = runBlocking {
        // onDataChanged also fires on the writing node: our own echo must neither be merged again nor claim "Synced".
        layer.peers = PeerState.DISCONNECTED
        sync.push(feed)
        assertEquals(SyncStatus.Offline, sync.status.value)
        val own = RemoteItem("/entries/f1", EntryJson.encode(feed).encodeToByteArray(), LOCAL_NODE)
        assertEquals(0, sync.receive(listOf(own)))
        assertEquals(SyncStatus.Offline, sync.status.value)
        assertTrue(sink.entries.isEmpty())

        // A mixed delivery merges only the other device's items.
        val remote = Entry.feed(FeedType.FOOD, 950_000L, id = "w1")
        assertEquals(1, sync.receive(listOf(own, RemoteItem("/entries/w1", EntryJson.encode(remote).encodeToByteArray(), "watch"))))
        assertEquals(listOf(remote), sink.entries)
        assertEquals(SyncStatus.Synced(clock.now()), sync.status.value)
    }

    @Test
    fun aDeliveryIsMergedAsOneBatchOldestWriteFirst() = runBlocking {
        val newer = feed.copy(startMillis = 800_000L, updatedAtMillis = 2_000)
        val older = feed.copy(updatedAtMillis = 1_000)
        val other = Entry.feed(FeedType.FOOD, 950_000L, id = "w1", updatedAtMillis = 1_500)
        sync.receive(listOf(newer, other, older).map { RemoteItem("/entries/${it.id}", EntryJson.encode(it).encodeToByteArray(), "watch") })
        assertEquals(listOf(listOf(older, other, newer)), sink.batches)
    }

    @Test
    fun retryPushesPendingWritesThroughTheSinkAndSettings() = runBlocking {
        sink.local = Settings("Noa", updatedAtMillis = 3)
        layer.peers = PeerState.DISCONNECTED
        sync.retry()
        assertEquals(1, sink.pendingPushed)
        assertTrue(layer.items.containsKey("/settings"))
        assertEquals(SyncStatus.Offline, sync.status.value)
    }

    @Test
    fun paths() {
        assertEquals("/entries/abc", SyncPaths.entry("abc"))
        assertEquals("abc", SyncPaths.entryId("/entries/abc"))
        assertEquals(null, SyncPaths.entryId("/entries/"))
        assertEquals(null, SyncPaths.entryId("/entries/a/b"))
        assertEquals(null, SyncPaths.entryId("/settings"))
    }
}
