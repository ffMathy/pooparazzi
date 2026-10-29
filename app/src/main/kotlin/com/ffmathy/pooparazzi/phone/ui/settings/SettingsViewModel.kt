package com.ffmathy.pooparazzi.phone.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.cloud.CloudSync
import com.ffmathy.pooparazzi.core.cloud.NoopCloudSync
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.data.SystemClock
import com.ffmathy.pooparazzi.core.export.LogExport
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.core.sync.EntrySync
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.core.sync.WatchVersionCheck
import com.ffmathy.pooparazzi.phone.BuildConfig
import com.ffmathy.pooparazzi.phone.account.Accounts
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.NoAccounts
import com.ffmathy.pooparazzi.phone.account.PrivacyPolicy
import com.ffmathy.pooparazzi.phone.app.AppLocales
import com.ffmathy.pooparazzi.phone.app.LocaleApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/** What Settings › Account says (BABIES_AND_BACKUP §3.2). */
@Immutable
sealed interface AccountSummary {
    /** The build has no Firebase configuration: "Backup isn't set up in this build", not tappable. */
    data object Unavailable : AccountSummary

    /** "Not signed in · Your log is only on this phone. Sign in to back it up." */
    data object SignedOut : AccountSummary

    /** The address and the backup status line. */
    data class SignedIn(val email: String, val status: CloudStatus) : AccountSummary
}

/**
 * Settings (DESIGN_SPEC §3.3.5, V1_ADDITIONS §5, BABIES_AND_BACKUP §2.4) as data.
 *
 * @property babies the babies in their order, each with what its name field shows: the parent's typing, or the
 *   stored name.
 * @property account what Settings › Account says (BABIES_AND_BACKUP §3.2).
 * @property canAddBaby false at [Settings.MAX_BABIES] (the "Add a baby" pill is hidden then).
 * @property removing the baby whose "Remove <label>?" confirmation is open, or null.
 * @property canRemoveBaby true with two or more babies: each name field has a remove button then.
 * @property watchNeedsUpdate the paired watch runs a version from before several babies: "Add a baby" is disabled, with
 *   "Update Pooparazzi on your watch first." under it.
 * @property privacyUrl where Settings › About › Privacy policy opens ([PrivacyPolicy]).
 */
@Immutable
data class SettingsUiState(
    val babies: List<Baby>,
    val appearance: Appearance,
    val language: AppLanguage,
    val sync: SyncStatus,
    val zone: ZoneId,
    val versionName: String,
    val account: AccountSummary = AccountSummary.Unavailable,
    val canAddBaby: Boolean = babies.size < Settings.MAX_BABIES,
    val watchNeedsUpdate: Boolean = false,
    val privacyUrl: String = PrivacyPolicy.REPOSITORY_URL,
    val removing: Baby? = null,
) {
    val canRemoveBaby: Boolean get() = babies.size > 1
}

/** Everything the parent can do in Settings. Defaults do nothing, so previews and screenshots can pass [None]. */
@Stable
interface SettingsActions {
    fun onBack() {}
    fun onBabyNameChange(id: String, name: String) {}

    /** The keyboard's Done in baby [id]'s field: saves its name at once. */
    fun onBabyNameDone(id: String) {}

    /** "Add a baby": appends an unnamed baby (the current baby stays). */
    fun onAddBaby() {}

    /** A name field's remove button: asks "Remove <label>?" first. */
    fun onRemoveBaby(id: String) {}

    /** "Remove" in the confirmation: removes the baby and deletes its log. */
    fun onConfirmRemoveBaby() {}

    /** "Cancel" in the confirmation, or Back. */
    fun onDismissRemoveBaby() {}
    fun onAppearance(appearance: Appearance) {}
    fun onLanguage(language: AppLanguage) {}
    fun onRetrySync() {}

    /** Settings › Account: opens the Account screen. */
    fun onOpenAccount() {}

    /** The account row in "Couldn't back up · Tap to retry": backs up again. */
    fun onRetryBackup() {}
    fun onOpenLicences() {}

    /** Settings › About › Privacy policy: opens it in the browser. */
    fun onOpenPrivacy() {}

