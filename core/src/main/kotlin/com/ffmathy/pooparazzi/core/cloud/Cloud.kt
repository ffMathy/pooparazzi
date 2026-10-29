package com.ffmathy.pooparazzi.core.cloud

import com.ffmathy.pooparazzi.core.model.Entry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * The signed-in account as the backup needs it. It is backed up only once the parent has agreed to it
 * ([consentVersion], BABIES_AND_BACKUP §3.4): the log is health information about a child (GDPR art. 9), so the
 * backup asks for explicit consent first. Signed in without it, the backup is [CloudStatus.Paused].
 *
 * @property consentVersion the [BackupConsent.VERSION] this account agreed to on this phone, or null.
 */
data class CloudAccount(val uid: String, val email: String, val consentVersion: Int? = null) {
    /** Agreed to the current wording of the backup's consent. */
    val consented: Boolean get() = (consentVersion ?: 0) >= BackupConsent.VERSION
}

/** The consent the backup asks for (docs/PRIVACY.md). */
object BackupConsent {
    /**
     * The version of the consent wording and the privacy policy it points to. Raise it when what is collected, or why,
     * changes: every account is then asked again before anything more is backed up.
     */
    const val VERSION = 1
}

/** Where the last pull stopped: the (server write time, document id) of the last document seen. */
@Serializable
data class CloudCursor(val seconds: Long, val nanos: Int, val documentId: String)

/**
 * One page of changes: decoded [entries] (undecodable documents skipped), the cursor of the page's last document,
 * and how many documents the page had (a page shorter than the limit is the last).
 */
class CloudPage(val entries: List<Entry>, val last: CloudCursor?, val documents: Int)

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
 * The account's copy of the log (Firestore in the phone app, BABIES_AND_BACKUP §4.1): one document per entry, holding
 * its [com.ffmathy.pooparazzi.core.model.EntryJson] and the server time of its last write. Calls throw
 * [CloudException].
 */
interface CloudStore {
    /** Writes the current version of [entries] (at most [UPLOAD_BATCH]), all or nothing, stamped with the server time. */
    suspend fun upload(uid: String, entries: List<Entry>)

    /** Up to [limit] documents written after [cursor] (null: from the start), oldest change first. */
    suspend fun changesAfter(uid: String, cursor: CloudCursor?, limit: Int): CloudPage

    /**
     * Stores that the account agreed to the backup's consent [version], stamped with the server time, so the consent can
     * be shown later (GDPR art. 7(1)). Written before the first upload under that version.
     */
    suspend fun recordConsent(uid: String, version: Int)

    /** Deletes the account's whole backup and its consent record (when the account is deleted). */
    suspend fun deleteAll(uid: String)

    companion object {
        /** The most entries one [upload] takes (Firestore allows 500 writes per batch). */
        const val UPLOAD_BATCH = 400

        /** How many documents a pull asks for per [changesAfter]. */
        const val PAGE_SIZE = 500
    }
}

/** What the phone app hands to `Core.init`: the store and the signed-in account (null when signed out). */
class CloudBackend(val store: CloudStore, val account: StateFlow<CloudAccount?>)

/** Where the backup stands, for Settings › Account (BABIES_AND_BACKUP §3.2). */
sealed interface CloudStatus {
    /** Nobody is signed in, or the build has no backend: "Not signed in". */
    data object Off : CloudStatus

    /**
     * Signed in, but the account has not agreed to the backup on this phone (signed in before the consent existed):
     * nothing is backed up until it does: "Backup paused · Agree to back up".
     */
    data object Paused : CloudStatus

    /** A sync is running: "Backing up…". */
    data object Syncing : CloudStatus

    /** The last sync finished at [atMillis]: "Backed up · 14:31". */
    data class Synced(val atMillis: Long) : CloudStatus

    /** The backend could not be reached; entries stay on the phone until it can: "Saved on phone · will back up". */
    data object Offline : CloudStatus

    /** The last sync failed for another reason: "Couldn't back up · Tap to retry". */
    data object Error : CloudStatus

    /**
     * The account's backup was deleted but the account was not (its deletion failed): nothing is uploaded for it until
     * "Back up now" or another account: "Backup deleted · account still exists".
     */
    data object Deleted : CloudStatus
}

/** The backup of the log to the account's [CloudStore] (BABIES_AND_BACKUP §4). Nothing here throws. */
interface CloudSync {
    val status: StateFlow<CloudStatus>

    /** Starts following the account and the pending entries. Called once, from `CoreGraph.start()`. */
    fun start()

    /**
     * Pulls and pushes now, and returns when done (the Account screen's "Back up now"). Never throws. Also backs up an
     * account whose backup was deleted ([CloudStatus.Deleted]) again: the parent asked for it.
     */
    suspend fun syncNow()

    /** Pulls and pushes in the background, at most once a minute (the app coming to the foreground). */
    fun requestSync()

    /**
     * Deletes the signed-in account's backup (before the account itself is deleted) and uploads nothing more for that
     * account, also after a restart, until another account signs in or [syncNow]. Returns true on success.
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
