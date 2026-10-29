package com.ffmathy.pooparazzi.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ffmathy.pooparazzi.core.cloud.CloudBabies
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.SystemClock
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * The user's settings. Local changes are stamped and mirrored to the other device through `/settings`, except the
 * choice of the current baby, which belongs to this device alone ([selectBaby]).
 */
interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun current(): Settings = settings.first()

    /** Renames the current baby ([Settings.selectedBaby]); a synced change. */
    suspend fun setBabyName(name: String): Settings

    /**
     * Renames baby [id] to [name] ([Settings.sanitizeName]d); a synced change that stamps the baby
     * ([com.ffmathy.pooparazzi.core.model.Baby.updatedAtMillis]), so it wins in the family's backup too. Unknown ids
     * (and the same name) change nothing.
     */
    suspend fun renameBaby(id: String, name: String): Settings

    /**
     * Appends a new, unnamed, stamped baby and returns it; a synced change. It does not become the current baby. With
     * [Settings.MAX_BABIES] babies nothing is added and the last baby is returned (the apps hide the action then).
     */
    suspend fun addBaby(): Baby

    /**
     * Removes baby [id] ([Settings.withRemovedBaby]); a synced change whose tombstone goes to the family's backup like a
     * rename. Unknown ids and the last baby change nothing. The baby's entries are the caller's to delete
     * ([com.ffmathy.pooparazzi.core.data.EntryRepository.deleteBabyEntries]).
     */
    suspend fun removeBaby(id: String): Settings

    /**
     * Makes [id] this device's current baby. Local only: not stamped and not mirrored to the other device, which keeps
     * its own. Unknown ids change nothing.
     */
    suspend fun selectBaby(id: String): Settings

    /**
     * Appends unnamed babies for the unknown, non-blank [ids], in the given order (the phone adopts babies whose
     * entries arrived from elsewhere, BABIES_AND_BACKUP §2.1). A synced change only when something was added, and not
     * limited by [Settings.MAX_BABIES], so no entry is ever left without its baby. Adopted babies are unstamped
     * (`updatedAtMillis` 0): the family's version of them, name included, wins when it arrives.
     */
    suspend fun ensureBabies(ids: Collection<String>): Settings

    suspend fun setAppearance(appearance: Appearance): Settings

    suspend fun setLanguage(language: AppLanguage): Settings

    /**
     * Merges settings from the other device (last-writer-wins). Never echoes and never changes this device's current
     * baby. Returns true when stored.
     */
    suspend fun applyRemote(remote: Settings): Boolean

    /**
     * Merges settings from a device from before several babies ([Settings.withLegacyRemote]): never replaces the list of
     * babies. Returns true when stored.
     */
    suspend fun applyLegacyRemote(remote: Settings): Boolean
}

