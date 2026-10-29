package com.ffmathy.pooparazzi.core.cloud

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BabyJson
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryJson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An in-memory backend shaped like Firestore's `families/{fid}/entries` and `families/{fid}/babies`: one document per
 * entry or baby holding its JSON and the server time of its last write. The server time only moves forward (one clock
 * for both collections), and every document of one upload (a batch) shares it, as Firestore's commit time does. Pages
 * are ordered by (time, document id), like the app's query. Members are only what tests [addMember]; [deleteAll]
 * removes one, and the family's data with the last.
 */
class FakeCloudStore : CloudStore {
    /** One stored document. */
    class Document(val json: String, val seconds: Long, val nanos: Int)

    private class Family {
        val entries = mutableMapOf<String, Document>()
        val babies = mutableMapOf<String, Document>()
        val members = mutableSetOf<String>()
    }

    private val families = mutableMapOf<String, Family>()
    private var seconds = 1_700_000_000L
    private var nanos = 0

    /** While set, every call fails with this error. */
    var failure: CloudError? = null

    /** While set, uploads (entries and babies) fail with this error, as a refused write does. */
    var uploadFailure: CloudError? = null

    /** While set, this many more [changesAfter] calls succeed, and the ones after fail with [CloudError.OFFLINE]. */
    var pullsBeforeFailure: Int? = null

    /** How long every call takes (virtual time in tests). */
    var latencyMillis = 0L

    /** When true, the server time stands still, so every write shares one time (the cursor's tie-break on ids). */
    var frozenTime = false

    /** Every entries [upload] call that was accepted, with the ids it wrote. */
    val uploads = mutableListOf<List<String>>()

    /** Every [uploadBabies] call that was accepted, with the babies it wrote. */
    val babyUploads = mutableListOf<List<Baby>>()

    /** Every [deleteAll] call that was accepted, as (uid, familyId). */
    val deletions = mutableListOf<Pair<String, String>>()

    /** How many entry pulls ([changesAfter]) were made, and how many documents they returned. */
    var pulls = 0
        private set
    var documentsRead = 0
        private set

    /** How many baby pulls ([babiesAfter]) were made. */
    var babyPulls = 0
        private set

    /** Total entries written by accepted uploads. */
    val uploadedCount: Int get() = uploads.sumOf { it.size }

    /** The family's log, decoded. */
    fun entries(familyId: String): Map<String, Entry> =
        families[familyId]?.entries.orEmpty().mapValues { EntryJson.decode(it.value.json) }

    /** The family's babies, decoded. */
    fun babies(familyId: String): Map<String, Baby> =
        families[familyId]?.babies.orEmpty().mapValues { checkNotNull(BabyJson.decodeOrNull(it.value.json)) }

    /** Who is in the family. */
    fun members(familyId: String): Set<String> = families[familyId]?.members.orEmpty()

    fun addMember(familyId: String, uid: String) {
        family(familyId).members += uid
    }

    /** Stores a raw entry document (e.g. one this version cannot decode), as a write at the next server time. */
    fun putRaw(familyId: String, id: String, json: String) {
        tick()
        family(familyId).entries[id] = Document(json, seconds, nanos)
    }

    /** Stores a raw baby document, as a write at the next server time. */
    fun putRawBaby(familyId: String, id: String, json: String) {
        tick()
        family(familyId).babies[id] = Document(json, seconds, nanos)
    }

    override suspend fun upload(familyId: String, entries: List<Entry>) {
        check(entries.size <= CloudStore.UPLOAD_BATCH) { "A batch of ${entries.size}" }
        fail()
        uploadFailure?.let { throw CloudException(it) }
        tick()
        val docs = family(familyId).entries
        for (entry in entries) docs[entry.id] = Document(EntryJson.encode(entry), seconds, nanos)
        uploads += entries.map { it.id }
    }

    override suspend fun changesAfter(familyId: String, cursor: CloudCursor?, limit: Int): CloudPage<Entry> {
        fail()
        pullsBeforeFailure?.let { left ->
            if (left <= 0) throw CloudException(CloudError.OFFLINE)
            pullsBeforeFailure = left - 1
        }
        pulls++
        val page = page(families[familyId]?.entries.orEmpty(), cursor, limit)
        documentsRead += page.size
        return CloudPage(page.mapNotNull { EntryJson.decodeOrNull(it.value.json) }, page.cursor(), page.size)
    }

    override suspend fun uploadBabies(familyId: String, babies: List<Baby>) {
        check(babies.size <= CloudStore.UPLOAD_BATCH) { "A batch of ${babies.size}" }
        fail()
        uploadFailure?.let { throw CloudException(it) }
        tick()
        val docs = family(familyId).babies
        for (baby in babies) docs[baby.id] = Document(BabyJson.encode(baby), seconds, nanos)
        babyUploads += babies
    }

    override suspend fun babiesAfter(familyId: String, cursor: CloudCursor?, limit: Int): CloudPage<Baby> {
        fail()
        babyPulls++
        val page = page(families[familyId]?.babies.orEmpty(), cursor, limit)
        return CloudPage(page.mapNotNull { BabyJson.decodeOrNull(it.value.json) }, page.cursor(), page.size)
    }

    override suspend fun deleteAll(uid: String, familyId: String) {
        fail()
        val family = families[familyId] ?: return
        family.members -= uid
        if (family.members.isEmpty()) families.remove(familyId)
        deletions += uid to familyId
    }

    private fun family(familyId: String) = families.getOrPut(familyId) { Family() }

    private fun page(docs: Map<String, Document>, cursor: CloudCursor?, limit: Int): List<Map.Entry<String, Document>> {
        val ordered = docs.entries.sortedBy { it.position() }
        val after = if (cursor == null) ordered else ordered.filter { it.position() > cursor.position() }
        return after.take(limit)
    }

    private fun List<Map.Entry<String, Document>>.cursor(): CloudCursor? =
        lastOrNull()?.let { CloudCursor(it.value.seconds, it.value.nanos, it.key) }

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

/** A Preferences [DataStore] in memory, running on the caller's coroutine (virtual time in tests). */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = mutex.withLock {
        // Frozen: the edit's MutablePreferences must not change what was stored after the fact.
        val next = transform(state.value).toPreferences()
        state.value = next
        next
    }
}
