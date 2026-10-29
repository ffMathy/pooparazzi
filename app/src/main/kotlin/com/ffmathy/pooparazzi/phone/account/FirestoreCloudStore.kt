package com.ffmathy.pooparazzi.phone.account

import com.ffmathy.pooparazzi.core.cloud.CloudCursor
import com.ffmathy.pooparazzi.core.cloud.CloudError
import com.ffmathy.pooparazzi.core.cloud.CloudException
import com.ffmathy.pooparazzi.core.cloud.CloudPage
import com.ffmathy.pooparazzi.core.cloud.CloudStore
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.BabyJson
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.EntryJson
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.IOException
import java.util.concurrent.Executor

/**
 * The family's backup in Cloud Firestore (BABIES_AND_BACKUP §4.1, FAMILIES): `families/{fid}/entries/{entryId}` and
 * `families/{fid}/babies/{babyId}`, each holding exactly `{ json, syncedAt }` (the entry's [EntryJson] or the baby's
 * [BabyJson], and the server time of the write), as `firebase/firestore.rules` requires. No settings are ever written.
 *
 * Reads go to the server only ([Source.SERVER]): the backup decides what is new by the stored cursor, not by a cache.
 * Firestore holds a write until the server confirms it, which offline (or with the day's write quota used up) means
 * for a long time: every call stops waiting after [timeoutMillis] as [CloudError.OFFLINE], so the backup's rows stay
 * pending for the next sync. Stopping to wait does not cancel the write: Firestore keeps the batch queued (in memory)
 * and sends it when it can. So at most one commit is in flight ([CommitGate]): the next upload, and deleting the
 * backup, first wait for the outstanding one (again within [timeoutMillis]) instead of queueing the same rows again.
 */
