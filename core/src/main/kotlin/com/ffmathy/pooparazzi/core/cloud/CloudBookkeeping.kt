package com.ffmathy.pooparazzi.core.cloud

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * What the backup remembers on this device between runs. Each value names the account it belongs to, so signing in to
 * another account starts afresh. Never synced and never backed up.
 *
 * @property cursor where the last pull for [cursorUid] stopped.
 * @property fullUploadUid the account every row was last flagged for ("everything logged so far is backed up").
 * @property lastSyncAtMillis when the last sync for [lastSyncUid] succeeded.
 * @property deletedUid the account whose backup was deleted while the account itself is still there (deleting the
 *   account failed, or has not run yet): nothing is uploaded for it, also after a restart, until another account (or
 *   nobody) is signed in or the parent backs up again ("Back up now").
 * @property consentVersion the consent version last recorded in the backend for [consentUid] ([CloudStore.recordConsent]).
 */
data class CloudBookkeeping(
    val cursorUid: String? = null,
    val cursor: CloudCursor? = null,
    val fullUploadUid: String? = null,
    val lastSyncUid: String? = null,
    val lastSyncAtMillis: Long? = null,
    val deletedUid: String? = null,
    val consentUid: String? = null,
    val consentVersion: Int? = null,
) {
    /** The cursor to pull [uid] from: null (from the start) when the stored one belongs to another account. */
    fun cursorFor(uid: String): CloudCursor? = cursor.takeIf { cursorUid == uid }

    /** When the last sync for [uid] succeeded, or null. */
    fun lastSyncFor(uid: String): Long? = lastSyncAtMillis.takeIf { lastSyncUid == uid }

    /** The consent version the backend holds for [uid], as far as this device knows; null when none was recorded. */
    fun consentFor(uid: String): Int? = consentVersion.takeIf { consentUid == uid }

    /** True while [uid]'s backup is deleted and nothing may be uploaded for it. */
    fun isDeleted(uid: String): Boolean = deletedUid == uid

    /** These values with everything about [uid] forgotten (its backup is being deleted). [deletedUid] is kept. */
    fun forget(uid: String): CloudBookkeeping = copy(
        cursorUid = cursorUid.takeUnless { it == uid },
        cursor = cursor.takeUnless { cursorUid == uid },
        fullUploadUid = fullUploadUid.takeUnless { it == uid },
        lastSyncUid = lastSyncUid.takeUnless { it == uid },
        lastSyncAtMillis = lastSyncAtMillis.takeUnless { lastSyncUid == uid },
        consentUid = consentUid.takeUnless { it == uid },
        consentVersion = consentVersion.takeUnless { consentUid == uid },
    )
}

/** Where [CloudBookkeeping] is kept. */
interface CloudBookkeepingStore {
    suspend fun get(): CloudBookkeeping

    /** Replaces the values with [transform] of the current ones, atomically. */
    suspend fun update(transform: (CloudBookkeeping) -> CloudBookkeeping)
}

/** [CloudBookkeepingStore] on Preferences DataStore (the `pooparazzi_cloud` file). Unreadable values read as absent. */
class DataStoreCloudBookkeepingStore(private val dataStore: DataStore<Preferences>) : CloudBookkeepingStore {

    override suspend fun get(): CloudBookkeeping = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .first()
        .toBookkeeping()

    override suspend fun update(transform: (CloudBookkeeping) -> CloudBookkeeping) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toBookkeeping())) }
    }

    private fun Preferences.toBookkeeping() = CloudBookkeeping(
        cursorUid = this[KEY_CURSOR_UID],
        cursor = this[KEY_CURSOR]?.let { runCatching { json.decodeFromString(CloudCursor.serializer(), it) }.getOrNull() },
        fullUploadUid = this[KEY_FULL_UPLOAD_UID],
        lastSyncUid = this[KEY_LAST_SYNC_UID],
        lastSyncAtMillis = this[KEY_LAST_SYNC_AT],
        deletedUid = this[KEY_DELETED_UID],
        consentUid = this[KEY_CONSENT_UID],
        consentVersion = this[KEY_CONSENT_VERSION],
    )

    private fun MutablePreferences.write(values: CloudBookkeeping) {
        putOrRemove(KEY_CURSOR_UID, values.cursorUid)
        putOrRemove(KEY_CURSOR, values.cursor?.let { json.encodeToString(CloudCursor.serializer(), it) })
        putOrRemove(KEY_FULL_UPLOAD_UID, values.fullUploadUid)
        putOrRemove(KEY_LAST_SYNC_UID, values.lastSyncUid)
        putOrRemove(KEY_LAST_SYNC_AT, values.lastSyncAtMillis)
        putOrRemove(KEY_DELETED_UID, values.deletedUid)
        putOrRemove(KEY_CONSENT_UID, values.consentUid)
        putOrRemove(KEY_CONSENT_VERSION, values.consentVersion)
    }

    /** Sets [key] to [value], or removes it for null. */
    private fun <T : Any> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val KEY_CURSOR_UID = stringPreferencesKey("cursor_uid")
        val KEY_CURSOR = stringPreferencesKey("cursor")
        val KEY_FULL_UPLOAD_UID = stringPreferencesKey("full_upload_uid")
        val KEY_LAST_SYNC_UID = stringPreferencesKey("last_sync_uid")
        val KEY_LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val KEY_DELETED_UID = stringPreferencesKey("deleted_uid")
        val KEY_CONSENT_UID = stringPreferencesKey("consent_uid")
        val KEY_CONSENT_VERSION = intPreferencesKey("consent_version")
    }
}
