package com.ffmathy.pooparazzi.phone.account

import com.ffmathy.pooparazzi.core.cloud.CloudCursor
import com.ffmathy.pooparazzi.core.cloud.CloudPage
import com.ffmathy.pooparazzi.core.model.Entry

/** A family's member document, `families/{fid}/members/{uid}`: `{ email, joinedAt, invite? }`. */
data class StoredMember(val uid: String, val email: String, val joinedAtMillis: Long)

/** An invite, `invites/{code}`: `{ familyId, createdBy, expiresAt, email? }`. */
data class StoredInvite(val familyId: String, val createdBy: String, val expiresAtMillis: Long, val email: String?)

/**
 * The family documents in Cloud Firestore (BABIES_AND_BACKUP §4.1, `firebase/firestore.rules`): one call per read or
 * batch, nothing decided here ([FamilyService] does that). Every call throws
 * [com.ffmathy.pooparazzi.core.cloud.CloudException] on failure, and gives up after the store's timeout as
 * [com.ffmathy.pooparazzi.core.cloud.CloudError.OFFLINE].
 */
interface FamilyStore {
    /** `users/{uid}.familyId`, or null when the account has no users document yet. */
    suspend fun familyOf(uid: String): String?

    /**
     * Whether [uid] is a member of [familyId]: its member document exists. False also when the rules refuse to read it,
     * which they do exactly when the account is not a member (it left, or was removed).
     */
    suspend fun isMember(familyId: String, uid: String): Boolean

    /**
     * One batch: `families/{familyId}` (`createdBy` = [uid], `createdAt` = server time), [uid]'s member document
     * ([email], `joinedAt` = server time) and `users/{uid}` pointing at it. With [leaving], [uid]'s member document in
     * that family is deleted in the same batch (leaving a family for a new one of their own), and with [handOverTo]
     * that family's `createdBy` is set to that member (the creator leaving).
     */
    suspend fun createFamily(familyId: String, uid: String, email: String, leaving: String? = null, handOverTo: String? = null)

    /** `families/{familyId}.createdBy`. */
    suspend fun creatorOf(familyId: String): String

    /** The family's members, in no particular order. */
    suspend fun members(familyId: String): List<StoredMember>

    /** Writes `invites/{code}`. */
    suspend fun createInvite(code: String, invite: StoredInvite)

    /** Deletes `invites/{code}` (its maker taking back an invite). */
    suspend fun deleteInvite(code: String)

    /** `invites/{code}`, or null when there is none (never made, used up or deleted). */
    suspend fun invite(code: String): StoredInvite?

    /**
     * One batch: [uid]'s member document in [familyId] naming the invite [code], `users/{uid}` pointing at it, and the
     * invite deleted.
     */
    suspend fun join(code: String, familyId: String, uid: String, email: String)

    /** Deletes [uid]'s member document in [familyId] (the family's creator removing a member). */
    suspend fun removeMember(familyId: String, uid: String)

    /**
     * Takes [uid] out of [familyId] ([FamilyExit]): deletes its member document (handing the family over when [uid]
     * created it and others remain), and when it was the last member first the family's entries and babies. The family
     * document stays. Nothing happens when [uid] is not a member any more.
     */
    suspend fun leave(familyId: String, uid: String)

    /** Deletes `users/{uid}` (account deletion). */
    suspend fun deleteUser(uid: String)

    /** A page of the backup from before families, `users/{uid}/entries`, after [cursor]. */
    suspend fun legacyEntries(uid: String, cursor: CloudCursor?, limit: Int): CloudPage<Entry>

    /** Deletes every document of `users/{uid}/entries` (also when deleting the account). */
    suspend fun deleteLegacyEntries(uid: String)
}