class FirestoreCloudStore(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : CloudStore {
    internal val commits = CommitGate(timeoutMillis)

    override suspend fun upload(familyId: String, entries: List<Entry>) =
        write(collection(familyId, COLLECTION_ENTRIES), entries.map { it.id to EntryJson.encode(it) })

    override suspend fun changesAfter(familyId: String, cursor: CloudCursor?, limit: Int): CloudPage<Entry> =
        pageOf(read(collection(familyId, COLLECTION_ENTRIES), cursor, limit))

    override suspend fun uploadBabies(familyId: String, babies: List<Baby>) =
        write(collection(familyId, COLLECTION_BABIES), babies.map { it.id to BabyJson.encode(it) })

    override suspend fun babiesAfter(familyId: String, cursor: CloudCursor?, limit: Int): CloudPage<Baby> =
        babyPageOf(read(collection(familyId, COLLECTION_BABIES), cursor, limit))

    override suspend fun deleteAll(uid: String, familyId: String) = call {
        // An upload still queued must not land after the deletion.
        commits.drain()
        val family = firestore.collection(COLLECTION_FAMILIES).document(familyId)
        val member = family.collection(COLLECTION_MEMBERS).document(uid)
        // Read while still a member (the rules let members alone read): the last one out takes the family's data along.
        val members = try {
            family.collection(COLLECTION_MEMBERS).limit(2).get(Source.SERVER).awaitWithin()
        } catch (e: FirebaseFirestoreException) {
            // Refused while signed in: no longer a member (an earlier attempt got this far, or removed). Nothing to do.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && auth.currentUser != null) return@call
            throw e
        }
        if (members.documents.all { it.id == uid }) {
            deleteCollection(family.collection(COLLECTION_ENTRIES))
            deleteCollection(family.collection(COLLECTION_BABIES))
            // The family goes in one batch with its last member's document, as the rules require.
            commits.commit {
                firestore.batch().apply {
                    delete(family)
                    delete(member)
                }.commit()
            }
        } else {
            commits.commit { member.delete() }
        }
        Unit
    }

    private fun collection(familyId: String, name: String): CollectionReference =
        firestore.collection(COLLECTION_FAMILIES).document(familyId).collection(name)

    /** Writes `{ json, syncedAt }` for each (id, json) in one batch (at most [CloudStore.UPLOAD_BATCH]). */
    private suspend fun write(collection: CollectionReference, documents: List<Pair<String, String>>) {
        if (documents.isEmpty()) return
        require(documents.size <= CloudStore.UPLOAD_BATCH) { "At most ${CloudStore.UPLOAD_BATCH} documents per upload" }
        call {
            val batch = firestore.batch()
            documents.forEach { (id, json) ->
                batch.set(collection.document(id), mapOf(FIELD_JSON to json, FIELD_SYNCED_AT to FieldValue.serverTimestamp()))
            }
            commits.commit { batch.commit() }
        }
    }

    /** Up to [limit] documents of [collection] written after [cursor], in the order of changes. */
    internal suspend fun read(collection: CollectionReference, cursor: CloudCursor?, limit: Int): List<StoredDocument> = call {
        var query: Query = collection.orderBy(FIELD_SYNCED_AT).orderBy(FieldPath.documentId())
        if (cursor != null) query = query.startAfter(Timestamp(cursor.seconds, cursor.nanos), cursor.documentId)
        val snapshot = query.limit(limit.toLong()).get(Source.SERVER).awaitWithin()
        snapshot.documents.map { doc ->
            // A pending server timestamp reads as null (ServerTimestampBehavior.NONE).
            val syncedAt = doc.get(FIELD_SYNCED_AT) as? Timestamp
            StoredDocument(doc.id, doc.get(FIELD_JSON) as? String, syncedAt?.let { Stamp(it.seconds, it.nanoseconds) })
        }
    }

    /** Deletes every document of [collection], a page at a time. */
    internal suspend fun deleteCollection(collection: CollectionReference) {
        while (true) {
            val page = collection.limit(DELETE_PAGE.toLong()).get(Source.SERVER).awaitWithin()
            if (page.isEmpty) break
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            commits.commit { batch.commit() }
        }
    }

    private suspend fun <T> Task<T>.awaitWithin(): T = awaitWithin(timeoutMillis)

    /** Runs a Firestore call, turning its failure into a [CloudException]. */
    internal suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: CloudException) {
        throw e
    } catch (e: Exception) {
        throw CloudException(errorOf(e, signedIn = { auth.currentUser != null }), e)
    }

    /** A document as [pageOf] needs it: its id, its `json` (null when not a string) and its `syncedAt`. */
    internal class StoredDocument(val id: String, val json: String?, val syncedAt: Stamp?)

    /** A server time, as `syncedAt` holds it. */
    internal class Stamp(val seconds: Long, val nanos: Int)

    /**
     * At most one commit in flight. A commit that timed out keeps going in Firestore; while it has not finished, the
     * next [commit] (or [drain]) waits for it, at most [timeoutMillis] and then as [CloudError.OFFLINE], instead of
     * starting another. Its outcome was its own caller's to hear: only its end matters here.
     */
    internal class CommitGate(private val timeoutMillis: Long) {
        private val mutex = Mutex()
        private var outstanding: Task<*>? = null

        /** Starts a commit with [start] once the one before has finished, and waits for it (within the timeout). */
        suspend fun <T> commit(start: () -> Task<T>): T = mutex.withLock {
            settle()
            val task = start()
            outstanding = task
            task.awaitWithin(timeoutMillis)
        }

        /** Waits for the outstanding commit, if any, within the timeout. */
        suspend fun drain() = mutex.withLock { settle() }

        private suspend fun settle() {
            val task = outstanding ?: return
            if (!task.isComplete) {
                val ended = withTimeoutOrNull(timeoutMillis) {
                    suspendCancellableCoroutine { done -> task.addOnCompleteListener(Executor { it.run() }) { done.resume(Unit) } }
                }
                if (ended == null) throw CloudException(CloudError.OFFLINE)
            }
            outstanding = null
        }
    }

    companion object {
        const val COLLECTION_FAMILIES = "families"
        const val COLLECTION_MEMBERS = "members"
        const val COLLECTION_ENTRIES = "entries"
        const val COLLECTION_BABIES = "babies"
        const val FIELD_JSON = "json"
        const val FIELD_SYNCED_AT = "syncedAt"

        /** How long one call may take before it counts as offline. */
        const val TIMEOUT_MILLIS = 30_000L

        /** Documents deleted per batch when the account is deleted. */
        internal const val DELETE_PAGE = 400

        /** What a failed call means for the backup (timeouts are [CloudError.OFFLINE] already, in awaitWithin). */
        internal fun errorOf(e: Exception, signedIn: () -> Boolean): CloudError = when (e) {
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CloudError.OFFLINE
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> CloudError.SIGNED_OUT
                // The rules refuse everything while signed out: say so rather than "couldn't back up".
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> if (signedIn()) CloudError.OTHER else CloudError.SIGNED_OUT
                else -> CloudError.OTHER
            }
            is FirebaseNetworkException, is IOException -> CloudError.OFFLINE
            else -> CloudError.OTHER
        }

        /**
         * One page of [changesAfter] from its documents in query order. A document without `syncedAt` is a write of
         * this phone the server has not stamped yet (it shows up stamped later): skipped, and the cursor stops before
         * it so nothing after it is passed over. A document that does not decode, or whose entry id is not its
         * document id, is skipped but still moves the cursor on. [CloudPage.documents] counts every document.
         */
        internal fun pageOf(documents: List<StoredDocument>): CloudPage<Entry> =
            pageOf(documents, EntryJson::decodeOrNull) { it.id }

        /** [pageOf] for the family's babies. */
        internal fun babyPageOf(documents: List<StoredDocument>): CloudPage<Baby> =
            pageOf(documents, BabyJson::decodeOrNull) { it.id }

        private fun <T> pageOf(documents: List<StoredDocument>, decode: (String) -> T?, idOf: (T) -> String): CloudPage<T> {
            val items = mutableListOf<T>()
            var last: CloudCursor? = null
            var pending = false
            for (doc in documents) {
                val at = doc.syncedAt
                if (at == null) {
                    pending = true
                    continue
                }
                if (!pending) last = CloudCursor(at.seconds, at.nanos, doc.id)
                doc.json?.let(decode)?.takeIf { idOf(it) == doc.id }?.let(items::add)
            }
            return CloudPage(items, last, documents.size)
        }
    }
}

/** Awaits the task for at most [timeoutMillis], then gives up as [CloudError.OFFLINE] (the task itself goes on). */
internal suspend fun <T> Task<T>.awaitWithin(timeoutMillis: Long): T {
    // A Task<Void> completes with null: the timeout is told apart by the wrapper, not by the value.
    val done = withTimeoutOrNull(timeoutMillis) { Box(await()) } ?: throw CloudException(CloudError.OFFLINE)
    return done.value
}

private class Box<T>(val value: T)
