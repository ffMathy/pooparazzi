package com.ffmathy.pooparazzi.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /** Renames baby [id] to [name] ([Settings.sanitizeName]d); a synced change. Unknown ids change nothing. */
    suspend fun renameBaby(id: String, name: String): Settings

    /**
     * Appends a new, unnamed baby and returns it; a synced change. It does not become the current baby. With
     * [Settings.MAX_BABIES] babies nothing is added and the last baby is returned (the apps hide the action then).
     */
    suspend fun addBaby(): Baby

    /**
     * Makes [id] this device's current baby. Local only: not stamped and not mirrored to the other device, which keeps
     * its own. Unknown ids change nothing.
     */
    suspend fun selectBaby(id: String): Settings

    /**
     * Appends unnamed babies for the unknown, non-blank [ids], in the given order (the phone adopts babies whose
     * entries arrived from elsewhere, BABIES_AND_BACKUP §2.1). A synced change only when something was added, and not
     * limited by [Settings.MAX_BABIES], so no entry is ever left without its baby.
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
 * [SettingsRepository] on Preferences DataStore.
 *
 * The babies are stored as one JSON array. An install from before several babies has only the `baby_name` key: it
 * reads as the first baby with that name, and the key is removed the first time the babies are written.
 *
 * @param syncScope where pushes run; null pushes inline (tests).
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock = SystemClock,
    private val sync: EntrySync = NoopEntrySync,
    private val syncScope: CoroutineScope? = null,
) : SettingsRepository {

    override val settings: Flow<Settings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun setBabyName(name: String): Settings = updateLocal { it.withBabyName(it.selectedBaby.id, name) }

    override suspend fun renameBaby(id: String, name: String): Settings = updateLocal { it.withBabyName(id, name) }

    override suspend fun addBaby(): Baby {
        val baby = Baby(Baby.newId())
        val result = updateLocal { if (it.babies.size >= Settings.MAX_BABIES) it else it.withBabies(listOf(baby.id)) }
        return result.babies.firstOrNull { it.id == baby.id } ?: result.babies.last()
    }

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

    override suspend fun ensureBabies(ids: Collection<String>): Settings = updateLocal { it.withBabies(ids) }

    override suspend fun setAppearance(appearance: Appearance): Settings = updateLocal { it.copy(appearance = appearance) }

    override suspend fun setLanguage(language: AppLanguage): Settings = updateLocal { it.copy(language = language) }

    override suspend fun applyRemote(remote: Settings): Boolean {
        val incoming = remote.normalized()
        var applied = false
        dataStore.edit { prefs ->
            val current = prefs.toSettings()
            // The current baby is this device's own: it is neither taken from the other device nor a difference.
            val same = incoming.copy(selectedBabyId = current.selectedBabyId) == current
            if (incoming.updatedAtMillis >= current.updatedAtMillis && !same) {
                prefs.write(incoming)
                applied = true
            }
        }
        return applied
    }

    override suspend fun applyLegacyRemote(remote: Settings): Boolean {
        val incoming = remote.normalized()
        var applied = false
        dataStore.edit { prefs ->
            val next = prefs.toSettings().withLegacyRemote(incoming) ?: return@edit
            prefs.write(next)
            applied = true
        }
        return applied
    }

    private suspend fun updateLocal(transform: (Settings) -> Settings): Settings {
        var changed: Settings? = null
        var result: Settings? = null
        dataStore.edit { prefs ->
            val current = prefs.toSettings()
            val next = transform(current)
            if (next == current) {
                result = current
            } else {
                val stamped = next.copy(updatedAtMillis = maxOf(clock.now(), current.updatedAtMillis + 1))
                prefs.write(stamped)
                changed = stamped
                result = stamped
            }
        }
        if (changed != null) {
            // One push at a time, always of the current settings: a slow push of an older change can never
            // land after a newer one and leave the other device with stale settings.
            val push: suspend () -> Unit = { pushMutex.withLock { runCatching { sync.pushSettings(current()) } } }
            val scope = syncScope
            if (scope == null) push() else scope.launch { push() }
        }
        return checkNotNull(result)
    }

    private val pushMutex = Mutex()

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
        ).normalized()
        val selected = this[KEY_SELECTED_BABY]?.takeIf { id -> settings.babies.any { it.id == id } }
        return settings.copy(selectedBabyId = selected ?: settings.babies.first().id)
    }

    /** Writes the synced settings. Never the current baby: that is written by [selectBaby] alone. */
    private fun MutablePreferences.write(settings: Settings) {
        this[KEY_BABIES] = SettingsJson.encodeBabies(settings.babies)
        remove(KEY_LEGACY_BABY_NAME)
        this[KEY_APPEARANCE] = settings.appearance.name
        this[KEY_LANGUAGE] = settings.language.name
        this[KEY_UPDATED_AT] = settings.updatedAtMillis
    }

    private companion object {
        val KEY_BABIES = stringPreferencesKey("babies")
        val KEY_SELECTED_BABY = stringPreferencesKey("selected_baby")

        /** The one baby's name, from before several babies. Read only; removed when the babies are written. */
        val KEY_LEGACY_BABY_NAME = stringPreferencesKey("baby_name")
        val KEY_APPEARANCE = stringPreferencesKey("appearance")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_UPDATED_AT = longPreferencesKey("updated_at")
    }
}
