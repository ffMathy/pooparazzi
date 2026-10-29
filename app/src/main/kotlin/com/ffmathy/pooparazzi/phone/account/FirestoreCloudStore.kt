package com.ffmathy.pooparazzi.phone.account

import com.ffmathy.pooparazzi.core.cloud.CloudCursor
import com.ffmathy.pooparazzi.core.cloud.CloudError
import com.ffmathy.pooparazzi.core.cloud.CloudException
import com.ffmathy.pooparazzi.core.cloud.CloudPage
import com.ffmathy.pooparazzi.core.cloud.CloudStore
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * The backup in Cloud Firestore (BABIES_AND_BACKUP §4.1): `users/{uid}/entries/{entryId}` holding exactly
 * `{ json, syncedAt }`, the entry's [EntryJson] and the server time of the write, as `firebase/firestore.rules`
 * requires. Nothing else is ever written: no settings, no babies, no names.
 *
 * Reads go to the server only ([Source.SERVER]): the backup decides what is new by the stored cursor, not by a cache.
 * Firestore holds a write until the server confirms it, which offline means for ever: every call gives up after
 * [timeoutMillis] as [CloudError.OFFLINE], so the backup's rows stay pending for the next sync.
 */
class FirestoreCloudStore(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : CloudStore {

    override suspend fun upload(uid: String, entries: List<Entry>) {
        if (entries.isEmpty()) return
        require(entries.size <= CloudStore.UPLOAD_BATCH) { "At most ${CloudStore.UPLOAD_BATCH} entries per upload" }
        call {
            val collection = entries(uid)
            val batch = firestore.batch()
            entries.forEach { entry ->
                val data = mapOf(FIELD_JSON to EntryJson.encode(entry), FIELD_SYNCED_AT to FieldValue.serverTimestamp())
                batch.set(collection.document(entry.id), data)
            }
            batch.commit().awaitWithin()
        }
    }

    override suspend fun changesAfter(uid: String, cursor: CloudCursor?, limit: Int): CloudPage = call {
        var query: Query = entries(uid).orderBy(FIELD_SYNCED_AT).orderBy(FieldPath.documentId())
        if (cursor != null) query = query.startAfter(Timestamp(cursor.seconds, cursor.nanos), cursor.documentId)
        val snapshot = query.limit(limit.toLong()).get(Source.SERVER).awaitWithin()
        pageOf(
            snapshot.documents.map { doc ->
                // A pending server timestamp reads as null (ServerTimestampBehavior.NONE).
                val syncedAt = doc.get(FIELD_SYNCED_AT) as? Timestamp
                StoredDocument(doc.id, doc.get(FIELD_JSON) as? String, syncedAt?.let { Stamp(it.seconds, it.nanoseconds) })
            },
        )
    }

    override suspend fun deleteAll(uid: String) = call {
        val collection = entries(uid)
        while (true) {
            val page = collection.limit(DELETE_PAGE.toLong()).get(Source.SERVER).awaitWithin()
            if (page.isEmpty) break
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().awaitWithin()
        }
    }

    private fun entries(uid: String): CollectionReference =
        firestore.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_ENTRIES)

    private suspend fun <T> Task<T>.awaitWithin(): T {
        // A Task<Void> completes with null: the timeout is told apart by the wrapper, not by the value.
        val done = withTimeoutOrNull(timeoutMillis) { Box(await()) } ?: throw CloudException(CloudError.OFFLINE)
        return done.value
    }

    private class Box<T>(val value: T)

    /** Runs a Firestore call, turning its failure into a [CloudException]. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
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

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_ENTRIES = "entries"
        const val FIELD_JSON = "json"
        const val FIELD_SYNCED_AT = "syncedAt"

        /** How long one call may take before it counts as offline. */
        const val TIMEOUT_MILLIS = 30_000L

        /** Documents deleted per batch when the account is deleted. */
        private const val DELETE_PAGE = 400

        /**
         * One page of [changesAfter] from its documents in query order. A document without `syncedAt` is a write of
         * this phone the server has not stamped yet (it shows up stamped later): skipped, and the cursor stops before
         * it so nothing after it is passed over. A document that does not decode, or whose entry id is not its
         * document id, is skipped but still moves the cursor on. [CloudPage.documents] counts every document.
         */
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

        internal fun pageOf(documents: List<StoredDocument>): CloudPage {
            val entries = mutableListOf<Entry>()
            var last: CloudCursor? = null
            var pending = false
            for (doc in documents) {
                val at = doc.syncedAt
                if (at == null) {
                    pending = true
                    continue
                }
                if (!pending) last = CloudCursor(at.seconds, at.nanos, doc.id)
                doc.json?.let(EntryJson::decodeOrNull)?.takeIf { it.id == doc.id }?.let(entries::add)
            }
            return CloudPage(entries, last, documents.size)
        }
    }
}
