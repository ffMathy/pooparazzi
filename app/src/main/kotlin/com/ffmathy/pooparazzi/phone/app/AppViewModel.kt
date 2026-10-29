package com.ffmathy.pooparazzi.phone.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.phone.account.Accounts
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.ffmathy.pooparazzi.phone.account.NoAccounts
import com.ffmathy.pooparazzi.phone.account.WelcomePrefs
import com.ffmathy.pooparazzi.phone.account.WelcomeStore
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The inputs of the app-wide Appearance rule (DESIGN_SPEC §8.1) that come from data. */
data class AppearanceInputs(val appearance: Appearance, val sleepRunning: Boolean)

/**
 * App-level state: the Appearance setting and whether a sleep runs for any baby (Auto dims for naps while any baby is
 * asleep, BABIES_AND_BACKUP §2.3), and the launch a widget or
 * notification asked for. [appearance] is null until the stores have answered, which keeps the splash on screen so
 * the first frame already has the right theme.
 */
class AppViewModel(
    repository: EntryRepository,
    settings: SettingsRepository,
    private val accounts: Accounts = NoAccounts,
    private val welcomeSeen: WelcomeStore = WelcomeStore.Seen,
) : ViewModel() {
    val appearance: StateFlow<AppearanceInputs?> = combine(
        settings.settings.map { it.appearance }.distinctUntilChanged(),
        repository.observeAllEntries().map { entries -> entries.any { it.isSleep && it.isRunning } }.distinctUntilChanged(),
    ) { appearance, sleepRunning -> AppearanceInputs(appearance, sleepRunning) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pendingLaunch = MutableStateFlow<HomeLaunch?>(null)

    /** A deep link (widget, notification) that Home has not handled yet. Survives configuration changes. */
    val launch: StateFlow<HomeLaunch?> = pendingLaunch.asStateFlow()

    private val showWelcome = MutableStateFlow(accounts.available && accounts.user.value == null && !welcomeSeen.seen)

    /**
     * The first-run Welcome (BABIES_AND_BACKUP §3.1): once per phone, only in builds with accounts and while nobody is
     * signed in. Decided when the app starts, so it never appears in the middle of a session.
     */
    val welcome: StateFlow<Boolean> = showWelcome.asStateFlow()

    init {
        if (showWelcome.value) {
            // Signing in on the Welcome screen answers it.
            viewModelScope.launch {
                accounts.user.first { it != null }
                finishWelcome()
            }
        } else if (accounts.available && !welcomeSeen.seen) {
            // Signed in already (an update of a phone that has an account): nothing to ask any more.
            welcomeSeen.seen = true
        }
    }

    /** The Welcome was answered: signed in, or "Continue without an account". Not asked again on this phone. */
    fun finishWelcome() {
        welcomeSeen.seen = true
        showWelcome.value = false
    }

    fun request(launch: HomeLaunch) {
        pendingLaunch.value = launch
    }

    /** Home has handled [launch] (a newer request is kept). */
    fun consume(launch: HomeLaunch) {
        pendingLaunch.compareAndSet(launch, null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                AppViewModel(Core.repository, Core.settings, AppAccounts.accounts, WelcomePrefs(app))
            }
        }
    }
}