/**
 * [SettingsRepository] on Preferences DataStore, and the babies' side of the family's backup ([CloudBabies]).
 *
 * The babies are stored as one JSON array. An install from before several babies has only the `baby_name` key: it
 * reads as the first baby with that name, and the key is removed the first time the babies are written.
 *
 * While [cloudBackup] says so (an account is signed in), every baby added, renamed or removed here, or changed by settings from
 * the watch, is flagged for the backup (the ids in `cloud_pending_babies`, never mirrored to the watch); babies adopted
 * from their entries ([ensureBabies]) are not: they carry nothing the family lacks. Babies merged from the backup
 * ([applyCloudBabies]) are stamped settings changes like local ones, so they reach the watch.
 *
 * @param syncScope where pushes run; null pushes inline (tests).
 * @param cloudBackup true while an account is signed in (the phone with a backend).
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock = SystemClock,
    private val sync: EntrySync = NoopEntrySync,
    private val syncScope: CoroutineScope? = null,
    private val cloudBackup: () -> Boolean = { false },
) : SettingsRepository, CloudBabies {

    private val data: Flow<Preferences> =
        dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val settings: Flow<Settings> = data.map { it.toSettings() }.distinctUntilChanged()

    override suspend fun setBabyName(name: String): Settings =
        updateLocal { current, at -> current.withBabyName(current.selectedBaby.id, name, at) }

    override suspend fun renameBaby(id: String, name: String): Settings =
        updateLocal { current, at -> current.withBabyName(id, name, at) }

    override suspend fun addBaby(): Baby {
        val baby = Baby(Baby.newId())
        val result = updateLocal { current, at ->
            if (current.babies.size >= Settings.MAX_BABIES) current else current.withBabies(listOf(baby.id), at)
        }
        return result.babies.firstOrNull { it.id == baby.id } ?: result.babies.last()
    }

    override suspend fun removeBaby(id: String): Settings =
        updateLocal { current, at -> current.withRemovedBaby(id, at) }

    override suspend fun selectBaby(id: String): Settings {
        var result: Settings? = null
        dataStore.edit { prefs ->
            val current = prefs.toSettings()
            val next = current.withSelectedBaby(id)
            if (next != current) prefs[KEY_SELECTED_BABY] = next.selectedBabyId
            result = next
        }
        return checkNotNull(result)
    }

    override suspend fun ensureBabies(ids: Collection<String>): Settings =
        updateLocal(flagBabies = false) { current, _ -> current.withBabies(ids) }

    override suspend fun setAppearance(appearance: Appearance): Settings =
        updateLocal { current, _ -> current.copy(appearance = appearance) }

    override suspend fun setLanguage(language: AppLanguage): Settings =
        updateLocal { current, _ -> current.copy(language = language) }

    override suspend fun applyRemote(remote: Settings): Boolean {
        var applied = false
        dataStore.edit { prefs ->
            val current = prefs.toSettings()
            // A device that does not know tombstones yet sends none: the removed babies stay removed.
            val incoming = remote.normalized().withMergedBabies(current.removedBabies)
            // The current baby is this device's own: it is neither taken from the other device nor a difference.
            val same = incoming.copy(selectedBabyId = current.selectedBabyId) == current
            if (incoming.updatedAtMillis >= current.updatedAtMillis && !same) {
                prefs.write(incoming)
                prefs.flagChangedBabies(current, incoming)
                applied = true
            }
        }
        return applied
    }

    override suspend fun applyLegacyRemote(remote: Settings): Boolean {
        val incoming = remote.normalized()
        var applied = false
        dataStore.edit { prefs ->
            val current = prefs.toSettings()
            val next = current.withLegacyRemote(incoming) ?: return@edit
            prefs.write(next)
            prefs.flagChangedBabies(current, next)
            applied = true
        }
        return applied
    }

    // ---- The family's backup (CloudBabies) ------------------------------------------------------------

    override fun observeCloudPendingBabies(): Flow<List<Baby>> = data.map { it.pendingBabies() }.distinctUntilChanged()

    override suspend fun cloudPendingBabies(): List<Baby> = data.first().pendingBabies()

    override suspend fun markBabiesCloudSynced(babies: List<Baby>): Int {
        var cleared = 0
        dataStore.edit { prefs ->
            val stored = prefs.toSettings().allBabies
            val pending = prefs[KEY_CLOUD_PENDING].orEmpty()
            // Cleared only while still exactly as uploaded: a baby renamed meanwhile goes up again.
            val done = babies.filter { it.id in pending && it in stored }.mapTo(mutableSetOf()) { it.id }
            prefs.writePending(pending - done)
            cleared = done.size
        }
        return cleared
    }

    override suspend fun markAllBabiesForCloud() {
        dataStore.edit { prefs -> prefs.writePending(prefs.toSettings().allBabies.mapTo(mutableSetOf()) { it.id }) }
    }

    override suspend fun clearBabiesCloudPending() {
        dataStore.edit { prefs -> prefs.writePending(emptySet()) }
    }

    override suspend fun applyCloudBabies(babies: List<Baby>): Int {
        var changed = 0
        val stamped = edit { prefs ->
            val current = prefs.toSettings()
            val merged = current.withMergedBabies(babies)
            val pending = prefs[KEY_CLOUD_PENDING].orEmpty().toMutableSet()
            for (incoming in babies) {
                val kept = merged.allBabies.firstOrNull { it.id == incoming.id } ?: continue
                val tidy = if (incoming.removed) incoming.copy(name = "") else incoming.copy(name = Settings.sanitizeName(incoming.name))
                val same = kept == tidy
                // What the backup holds now, or older: the backup's version is no reason to send this one again;
                // a stored version that beats the backup's goes up (while signed in).
                if (same) pending -= kept.id else if (cloudBackup()) pending += kept.id
            }
            prefs.writePending(pending)
            changed = merged.allBabies.count { it !in current.allBabies }
            if (merged == current) {
                null
            } else {
                merged.copy(updatedAtMillis = nextStamp(current)).also { prefs.write(it) }
            }
        }
        if (stamped != null) mirror()
        return changed
    }

    // ---- Writing ---------------------------------------------------------------------------------

    /**
     * Applies a local change: [transform] gets the current settings and the stamp the change will carry (for the
     * babies it adds or renames). A change is stamped, mirrored to the watch and, with [flagBabies], flags the babies it
     * changed for the family's backup.
     */
    private suspend fun updateLocal(flagBabies: Boolean = true, transform: (Settings, Long) -> Settings): Settings {
        var result: Settings? = null
        val changed = edit { prefs ->
            val current = prefs.toSettings()
            val stamp = nextStamp(current)
            val next = transform(current, stamp)
            if (next == current) {
                result = current
                null
            } else {
                val stamped = next.copy(updatedAtMillis = stamp)
                prefs.write(stamped)
                if (flagBabies) prefs.flagChangedBabies(current, stamped)
                result = stamped
                stamped
            }
        }
        if (changed != null) mirror()
        return checkNotNull(result)
    }

    /** Edits the preferences with [block] and returns what it returned. */
    private suspend fun <T> edit(block: (MutablePreferences) -> T): T {
        var out: Result<T>? = null
        dataStore.edit { prefs -> out = Result.success(block(prefs)) }
        return checkNotNull(out).getOrThrow()
    }

    /** The stamp of the next change: now, or just after [current]'s when the clock is behind it. */
    private fun nextStamp(current: Settings): Long = maxOf(clock.now(), current.updatedAtMillis + 1)

    /**
     * Pushes the current settings to the watch. One push at a time, always of the current settings: a slow push of an
     * older change can never land after a newer one and leave the other device with stale settings.
     */
    private suspend fun mirror() {
        val push: suspend () -> Unit = { pushMutex.withLock { runCatching { sync.pushSettings(current()) } } }
        val scope = syncScope
        if (scope == null) push() else scope.launch { push() }
    }

    private val pushMutex = Mutex()

    /** Flags the babies of [after] that are new or changed against [before], while an account is signed in. */
    private fun MutablePreferences.flagChangedBabies(before: Settings, after: Settings) {
        if (!cloudBackup()) return
        val changed = after.allBabies.filter { it !in before.allBabies }.map { it.id }
        if (changed.isNotEmpty()) writePending(this[KEY_CLOUD_PENDING].orEmpty() + changed)
    }

    private fun MutablePreferences.writePending(ids: Set<String>) {
        if (ids.isEmpty()) remove(KEY_CLOUD_PENDING) else this[KEY_CLOUD_PENDING] = ids
    }

    /** The flagged babies, in their current version and in list order. */
    private fun Preferences.pendingBabies(): List<Baby> {
        val pending = this[KEY_CLOUD_PENDING].orEmpty()
        return if (pending.isEmpty()) emptyList() else toSettings().allBabies.filter { it.id in pending }
    }

    /**
     * The stored settings, [Settings.normalized]. The current baby is the stored choice while it is still one of the
     * babies, else the first baby.
     */
    private fun Preferences.toSettings(): Settings {
        val babies = this[KEY_BABIES]?.let(SettingsJson::decodeBabiesOrNull)
            ?: listOf(Baby(Baby.FIRST_ID, this[KEY_LEGACY_BABY_NAME].orEmpty()))
        val settings = Settings(
            babies = babies,
            appearance = this[KEY_APPEARANCE]?.let { name -> Appearance.entries.firstOrNull { it.name == name } } ?: Appearance.AUTO,
            language = this[KEY_LANGUAGE]?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } } ?: AppLanguage.SYSTEM,
            updatedAtMillis = this[KEY_UPDATED_AT] ?: 0L,
            removedBabies = this[KEY_REMOVED_BABIES]?.let(SettingsJson::decodeBabiesOrNull).orEmpty(),
        ).normalized()
        val selected = this[KEY_SELECTED_BABY]?.takeIf { id -> settings.babies.any { it.id == id } }
        return settings.copy(selectedBabyId = selected ?: settings.babies.first().id)
    }

    /** Writes the synced settings. Never the current baby: that is written by [selectBaby] alone. */
    private fun MutablePreferences.write(settings: Settings) {
        this[KEY_BABIES] = SettingsJson.encodeBabies(settings.babies)
        if (settings.removedBabies.isEmpty()) {
            remove(KEY_REMOVED_BABIES)
        } else {
            this[KEY_REMOVED_BABIES] = SettingsJson.encodeBabies(settings.removedBabies)
        }
        remove(KEY_LEGACY_BABY_NAME)
        this[KEY_APPEARANCE] = settings.appearance.name
        this[KEY_LANGUAGE] = settings.language.name
        this[KEY_UPDATED_AT] = settings.updatedAtMillis
    }

    private companion object {
        val KEY_BABIES = stringPreferencesKey("babies")
        val KEY_SELECTED_BABY = stringPreferencesKey("selected_baby")

        /** The removed babies' tombstones ([Settings.removedBabies]), one JSON array. */
        val KEY_REMOVED_BABIES = stringPreferencesKey("removed_babies")

        /** The one baby's name, from before several babies. Read only; removed when the babies are written. */
        val KEY_LEGACY_BABY_NAME = stringPreferencesKey("baby_name")
        val KEY_APPEARANCE = stringPreferencesKey("appearance")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_UPDATED_AT = longPreferencesKey("updated_at")

        /** The ids of the babies not yet in the family's backup. This phone's alone: never mirrored, never synced. */
        val KEY_CLOUD_PENDING = stringSetPreferencesKey("cloud_pending_babies")
    }
}
