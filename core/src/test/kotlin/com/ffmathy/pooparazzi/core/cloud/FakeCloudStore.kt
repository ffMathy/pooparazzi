package com.ffmathy.pooparazzi.core.cloud

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryJson
import kotlinx.coroutines.delay

/**
 * An in-memory backend shaped like Firestore's `users/{uid}/entries`: one document per entry holding its JSON and the
 * server time of its last write. The server time only moves forward, and every document of one [upload] (a batch)
 * shares it, as Firestore's commit time does. Pages are ordered by (time, document id), like the app's query.
 */
class FakeCloudStore : CloudStore {
    /** One stored document. */
    class Document(val json: String, val seconds: Long, val nanos: Int)

    private val accounts = mutableMapOf<String, MutableMap<String, Document>>()
    private var seconds = 1_700_000_000L
    private var nanos = 0

    /** While set, every call fails with this error. */
    var failure: CloudError? = null

    /** While set, uploads (only) fail with this error, as a refused write does. */
    var uploadFailure: CloudError? = null

    /** While set, this many more [changesAfter] calls succeed, and the ones after fail with [CloudError.OFFLINE]. */
    var pullsBeforeFailure: Int? = null

    /** How long every call takes (virtual time in tests). */
    var latencyMillis = 0L

    /** When true, the server time stands still, so every write shares one time (the cursor's tie-break on ids). */
    var frozenTime = false

    /** Every [upload] call that was accepted, with the ids it wrote. */
    val uploads = mutableListOf<List<String>>()

    /** How many [changesAfter] calls were made, and how many documents they returned. */
    var pulls = 0
        private set
    var documentsRead = 0
        private set

    /** Total entries written by accepted uploads. */
    val uploadedCount: Int get() = uploads.sumOf { it.size }

    /** The account's backup, decoded. */
    fun entries(uid: String): Map<String, Entry> =
        accounts[uid].orEmpty().mapValues { EntryJson.decode(it.value.json) }

    /** Stores a raw document (e.g. one this version cannot decode), as a write at the next server time. */
    fun putRaw(uid: String, id: String, json: String) {
        tick()
        accounts.getOrPut(uid) { mutableMapOf() }[id] = Document(json, seconds, nanos)
    }

    override suspend fun upload(uid: String, entries: List<Entry>) {
        check(entries.size <= CloudStore.UPLOAD_BATCH) { "A batch of ${entries.size}" }
        fail()
        uploadFailure?.let { throw CloudException(it) }
        tick()
        val docs = accounts.getOrPut(uid) { mutableMapOf() }
        for (entry in entries) docs[entry.id] = Document(EntryJson.encode(entry), seconds, nanos)
        uploads += entries.map { it.id }
    }

    override suspend fun changesAfter(uid: String, cursor: CloudCursor?, limit: Int): CloudPage {
        fail()
        pullsBeforeFailure?.let { left ->
            if (left <= 0) throw CloudException(CloudError.OFFLINE)
            pullsBeforeFailure = left - 1
        }
        pulls++
        val ordered = accounts[uid].orEmpty().entries.sortedBy { it.position() }
        val after = if (cursor == null) ordered else ordered.filter { it.position() > cursor.position() }
        val page = after.take(limit)
        documentsRead += page.size
        val last = page.lastOrNull()?.let { CloudCursor(it.value.seconds, it.value.nanos, it.key) }
        return CloudPage(page.mapNotNull { EntryJson.decodeOrNull(it.value.json) }, last, page.size)
    }

    override suspend fun deleteAll(uid: String) {
        fail()
        accounts.remove(uid)
    }

    private suspend fun fail() {
        if (latencyMillis > 0) delay(latencyMillis)
        failure?.let { throw CloudException(it) }
    }

    private fun tick() {
        if (frozenTime) return
        nanos += 1_000
        if (nanos >= 1_000_000_000) {
            seconds++
            nanos = 0
        }
    }

    /** Where a document stands in the order of changes: (time, id). */
    private data class Position(val seconds: Long, val nanos: Int, val id: String) : Comparable<Position> {
        override fun compareTo(other: Position): Int = compareValuesBy(this, other, { it.seconds }, { it.nanos }, { it.id })
    }

    private fun Map.Entry<String, Document>.position() = Position(value.seconds, value.nanos, key)

    private fun CloudCursor.position() = Position(seconds, nanos, documentId)
}

/** [CloudBookkeepingStore] in memory. */
class InMemoryCloudBookkeepingStore(var values: CloudBookkeeping = CloudBookkeeping()) : CloudBookkeepingStore {
    override suspend fun get(): CloudBookkeeping = values

    override suspend fun update(transform: (CloudBookkeeping) -> CloudBookkeeping) {
        values = transform(values)
    }
}
