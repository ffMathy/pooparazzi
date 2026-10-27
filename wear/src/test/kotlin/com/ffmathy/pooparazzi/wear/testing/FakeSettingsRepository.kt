package com.ffmathy.pooparazzi.wear.testing

import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.core.settings.Appearance
import com.ffmathy.pooparazzi.core.settings.Settings
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Settings held in memory; [state] stands in for what the phone syncs through `/settings`. */
class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state

    override suspend fun setBabyName(name: String) = state.updateAndGet { it.copy(babyName = name) }

    override suspend fun setAppearance(appearance: Appearance) = state.updateAndGet { it.copy(appearance = appearance) }

    override suspend fun setLanguage(language: AppLanguage) = state.updateAndGet { it.copy(language = language) }

    override suspend fun applyRemote(remote: Settings): Boolean {
        state.value = remote
        return true
    }
}
