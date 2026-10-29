package com.ffmathy.pooparazzi.core.cloud

import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * The signed-in account as the backup needs it (FAMILIES). What is backed up is the account's family, not the account:
 * the log and the babies live under [familyId], shared by every member.
 *
 * @property familyId the account's current family (`users/{uid}.familyId`), or null while the app does not know it yet
 *   (just signed in, a family still being created or joined): nothing is synced then, and the rows stay pending. A new
 *   [familyId] (joining or leaving a family) counts as a new backup, like another account: everything on the phone is
 *   flagged for it and pulled from its start.
 */
data class CloudAccount(val uid: String, val email: String, val familyId: String?)

/** Where the last pull stopped: the (server write time, document id) of the last document seen. */
@Serializable
data class CloudCursor(val seconds: Long, val nanos: Int, val documentId: String)

/**
 * One page of changes: the decoded [items] (entries or babies; undecodable documents skipped), the cursor of the page's
 * last document, and how many documents the page had (a page shorter than the limit is the last).
 */
class CloudPage<out T>(val items: List<T>, val last: CloudCursor?, val documents: Int)

/** Why a backend call failed, as the backup reports it ([CloudStatus]). */
enum class CloudError {
    /** No connection (or the backend is unreachable); the entries stay pending and go later. */
    OFFLINE,

    /** Nobody is signed in any more (signed out, or the session expired). */
    SIGNED_OUT,

    /** Anything else, such as a refused write or a used-up daily quota. */
    OTHER,
}

/** A failed [CloudStore] call. */
class CloudException(val error: CloudError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * The family's copy of the log and the babies (Firestore in the phone app, FAMILIES): one document per entry under
 * `families/{fid}/entries`, holding its [com.ffmathy.pooparazzi.core.model.EntryJson], and one per baby under
 * `families/{fid}/babies`, holding its [com.ffmathy.pooparazzi.core.model.BabyJson]; each with the server time of its
 * last write (`syncedAt`), by which changes are paged. Calls throw [CloudException].
 */
interface CloudStore {
    /** Writes the current version of [entries] (at most [UPLOAD_BATCH]), all or nothing, stamped with the server time. */
    suspend fun upload(familyId: String, entries: List<Entry>)

    /** Up to [limit] entry documents written after [cursor] (null: from the start), oldest change first. */
    suspend fun changesAfter(familyId: String, cursor: CloudCursor?, limit: Int): CloudPage<Entry>

    /** Writes the current version of [babies] (at most [UPLOAD_BATCH]), all or nothing, stamped with the server time. */
    suspend fun uploadBabies(familyId: String, babies: List<Baby>)

    /** Up to [limit] baby documents written after [cursor] (null: from the start), oldest change first. */
    suspend fun babiesAfter(familyId: String, cursor: CloudCursor?, limit: Int): CloudPage<Baby>

    /**
     * Takes account [uid] out of the backup before the account is deleted: removes its membership of [familyId], and
     * when it was the family's last member deletes the family's entries, babies and the family itself.
     */
    suspend fun deleteAll(uid: String, familyId: String)

    companion object {
        /** The most entries (or babies) one upload takes (Firestore allows 500 writes per batch). */
        const val UPLOAD_BATCH = 400

        /** How many documents a pull asks for per [changesAfter] or [babiesAfter]. */
        const val PAGE_SIZE = 500
    }
}

/**
 * The babies' side of the backup on this phone (the settings, BABIES_AND_BACKUP §2): like the entries' `cloudPending`,
 * each baby carries a flag "not yet in the family's backup", kept on the phone only (never sent to the watch). Babies
 * are flagged when added or renamed here (or taken from the watch) while an account is signed in, and cleared once
 * uploaded unchanged. Pulled babies merge last-writer-wins per baby ([Baby.winsOver]) and reach the watch with the
 * settings.
 */
interface CloudBabies {
    /** The babies waiting to go up, in their current version (every change to one emits again). */
    fun observeCloudPendingBabies(): Flow<List<Baby>>

    /** The babies waiting to go up, in their current version. */
    suspend fun cloudPendingBabies(): List<Baby>

    /** Clears the flag of each of [babies] still exactly as uploaded; returns how many were cleared. */
    suspend fun markBabiesCloudSynced(babies: List<Baby>): Int

    /** Flags every baby (the first sync for a family: "every baby so far is backed up"). */
    suspend fun markAllBabiesForCloud()

    /** Clears every flag (signed out: nothing is pending for any backend). */
    suspend fun clearBabiesCloudPending()

    /**
     * Merges babies from the family's backup: unknown babies are added, a version that wins replaces the stored one
     * and clears its flag, an identical one clears it, and a stored version that wins stays (or becomes) flagged so it
     * goes up. Returns how many babies changed here.
     */
    suspend fun applyCloudBabies(babies: List<Baby>): Int
}

/**
 * What the phone app hands to `Core.init`: the store and the signed-in account with its family (null when signed
 * out). The app keeps [account] current: [CloudAccount.familyId] as `users/{uid}` says, changed when the parent joins or
 * leaves a family.
 */
class CloudBackend(val store: CloudStore, val account: StateFlow<CloudAccount?>)

/** Where the backup stands, for Settings › Account (BABIES_AND_BACKUP §3.2). */
sealed interface CloudStatus {
    /** Nobody is signed in, or the build has no backend: "Not signed in". */
    data object Off : CloudStatus

    /** A sync is running, or the account's family is not known yet: "Backing up…". */
    data object Syncing : CloudStatus

    /** The last sync finished at [atMillis]: "Backed up · 14:31". */
    data class Synced(val atMillis: Long) : CloudStatus

    /** The backend could not be reached; entries stay on the phone until it can: "Saved on phone · will back up". */
    data object Offline : CloudStatus

    /** The last sync failed for another reason: "Couldn't back up · Tap to retry". */
    data object Error : CloudStatus

    /**
     * The account's backup was deleted but the account was not (its deletion failed): nothing is uploaded to its family
     * until "Back up now", another family or another account: "Backup deleted · account still exists".
     */
    data object Deleted : CloudStatus
}

/**
 * The backup of the log and the babies to the account's family in its [CloudStore] (BABIES_AND_BACKUP §4). Nothing here
 * throws.
 */
interface CloudSync {
    val status: StateFlow<CloudStatus>

    /** Starts following the account, its family and the pending entries and babies. Called once, from `CoreGraph.start()`. */
    fun start()

    /**
     * Pulls and pushes now, and returns when done (the Account screen's "Back up now"). Never throws. Also backs up an
     * account whose backup was deleted ([CloudStatus.Deleted]) again: the parent asked for it.
     */
    suspend fun syncNow()

    /** Pulls and pushes in the background, at most once a minute (the app coming to the foreground). */
    fun requestSync()

    /**
     * Takes the signed-in account out of its family's backup ([CloudStore.deleteAll], before the account itself is
     * deleted) and uploads nothing more to that family, also after a restart, until another family or account, signing
     * out, or [syncNow]. False (nothing done) while signed out or without a family. Returns true on success.
     */
    suspend fun deleteBackup(): Boolean
}

/** No backup: the watch, and phone builds without a backend. The status stays [CloudStatus.Off]. */
object NoopCloudSync : CloudSync {
    private val state = MutableStateFlow<CloudStatus>(CloudStatus.Off)
    override val status: StateFlow<CloudStatus> = state.asStateFlow()
    override fun start() = Unit
    override suspend fun syncNow() = Unit
    override fun requestSync() = Unit
    override suspend fun deleteBackup(): Boolean = false
}
