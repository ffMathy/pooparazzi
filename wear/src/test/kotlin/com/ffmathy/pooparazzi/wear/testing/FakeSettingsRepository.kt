package com.ffmathy.pooparazzi.wear.testing

import com.ffmathy.pooparazzi.core.model.Baby
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

    override suspend fun setBabyName(name: String) = state.updateAndGet { it.withBabyName(it.selectedBaby.id, name) }

    override suspend fun renameBaby(id: String, name: String) = state.updateAndGet { it.withBabyName(id, name) }

    override suspend fun addBaby(): Baby {
        val baby = Baby(Baby.newId())
        val next = state.updateAndGet { if (it.babies.size >= Settings.MAX_BABIES) it else it.withBabies(listOf(baby.id)) }
        return next.babies.firstOrNull { it.id == baby.id } ?: next.babies.last()
    }

    override suspend fun removeBaby(id: String) = state.updateAndGet { it.withRemovedBaby(id, 1L) }

    override suspend fun selectBaby(id: String) = state.updateAndGet { it.withSelectedBaby(id) }

    override suspend fun ensureBabies(ids: Collection<String>) = state.updateAndGet { it.withBabies(ids) }

    override suspend fun setAppearance(appearance: Appearance) = state.updateAndGet { it.copy(appearance = appearance) }

    override suspend fun setLanguage(language: AppLanguage) = state.updateAndGet { it.copy(language = language) }

    override suspend fun applyRemote(remote: Settings): Boolean {
        state.value = remote
        return true
    }

    override suspend fun applyLegacyRemote(remote: Settings): Boolean {
        val next = state.value.withLegacyRemote(remote) ?: return false
        state.value = next
        return true
    }
}
