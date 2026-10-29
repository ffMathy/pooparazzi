package com.ffmathy.pooparazzi.phone.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.phone.BuildConfig
import com.ffmathy.pooparazzi.phone.app.AppLocales
import com.ffmathy.pooparazzi.phone.app.LocaleApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings (DESIGN_SPEC §3.3.5, V1_ADDITIONS §5, BABIES_AND_BACKUP §2.4) as data.
 *
 * @property babies the babies in their order, each with what its name field shows: the parent's typing, or the
 *   stored name.
 * @property canAddBaby false at [Settings.MAX_BABIES] (the "Add a baby" pill is hidden then).
 */
@Immutable
data class SettingsUiState(
    val babies: List<Baby>,
    val appearance: Appearance,
    val language: AppLanguage,
    val sync: SyncStatus,
    val zone: ZoneId,
    val versionName: String,
    val canAddBaby: Boolean = babies.size < Settings.MAX_BABIES,
)

/** Everything the parent can do in Settings. Defaults do nothing, so previews and screenshots can pass [None]. */
@Stable
interface SettingsActions {
    fun onBack() {}
    fun onBabyNameChange(id: String, name: String) {}

    /** The keyboard's Done in baby [id]'s field: saves its name at once. */
    fun onBabyNameDone(id: String) {}

    /** "Add a baby": appends an unnamed baby (the current baby stays). */
    fun onAddBaby() {}
    fun onAppearance(appearance: Appearance) {}
    fun onLanguage(language: AppLanguage) {}
    fun onRetrySync() {}
    fun onOpenLicences() {}

    /** Settings › Notifications: the system setting for Live Updates or notifications. */
    fun onOpenNotificationSettings() {}

    object None : SettingsActions
}

/**
 * Settings' state: the babies' names (each saved 500 ms after the last keystroke in its field, or at once on Done, and
 * never lost when the screen closes mid-typing), adding a baby, Appearance, and the language, which is persisted in
 * [settings] (so it syncs to the watch) and applied through [locales].
 *
 * @param writeScope outlives this view model, so a pending name write still lands after the screen closed.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val sync: EntrySync,
    private val locales: LocaleApplier,
    private val writeScope: CoroutineScope,
    private val versionName: String = BuildConfig.VERSION_NAME,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nameDebounceMillis: Long = NAME_DEBOUNCE_MILLIS,
) : ViewModel(), SettingsActions {

    /** What each touched field shows, by baby id. */
    private val nameDrafts = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * The typed names not written yet, by baby id; a write takes its baby's name atomically, so no keystroke is lost or
     * written twice.
     */
    private val pendingNames = ConcurrentHashMap<String, String>()

    /** One name write at a time, for every baby: an older name never lands after a newer one. */
    private val nameWrites = Mutex()
    private val nameJobs = mutableMapOf<String, Job>()

    /** Null until the settings have loaded. */
    val state: StateFlow<SettingsUiState?> = combine(settings.settings, sync.status, nameDrafts) { s, status, drafts ->
        SettingsUiState(
            babies = s.babies.map { baby -> drafts[baby.id]?.let { baby.copy(name = it) } ?: baby },
            appearance = s.appearance,
            language = s.language,
            sync = status,
            zone = zone,
            versionName = versionName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override fun onBabyNameChange(id: String, name: String) {
        val limited = name.take(Settings.MAX_BABY_NAME_LENGTH)
        nameDrafts.update { it + (id to limited) }
        pendingNames[id] = limited
        nameJobs.remove(id)?.cancel()
        nameJobs[id] = writeScope.launch {
            delay(nameDebounceMillis)
            flushName(id)
        }
    }

    override fun onBabyNameDone(id: String) {
        nameJobs.remove(id)?.cancel()
        nameJobs[id] = writeScope.launch { flushName(id) }
    }

    override fun onAddBaby() {
        viewModelScope.launch { settings.addBaby() }
    }

    override fun onAppearance(appearance: Appearance) {
        viewModelScope.launch { settings.setAppearance(appearance) }
    }

    override fun onLanguage(language: AppLanguage) {
        viewModelScope.launch {
            // Persist first (it syncs to the watch), then switch: switching recreates the activity.
            settings.setLanguage(language)
            locales.apply(language)
        }
    }

    override fun onRetrySync() {
        viewModelScope.launch { sync.retry() }
    }

    override fun onCleared() {
        pendingNames.keys.toList().forEach { id -> writeScope.launch { flushName(id) } }
    }

    /** Writes the latest typed name of baby [id], one write at a time (an older name never lands after a newer one). */
    private suspend fun flushName(id: String) = nameWrites.withLock {
        val name = pendingNames.remove(id) ?: return@withLock
        settings.renameBaby(id, name)
    }

    companion object {
        const val NAME_DEBOUNCE_MILLIS = 500L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(Core.settings, Core.sync, AppLocales, Core.graph.scope) }
        }
    }
}
