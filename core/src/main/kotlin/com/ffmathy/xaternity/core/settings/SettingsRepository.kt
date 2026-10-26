package com.ffmathy.xaternity.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ffmathy.xaternity.core.data.Clock
import com.ffmathy.xaternity.core.data.SystemClock
import com.ffmathy.xaternity.core.sync.EntrySync
import com.ffmathy.xaternity.core.sync.NoopEntrySync
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

/** The user's settings. Local changes are mirrored to the other device through `/settings`. */
interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun current(): Settings = settings.first()

    suspend fun setBabyName(name: String): Settings

    suspend fun setAppearance(appearance: Appearance): Settings

    suspend fun setLanguage(language: AppLanguage): Settings

    /** Merges settings from the other device (last-writer-wins). Never echoes. Returns true when stored. */
    suspend fun applyRemote(remote: Settings): Boolean
}

/**
 * [SettingsRepository] on Preferences DataStore.
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

    override suspend fun setBabyName(name: String): Settings = updateLocal { it.copy(babyName = Settings.sanitizeName(name)) }

    override suspend fun setAppearance(appearance: Appearance): Settings = updateLocal { it.copy(appearance = appearance) }

    override suspend fun setLanguage(language: AppLanguage): Settings = updateLocal { it.copy(language = language) }

    override suspend fun applyRemote(remote: Settings): Boolean {
        val incoming = remote.copy(babyName = Settings.sanitizeName(remote.babyName))
        var applied = false
        dataStore.edit { prefs ->
            val current = prefs.toSettings()
            if (incoming.updatedAtMillis >= current.updatedAtMillis && incoming != current) {
                prefs.write(incoming)
                applied = true
            }
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

    private fun Preferences.toSettings() = Settings(
        babyName = this[KEY_BABY_NAME].orEmpty(),
        appearance = this[KEY_APPEARANCE]?.let { name -> Appearance.entries.firstOrNull { it.name == name } } ?: Appearance.AUTO,
        language = this[KEY_LANGUAGE]?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } } ?: AppLanguage.SYSTEM,
        updatedAtMillis = this[KEY_UPDATED_AT] ?: 0L,
    )

    private fun MutablePreferences.write(settings: Settings) {
        this[KEY_BABY_NAME] = settings.babyName
        this[KEY_APPEARANCE] = settings.appearance.name
        this[KEY_LANGUAGE] = settings.language.name
        this[KEY_UPDATED_AT] = settings.updatedAtMillis
    }

    private companion object {
        val KEY_BABY_NAME = stringPreferencesKey("baby_name")
        val KEY_APPEARANCE = stringPreferencesKey("appearance")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_UPDATED_AT = longPreferencesKey("updated_at")
    }
}
