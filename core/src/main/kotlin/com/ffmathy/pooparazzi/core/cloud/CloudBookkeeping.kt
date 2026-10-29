package com.ffmathy.pooparazzi.core.cloud

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * What the backup remembers on this device between runs. Each value names the family it belongs to (the backup is the
 * family's, FAMILIES), so joining or leaving a family, or signing in to an account in another family, starts afresh.
 * Never synced and never backed up.
 *
 * @property cursor where the last pull of the family's entries for [cursorFamilyId] stopped.
 * @property babiesCursor where the last pull of the family's babies for [cursorFamilyId] stopped.
 * @property fullUploadFamilyId the family every row and baby was last flagged for ("everything so far is backed up").
 * @property lastSyncAtMillis when the last sync for [lastSyncFamilyId] succeeded.
 * @property deletedFamilyId the family the account was taken out of ([CloudSync.deleteBackup]) while the account itself
 *   is still there (deleting the account failed, or has not run yet): nothing is uploaded to it, also after a restart,
 *   until another family (or nobody) is signed in or the parent backs up again ("Back up now").
 */
data class CloudBookkeeping(
    val cursorFamilyId: String? = null,
    val cursor: CloudCursor? = null,
    val babiesCursor: CloudCursor? = null,
    val fullUploadFamilyId: String? = null,
    val lastSyncFamilyId: String? = null,
    val lastSyncAtMillis: Long? = null,
    val deletedFamilyId: String? = null,
) {
    /** The entries' cursor to pull [familyId] from: null (from the start) when the stored one belongs to another family. */
    fun cursorFor(familyId: String): CloudCursor? = cursor.takeIf { cursorFamilyId == familyId }

    /** The babies' cursor to pull [familyId] from: null (from the start) when the stored one belongs to another family. */
    fun babiesCursorFor(familyId: String): CloudCursor? = babiesCursor.takeIf { cursorFamilyId == familyId }

    /** These values with the entries' cursor for [familyId] at [at] (the babies' cursor kept only if it is [familyId]'s). */
    fun withCursor(familyId: String, at: CloudCursor): CloudBookkeeping =
        copy(cursorFamilyId = familyId, cursor = at, babiesCursor = babiesCursorFor(familyId))

    /** These values with the babies' cursor for [familyId] at [at] (the entries' cursor kept only if it is [familyId]'s). */
    fun withBabiesCursor(familyId: String, at: CloudCursor): CloudBookkeeping =
        copy(cursorFamilyId = familyId, cursor = cursorFor(familyId), babiesCursor = at)

    /** When the last sync for [familyId] succeeded, or null. */
    fun lastSyncFor(familyId: String): Long? = lastSyncAtMillis.takeIf { lastSyncFamilyId == familyId }

    /** True while the account was taken out of [familyId]'s backup and nothing may be uploaded to it. */
    fun isDeleted(familyId: String): Boolean = deletedFamilyId == familyId

    /** These values with everything about [familyId] forgotten (its backup is being deleted). [deletedFamilyId] is kept. */
    fun forget(familyId: String): CloudBookkeeping = copy(
        cursorFamilyId = cursorFamilyId.takeUnless { it == familyId },
        cursor = cursor.takeUnless { cursorFamilyId == familyId },
        babiesCursor = babiesCursor.takeUnless { cursorFamilyId == familyId },
        fullUploadFamilyId = fullUploadFamilyId.takeUnless { it == familyId },
        lastSyncFamilyId = lastSyncFamilyId.takeUnless { it == familyId },
        lastSyncAtMillis = lastSyncAtMillis.takeUnless { lastSyncFamilyId == familyId },
    )
}

/** Where [CloudBookkeeping] is kept. */
interface CloudBookkeepingStore {
    suspend fun get(): CloudBookkeeping

    /** Replaces the values with [transform] of the current ones, atomically. */
    suspend fun update(transform: (CloudBookkeeping) -> CloudBookkeeping)
}

/**
 * [CloudBookkeepingStore] on Preferences DataStore (the `pooparazzi_cloud` file). Unreadable values read as absent.
 *
 * The keys of the backup from before families (named by account: `cursor_uid`, `full_upload_uid`, …) are not read, so
 * the first sync after the update counts as the first for the family: everything on the phone is flagged and uploaded
 * to it (FAMILIES, "existing accounts"). They are removed at the first write.
 */
class DataStoreCloudBookkeepingStore(private val dataStore: DataStore<Preferences>) : CloudBookkeepingStore {

    override suspend fun get(): CloudBookkeeping = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .first()
        .toBookkeeping()

    override suspend fun update(transform: (CloudBookkeeping) -> CloudBookkeeping) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toBookkeeping())) }
    }

    private fun Preferences.toBookkeeping() = CloudBookkeeping(
        cursorFamilyId = this[KEY_CURSOR_FAMILY],
        cursor = this[KEY_CURSOR]?.let(::decodeCursor),
        babiesCursor = this[KEY_BABIES_CURSOR]?.let(::decodeCursor),
        fullUploadFamilyId = this[KEY_FULL_UPLOAD_FAMILY],
        lastSyncFamilyId = this[KEY_LAST_SYNC_FAMILY],
        lastSyncAtMillis = this[KEY_LAST_SYNC_AT],
        deletedFamilyId = this[KEY_DELETED_FAMILY],
    )

    private fun MutablePreferences.write(values: CloudBookkeeping) {
        LEGACY_KEYS.forEach { remove(it) }
        putOrRemove(KEY_CURSOR_FAMILY, values.cursorFamilyId)
        putOrRemove(KEY_CURSOR, values.cursor?.let(::encodeCursor))
        putOrRemove(KEY_BABIES_CURSOR, values.babiesCursor?.let(::encodeCursor))
        putOrRemove(KEY_FULL_UPLOAD_FAMILY, values.fullUploadFamilyId)
        putOrRemove(KEY_LAST_SYNC_FAMILY, values.lastSyncFamilyId)
        putOrRemove(KEY_LAST_SYNC_AT, values.lastSyncAtMillis)
        putOrRemove(KEY_DELETED_FAMILY, values.deletedFamilyId)
    }

    /** Sets [key] to [value], or removes it for null. */
    private fun <T : Any> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun decodeCursor(text: String): CloudCursor? =
            runCatching { json.decodeFromString(CloudCursor.serializer(), text) }.getOrNull()

        fun encodeCursor(cursor: CloudCursor): String = json.encodeToString(CloudCursor.serializer(), cursor)

        val KEY_CURSOR_FAMILY = stringPreferencesKey("cursor_family")
        val KEY_CURSOR = stringPreferencesKey("family_cursor")
        val KEY_BABIES_CURSOR = stringPreferencesKey("family_babies_cursor")
        val KEY_FULL_UPLOAD_FAMILY = stringPreferencesKey("full_upload_family")
        val KEY_LAST_SYNC_FAMILY = stringPreferencesKey("last_sync_family")
        val KEY_LAST_SYNC_AT = longPreferencesKey("family_last_sync_at")
        val KEY_DELETED_FAMILY = stringPreferencesKey("deleted_family")

        /** The keys from before families, named by account. Never read; removed at the first write. */
        val LEGACY_KEYS: List<Preferences.Key<*>> = listOf(
            stringPreferencesKey("cursor_uid"),
            stringPreferencesKey("cursor"),
            stringPreferencesKey("full_upload_uid"),
            stringPreferencesKey("last_sync_uid"),
            longPreferencesKey("last_sync_at"),
            stringPreferencesKey("deleted_uid"),
        )
    }
}
