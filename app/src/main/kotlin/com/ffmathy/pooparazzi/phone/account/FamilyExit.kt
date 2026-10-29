package com.ffmathy.pooparazzi.phone.account

/**
 * The Firestore calls that taking an account out of its family needs ([FamilyExit]), one read or batch each. Calls
 * throw [com.ffmathy.pooparazzi.core.cloud.CloudException] on failure.
 */
interface ExitStore {
    /**
     * The family's members, or null when the account may not read them any more: it is not a member (it was removed,
     * or an earlier attempt already took it out).
     */
    suspend fun membersOrNull(familyId: String): List<StoredMember>?

    /** `families/{familyId}.createdBy`. */
    suspend fun creatorOf(familyId: String): String

    /** Deletes one page of the family's [collection] (`entries` or `babies`); returns how many were deleted (0: none left). */
    suspend fun deletePage(familyId: String, collection: String): Int

    /**
     * One batch: [uid]'s member document deleted and, with [handOverTo], `createdBy` of the family set to that member
     * (the rules let only the current creator do that, and only to a member).
     */
    suspend fun leave(familyId: String, uid: String, handOverTo: String?)
}

/**
 * Takes an account out of its family (deleting the account, or leaving an old family after joining another,
 * BABIES_AND_BACKUP §4.1). The family document itself is never deleted (the rules refuse it): it stays as a tombstone,
 * so its id can never be created again by anyone.
 *
 * - **Others remain:** only the account's member document goes. When the account is the family's creator, the family
 *   is handed over in the same batch to the member who joined first ([successor]), so someone can still remove members.
 * - **The last member:** the family's entries and babies are deleted, a page at a time, then the member document. The
 *   rules cannot count members, and someone may join with an invite while this runs, so the member list is read again
 *   before every page and before the member document goes: as soon as anyone else is a member, the wipe stops and the
 *   account just leaves (handing the family over), and the newcomer keeps what is left.
 */
class FamilyExit(private val store: ExitStore) {

    suspend fun leave(familyId: String, uid: String) {
        // Not (or no longer) a member: nothing to do.
        val members = store.membersOrNull(familyId)?.takeIf { list -> list.any { it.uid == uid } } ?: return
        if (members.any { it.uid != uid }) return leaveTo(familyId, uid, members)
        for (collection in WIPED) {
            while (true) {
                if (othersJoined(familyId, uid)) return
                if (store.deletePage(familyId, collection) == 0) break
            }
        }
        if (othersJoined(familyId, uid)) return
        store.leave(familyId, uid, handOverTo = null)
    }

    /** Reads the members again; when anyone else is in the family now, leaves it to them and returns true. */
    private suspend fun othersJoined(familyId: String, uid: String): Boolean {
        val now = store.membersOrNull(familyId) ?: return true
        if (now.none { it.uid == uid }) return true
        if (now.none { it.uid != uid }) return false
        leaveTo(familyId, uid, now)
        return true
    }

    private suspend fun leaveTo(familyId: String, uid: String, members: List<StoredMember>) {
        val handOverTo = if (store.creatorOf(familyId) == uid) successor(uid, members) else null
        store.leave(familyId, uid, handOverTo)
    }

    companion object {
        /** What the last member deletes, in this order. */
        val WIPED = listOf(FirestoreCloudStore.COLLECTION_ENTRIES, FirestoreCloudStore.COLLECTION_BABIES)

        /** Who takes the family over from [uid]: the member who joined first (the longest-standing), if any. */
        fun successor(uid: String, members: List<StoredMember>): String? =
            members.filter { it.uid != uid }.minWithOrNull(compareBy<StoredMember>({ it.joinedAtMillis }, { it.uid }))?.uid
    }
}