    /** Settings › About › "Download my data": asks where to save the file, then writes it ([SettingsViewModel.export]). */
    fun onExport() {}

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
 * @param watchNeedsUpdate whether the paired watch runs a version from before several babies
 *   ([com.ffmathy.pooparazzi.core.sync.WatchVersionCheck]); asked once, when Settings opens.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val sync: EntrySync,
    private val locales: LocaleApplier,
    private val writeScope: CoroutineScope,
    private val accounts: Accounts = NoAccounts,
    private val cloud: CloudSync = NoopCloudSync,
    private val versionName: String = BuildConfig.VERSION_NAME,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nameDebounceMillis: Long = NAME_DEBOUNCE_MILLIS,
    private val watchNeedsUpdate: suspend () -> Boolean = { false },
    private val entries: EntryRepository? = null,
    private val clock: Clock = SystemClock,
) : ViewModel(), SettingsActions {

    /** An old watch would resolve conflicts across babies: no second baby until it is updated. */
    private val oldWatch = MutableStateFlow(false)

    /** What each touched field shows, by baby id. */
    private val nameDrafts = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The id of the baby whose removal waits for the parent's yes. */
    private val removing = MutableStateFlow<String?>(null)

    /**
     * The typed names not written yet, by baby id; a write takes its baby's name atomically, so no keystroke is lost or
     * written twice.
     */
    private val pendingNames = ConcurrentHashMap<String, String>()

    /** One name write at a time, for every baby: an older name never lands after a newer one. */
    private val nameWrites = Mutex()
    private val nameJobs = mutableMapOf<String, Job>()

    /** Null until the settings have loaded. */
    private val account = combine(accounts.user, cloud.status) { user, status ->
        when {
            !accounts.available -> AccountSummary.Unavailable
            user == null -> AccountSummary.SignedOut
            else -> AccountSummary.SignedIn(user.email, status)
        }
    }

    private val edits = combine(nameDrafts, removing, ::Pair)

    val state: StateFlow<SettingsUiState?> = combine(settings.settings, sync.status, edits, account, oldWatch) { s, status, (drafts, removingId), account, old ->
        SettingsUiState(
            babies = s.babies.map { baby -> drafts[baby.id]?.let { baby.copy(name = it) } ?: baby },
            appearance = s.appearance,
            language = s.language,
            sync = status,
            zone = zone,
            versionName = versionName,
            account = account,
            watchNeedsUpdate = old,
            privacyUrl = PrivacyPolicy.url(accounts.hostingDomain),
            removing = s.babies.takeIf { it.size > 1 }?.firstOrNull { it.id == removingId }
                ?.let { baby -> drafts[baby.id]?.let { baby.copy(name = it) } ?: baby },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { oldWatch.value = watchNeedsUpdate() }
    }

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
        if (oldWatch.value) return
        viewModelScope.launch { settings.addBaby() }
    }

    override fun onRemoveBaby(id: String) {
        removing.value = id
    }

    override fun onDismissRemoveBaby() {
        removing.value = null
    }

    /**
     * Removes the baby being confirmed: its name typed but not saved is dropped, then the baby is removed and, when it
     * was, its log deleted. In the app's scope, so leaving Settings at once cannot cut it short.
     */
    override fun onConfirmRemoveBaby() {
        val id = removing.value ?: return
        removing.value = null
        nameJobs.remove(id)?.cancel()
        pendingNames.remove(id)
        nameDrafts.update { it - id }
        writeScope.launch {
            val after = settings.removeBaby(id)
            if (after.babies.none { it.id == id }) entries?.deleteBabyEntries(id)
        }
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

    override fun onRetryBackup() {
        // In the app's scope: the Account screen opens over Settings while it runs.
        writeScope.launch { cloud.syncNow() }
    }

    override fun onCleared() {
        pendingNames.keys.toList().forEach { id -> writeScope.launch { flushName(id) } }
    }

    /**
     * "Download my data" (GDPR art. 15, 20): writes the whole log and the babies with their names ([LogExport]) to [out],
     * with the names as typed (also one not saved yet). Returns how many entries were written.
     */
    suspend fun export(out: OutputStream): Int {
        val repository = checkNotNull(entries) { "No log to export" }
        val drafts = nameDrafts.value
        val babies = settings.settings.first().babies.map { baby -> drafts[baby.id]?.let { baby.copy(name = it) } ?: baby }
        val log = repository.observeAllEntries().first()
        val text = LogExport.encode(babies, log, clock.now())
        withContext(Dispatchers.IO) { out.bufferedWriter(Charsets.UTF_8).use { it.write(text) } }
        return log.size
    }

    /** Writes the latest typed name of baby [id], one write at a time (an older name never lands after a newer one). */
    private suspend fun flushName(id: String) = nameWrites.withLock {
        val name = pendingNames.remove(id) ?: return@withLock
        settings.renameBaby(id, name)
    }

    companion object {
        const val NAME_DEBOUNCE_MILLIS = 500L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val watch = WatchVersionCheck.create(app)
                SettingsViewModel(
                    Core.settings, Core.sync, AppLocales, Core.graph.scope, AppAccounts.accounts, Core.cloud,
                    watchNeedsUpdate = watch::needsUpdate,
                    entries = Core.repository,
                    clock = Core.clock,
                )
            }
        }
    }
}
