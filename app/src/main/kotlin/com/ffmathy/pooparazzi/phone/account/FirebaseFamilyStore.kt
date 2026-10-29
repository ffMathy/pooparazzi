package com.ffmathy.pooparazzi.phone.account

import com.ffmathy.pooparazzi.core.cloud.CloudCursor
import com.ffmathy.pooparazzi.core.cloud.CloudPage
import com.ffmathy.pooparazzi.core.model.Entry
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import java.util.Date

/**
 * [FamilyStore] in Cloud Firestore, in exactly the shapes `firebase/firestore.rules` accepts (BABIES_AND_BACKUP §4.1).
 * Reads go to the server only. Writes share [backup]'s commit gate, so a family change never overtakes an upload still
 * on its way, and every call gives up after the backup's timeout as offline.
 */
class FirebaseFamilyStore(
    private val firestore: FirebaseFirestore,
    private val backup: FirestoreCloudStore,
    private val timeoutMillis: Long = FirestoreCloudStore.TIMEOUT_MILLIS,
) : FamilyStore {

    override suspend fun familyOf(uid: String): String? = backup.call {
        val doc = user(uid).get(Source.SERVER).awaitWithin(timeoutMillis)
        doc.getString(FIELD_FAMILY_ID)?.takeIf { doc.exists() && it.isNotBlank() }
    }

    override suspend fun isMember(familyId: String, uid: String): Boolean = backup.call {
        try {
            member(familyId, uid).get(Source.SERVER).awaitWithin(timeoutMillis).exists()
        } catch (e: FirebaseFirestoreException) {
            // The rules refuse to read a family's member documents to anyone outside it.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) false else throw e
        }
    }

    override suspend fun createFamily(familyId: String, uid: String, email: String, leaving: String?) = commit {
        leaving?.let { delete(member(it, uid)) }
        set(family(familyId), mapOf(FIELD_CREATED_BY to uid, FIELD_CREATED_AT to FieldValue.serverTimestamp()))
        set(member(familyId, uid), mapOf(FIELD_EMAIL to email, FIELD_JOINED_AT to FieldValue.serverTimestamp()))
        set(user(uid), mapOf(FIELD_FAMILY_ID to familyId))
    }

    override suspend fun creatorOf(familyId: String): String = backup.call {
        family(familyId).get(Source.SERVER).awaitWithin(timeoutMillis).getString(FIELD_CREATED_BY).orEmpty()
    }

    override suspend fun members(familyId: String): List<StoredMember> = backup.call {
        family(familyId).collection(FirestoreCloudStore.COLLECTION_MEMBERS).get(Source.SERVER).awaitWithin(timeoutMillis)
            .documents.map { doc ->
                StoredMember(doc.id, doc.getString(FIELD_EMAIL).orEmpty(), doc.getTimestamp(FIELD_JOINED_AT)?.toDate()?.time ?: 0)
            }
    }

    override suspend fun createInvite(code: String, invite: StoredInvite) = backup.call {
        val data = buildMap {
            put(FIELD_FAMILY_ID, invite.familyId)
            put(FIELD_CREATED_BY, invite.createdBy)
            put(FIELD_EXPIRES_AT, Timestamp(Date(invite.expiresAtMillis)))
            invite.email?.let { put(FIELD_EMAIL, it) }
        }
        backup.commits.commit { inviteRef(code).set(data) }
        Unit
    }

    override suspend fun invite(code: String): StoredInvite? = backup.call {
        val doc = inviteRef(code).get(Source.SERVER).awaitWithin(timeoutMillis)
        if (!doc.exists()) return@call null
        StoredInvite(
            familyId = doc.getString(FIELD_FAMILY_ID) ?: return@call null,
            createdBy = doc.getString(FIELD_CREATED_BY).orEmpty(),
            expiresAtMillis = doc.getTimestamp(FIELD_EXPIRES_AT)?.toDate()?.time ?: 0,
            email = doc.getString(FIELD_EMAIL),
        )
    }

    override suspend fun join(code: String, familyId: String, uid: String, email: String) = commit {
        set(
            member(familyId, uid),
            mapOf(FIELD_EMAIL to email, FIELD_JOINED_AT to FieldValue.serverTimestamp(), FIELD_INVITE to code),
        )
        set(user(uid), mapOf(FIELD_FAMILY_ID to familyId))
        delete(inviteRef(code))
    }

    override suspend fun removeMember(familyId: String, uid: String) = backup.call {
        backup.commits.commit { member(familyId, uid).delete() }
        Unit
    }

    override suspend fun leave(familyId: String, uid: String) = backup.deleteAll(uid, familyId)

    override suspend fun deleteUser(uid: String) = backup.call {
        backup.commits.commit { user(uid).delete() }
        Unit
    }

    override suspend fun legacyEntries(uid: String, cursor: CloudCursor?, limit: Int): CloudPage<Entry> =
        FirestoreCloudStore.pageOf(backup.read(user(uid).collection(FirestoreCloudStore.COLLECTION_ENTRIES), cursor, limit))

    override suspend fun deleteLegacyEntries(uid: String) = backup.call {
        backup.deleteCollection(user(uid).collection(FirestoreCloudStore.COLLECTION_ENTRIES))
    }

    /** One batch, through the backup's commit gate. */
    private suspend fun commit(build: WriteBatch.() -> Unit) = backup.call {
        backup.commits.commit<Void> { firestore.batch().apply(build).commit() }
        Unit
    }

    private fun user(uid: String): DocumentReference = firestore.collection(COLLECTION_USERS).document(uid)
    private fun family(familyId: String): DocumentReference =
        firestore.collection(FirestoreCloudStore.COLLECTION_FAMILIES).document(familyId)
    private fun member(familyId: String, uid: String): DocumentReference =
        family(familyId).collection(FirestoreCloudStore.COLLECTION_MEMBERS).document(uid)
    private fun inviteRef(code: String): DocumentReference = firestore.collection(COLLECTION_INVITES).document(code)

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_INVITES = "invites"
        const val FIELD_FAMILY_ID = "familyId"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_EMAIL = "email"
        const val FIELD_JOINED_AT = "joinedAt"
        const val FIELD_INVITE = "invite"
        const val FIELD_EXPIRES_AT = "expiresAt"
    }
}
