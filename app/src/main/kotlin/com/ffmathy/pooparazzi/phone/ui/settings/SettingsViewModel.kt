package com.ffmathy.pooparazzi.phone.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * Settings (DESIGN_SPEC §3.3.5, V1_ADDITIONS §5) as data.
 *
 * @property babyName what the name field shows: the parent's typing, or the stored name.
 */
@Immutable
data class SettingsUiState(
    val babyName: String,
    val appearance: Appearance,
    val language: AppLanguage,
    val sync: SyncStatus,
    val zone: ZoneId,
    val versionName: String,
)

/** Everything the parent can do in Settings. Defaults do nothing, so previews and screenshots can pass [None]. */
@Stable
interface SettingsActions {
    fun onBack() {}
    fun onBabyNameChange(name: String) {}

    /** The keyboard's Done: saves the name at once. */
    fun onBabyNameDone() {}
    fun onAppearance(appearance: Appearance) {}
    fun onLanguage(language: AppLanguage) {}
    fun onRetrySync() {}
    fun onOpenLicences() {}

    /** Settings › Notifications: the system setting for Live Updates or notifications. */
    fun onOpenNotificationSettings() {}

    object None : SettingsActions
}

/**
 * Settings' state: the baby's name (saved 500 ms after the last keystroke, or at once on Done, and never lost when the
 * screen closes mid-typing), Appearance, and the language, which is persisted in [settings] (so it syncs to the
 * watch) and applied through [locales].
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

    private val nameDraft = MutableStateFlow<String?>(null)

    /** The typed name not written yet; taken atomically by the write, so no keystroke is lost or written twice. */
    private val pendingName = AtomicReference<String?>(null)
    private val nameWrites = Mutex()
    private var nameJob: Job? = null

    /** Null until the settings have loaded. */
    val state: StateFlow<SettingsUiState?> = combine(settings.settings, sync.status, nameDraft) { s, status, draft ->
        SettingsUiState(
            babyName = draft ?: s.babyName,
            appearance = s.appearance,
            language = s.language,
            sync = status,
            zone = zone,
            versionName = versionName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override fun onBabyNameChange(name: String) {
        val limited = name.take(Settings.MAX_BABY_NAME_LENGTH)
        nameDraft.value = limited
        pendingName.set(limited)
        nameJob?.cancel()
        nameJob = writeScope.launch {
            delay(nameDebounceMillis)
            flushName()
        }
    }

    override fun onBabyNameDone() {
        nameJob?.cancel()
        nameJob = writeScope.launch { flushName() }
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
        if (pendingName.get() != null) writeScope.launch { flushName() }
    }

    /** Writes the latest typed name, one write at a time (an older name never lands after a newer one). */
    private suspend fun flushName() = nameWrites.withLock {
        val name = pendingName.getAndSet(null) ?: return@withLock
        settings.setBabyName(name)
    }

    companion object {
        const val NAME_DEBOUNCE_MILLIS = 500L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(Core.settings, Core.sync, AppLocales, Core.graph.scope) }
        }
    }
}
