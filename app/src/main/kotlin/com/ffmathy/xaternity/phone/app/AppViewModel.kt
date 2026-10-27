package com.ffmathy.xaternity.phone.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.xaternity.core.Core
import com.ffmathy.xaternity.core.data.EntryRepository
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.model.isSleep
import com.ffmathy.xaternity.core.settings.Appearance
import com.ffmathy.xaternity.core.settings.SettingsRepository
import com.ffmathy.xaternity.phone.launch.HomeLaunch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The inputs of the app-wide Appearance rule (DESIGN_SPEC §8.1) that come from data. */
data class AppearanceInputs(val appearance: Appearance, val sleepRunning: Boolean)

/**
 * App-level state: the Appearance setting and whether a sleep runs (Auto dims for naps), and the launch a widget or
 * notification asked for. [appearance] is null until the stores have answered, which keeps the splash on screen so
 * the first frame already has the right theme.
 */
class AppViewModel(repository: EntryRepository, settings: SettingsRepository) : ViewModel() {
    val appearance: StateFlow<AppearanceInputs?> = combine(
        settings.settings.map { it.appearance }.distinctUntilChanged(),
        repository.observeEntries().map { entries -> entries.any { it.isSleep && it.isRunning } }.distinctUntilChanged(),
    ) { appearance, sleepRunning -> AppearanceInputs(appearance, sleepRunning) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pendingLaunch = MutableStateFlow<HomeLaunch?>(null)

    /** A deep link (widget, notification) that Home has not handled yet. Survives configuration changes. */
    val launch: StateFlow<HomeLaunch?> = pendingLaunch.asStateFlow()

    fun request(launch: HomeLaunch) {
        pendingLaunch.value = launch
    }

    /** Home has handled [launch] (a newer request is kept). */
    fun consume(launch: HomeLaunch) {
        pendingLaunch.compareAndSet(launch, null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(Core.repository, Core.settings) }
        }
    }
}
